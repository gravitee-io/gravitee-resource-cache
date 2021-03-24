/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.resource.cache;

import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MaxSizePolicy;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.resource.api.AbstractConfigurableResource;
import io.gravitee.resource.cache.configuration.CacheResourceConfiguration;
import io.gravitee.resource.cache.ehcache.EhCacheDelegate;
import io.gravitee.resource.cache.rediscache.RedisCacheDelegate;
import io.gravitee.resource.cache.rediscache.RedisConfiguration;
import io.gravitee.resource.cache.rediscache.RedisUtil;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.gravitee.resource.cache.hazelcast.HazelcastDelegate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * @author David BRASSELY (david at gravitee.io)
 * @author GraviteeSource Team
 */
public class CacheResource extends AbstractConfigurableResource<CacheResourceConfiguration> implements ApplicationContextAware {

    private final Logger LOGGER = LoggerFactory.getLogger(CacheResource.class);
    private ApplicationContext applicationContext;
    private static final char KEY_SEPARATOR = '_';
    private static final String MAP_PREFIX = "cache-resources" + KEY_SEPARATOR;

    private HazelcastInstance hazelcastInstance;
    private ApplicationContext applicationContext;
    private List<String> configuredResources = new ArrayList<>();

    /**
     * Generate a unique identifier for the resource cache.
     *
     * @param executionContext
     * @return
     */
    private String computeConfigName(ExecutionContext executionContext) {
        StringBuilder sb = new StringBuilder(MAP_PREFIX).append(configuration().getName());
        Object attribute = executionContext.getAttribute(ExecutionContext.ATTR_API);
        if (attribute != null) {
            sb.append(KEY_SEPARATOR).append(attribute);
        }
        return sb.toString();
    }

    protected void applyConfiguration(String resourceConfigName) {
        // Apply configuration only if not already configured
        if (!configuredResources.contains(resourceConfigName)) {
            Config config = hazelcastInstance.getConfig();
            MapConfig closestResourceConfig = config.getMapConfig(resourceConfigName);

            // Apply configuration from resource only if not have an explicit configuration in hazelcast.xml for this resource
            if (!resourceConfigName.equals(closestResourceConfig.getName())) {
                MapConfig resourceConfig = new MapConfig(closestResourceConfig);
                resourceConfig.setName(resourceConfigName);

                long desiredMaxSize = configuration().getMaxEntriesLocalHeap();
                MaxSizePolicy maxSizePolicy = closestResourceConfig.getEvictionConfig().getMaxSizePolicy();
                boolean notRelativeWithMemory =
                    maxSizePolicy.equals(MaxSizePolicy.PER_NODE) ||
                    maxSizePolicy.equals(MaxSizePolicy.PER_PARTITION) ||
                    maxSizePolicy.equals(MaxSizePolicy.ENTRY_COUNT);
                if (notRelativeWithMemory && desiredMaxSize != 0 && desiredMaxSize < closestResourceConfig.getEvictionConfig().getSize()) {
                    resourceConfig.getEvictionConfig().setSize((int) desiredMaxSize);
                    if (closestResourceConfig.getEvictionConfig().getEvictionPolicy().equals(EvictionPolicy.NONE)) {
                        // Set "Least Recently Used" eviction policy if not have eviction configured
                        resourceConfig.getEvictionConfig().setEvictionPolicy(EvictionPolicy.LRU);
                    }
                }

                long desiredTimeToIdle = configuration().getTimeToIdleSeconds();
                int maxIdleSeconds = closestResourceConfig.getMaxIdleSeconds();
                if (maxIdleSeconds == 0 && desiredTimeToIdle > 0) {
                    resourceConfig.setMaxIdleSeconds((int) desiredTimeToIdle);
                } else if (maxIdleSeconds > 0 && desiredTimeToIdle == 0) {
                    resourceConfig.setMaxIdleSeconds(maxIdleSeconds);
                } else {
                    resourceConfig.setMaxIdleSeconds(Math.min((int) desiredTimeToIdle, maxIdleSeconds));
                }

                long desiredTimeToLive = configuration().getTimeToLiveSeconds();
                int timeToLiveSeconds = closestResourceConfig.getTimeToLiveSeconds();
                if (timeToLiveSeconds == 0 && desiredTimeToLive > 0) {
                    resourceConfig.setTimeToLiveSeconds((int) desiredTimeToLive);
                } else if (timeToLiveSeconds > 0 && desiredTimeToLive == 0) {
                    resourceConfig.setTimeToLiveSeconds(timeToLiveSeconds);
                } else {
                    int timeToLive = Math.min((int) desiredTimeToLive, timeToLiveSeconds);
                    resourceConfig.setTimeToLiveSeconds(timeToLive);
                }

                config.addMapConfig(resourceConfig);
                configuredResources.add(resourceConfigName);
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        Environment environment = applicationContext.getEnvironment();
        String cacheType = configuration().getCacheType();
        if (isEhCache(cacheType)) {
            Configuration configuration = new Configuration();
            configuration.setName(configuration().getName());
            cacheManager = new CacheManager(configuration);

            CacheConfiguration cacheConfiguration = new CacheConfiguration();
            cacheConfiguration.setEternal(false);
            cacheConfiguration.setTimeToIdleSeconds(configuration().getTimeToIdleSeconds());
            cacheConfiguration.setTimeToLiveSeconds(configuration().getTimeToLiveSeconds());
            cacheConfiguration.setMaxEntriesLocalHeap(configuration().getMaxEntriesLocalHeap());
            cacheConfiguration.setName(configuration().getName());

            LOGGER.info("Create a new cache: {}", configuration().getName());
            net.sf.ehcache.Cache ehCache = new net.sf.ehcache.Cache(cacheConfiguration);
            cache = new EhCacheDelegate(ehCache);
            cacheManager.addCache(ehCache);
        } else {
            String USE_SENTINEL = environment.getProperty("ds.redis.sentinel.enable", "enable");
            String NODE_1_HOST = environment.getProperty("ds.redis.sentinel.node1.host", "x.x.x.x");
            String NODE_2_HOST = environment.getProperty("ds.redis.sentinel.node2.host", "x.x.x.x");
            String NODE_3_HOST = environment.getProperty("ds.redis.sentinel.node3.host", "x.x.x.x");
            String MASTER_NAME = environment.getProperty("ds.redis.sentinel.master", "master");
            String PASSWORD = environment.getProperty("ds.redis.sentinel.password", "password");
            String AUTH = environment.getProperty("ds.redis.sentinel.auth", "enable");
            int NODE_1_PORT = Integer.valueOf(environment.getProperty("ds.redis.sentinel.node1.port", "p1"));
            int NODE_2_PORT = Integer.valueOf(environment.getProperty("ds.redis.sentinel.node2.port", "p2"));
            int NODE_3_PORT = Integer.valueOf(environment.getProperty("ds.redis.sentinel.node3.port", "p3"));
            int MAX_IDLE = Integer.valueOf(environment.getProperty("ds.redis.pool.maxIdle", "20"));
            int MAX_ACTIVE = Integer.valueOf(environment.getProperty("ds.redis.pool.maxActive", "128"));

            String NODE_HOST = environment.getProperty("ds.redis.single.node", "x.x.x.x");
            int NODE_PORT = Integer.valueOf(environment.getProperty("ds.redis.single.port", "p4"));

            RedisConfiguration rc;
            if (USE_SENTINEL.equals(RedisConfiguration.ENABLE)) {
                LOGGER.info("Redis cache resource, use sentinel: enable.");
                rc = new RedisConfiguration(NODE_1_HOST, NODE_2_HOST, NODE_3_HOST, MASTER_NAME, PASSWORD, AUTH,
                                            NODE_1_PORT, NODE_2_PORT, NODE_3_PORT, MAX_IDLE, MAX_ACTIVE);
            } else {
                LOGGER.info("Redis cache resource, use sentinel: disable.");
                rc = new RedisConfiguration(NODE_HOST, NODE_PORT, MAX_IDLE, MAX_ACTIVE);
            }
            RedisTemplate<Object,Object> redisTemplate = rc.redisTemplate();
            RedisUtil rdcache = new RedisUtil(redisTemplate);
            cache = new RedisCacheDelegate(rdcache);
        }
    }

    public Cache getCache(ExecutionContext executionContext) {
        String resourceConfigName = computeConfigName(executionContext);
        applyConfiguration(resourceConfigName);
        IMap<Object, Object> map = hazelcastInstance.getMap(resourceConfigName);
        return new HazelcastDelegate(map, (int) configuration().getTimeToLiveSeconds());
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }


    private static boolean isEhCache(String cacheType) {
        return cacheType.equals("ehcache");
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }


    @Override
    protected void doStop() throws Exception {
        super.doStop();
        Config config = hazelcastInstance.getConfig();
        // Reset resource configuration
        Map<String, MapConfig> allExpectConfiguredResources = new HashMap<>();
        config
            .getMapConfigs()
            .values()
            .forEach(
                mapConfig -> {
                    if (!configuredResources.contains(mapConfig.getName())) {
                        allExpectConfiguredResources.put(mapConfig.getName(), mapConfig);
                    }
                }
            );
        config.setMapConfigs(allExpectConfiguredResources);
        configuredResources.clear();
    }
}
