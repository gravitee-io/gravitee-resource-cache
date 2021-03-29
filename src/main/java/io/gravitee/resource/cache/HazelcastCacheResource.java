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
import io.gravitee.resource.cache.api.Cache;
import io.gravitee.resource.cache.api.CacheResource;
import io.gravitee.resource.cache.configuration.CacheResourceConfiguration;
import io.gravitee.resource.cache.hazelcast.HazelcastDelegate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class HazelcastCacheResource extends CacheResource<CacheResourceConfiguration> implements ApplicationContextAware {

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
        hazelcastInstance = this.applicationContext.getBean(HazelcastInstance.class);
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
