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
package io.gravitee.resource.cache.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import io.gravitee.common.utils.UUID;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.resource.cache.api.Cache;
import io.gravitee.resource.cache.api.CacheResource;
import io.gravitee.resource.cache.configuration.CacheResourceConfiguration;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class HazelcastCacheResource extends CacheResource<CacheResourceConfiguration> implements ApplicationContextAware {

    private static final String MAP_PREFIX = "cache-resources" + CacheResourceConfiguration.KEY_SEPARATOR;

    private HazelcastInstance hazelcastInstance;
    private ApplicationContext applicationContext;

    private String cacheId;
    private HazelcastDelegate hazelcastDelegate;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        final CacheResourceConfiguration configuration = configuration();
        this.cacheId = MAP_PREFIX + configuration.getName() + CacheResourceConfiguration.KEY_SEPARATOR + UUID.random().toString();
        this.hazelcastInstance = this.applicationContext.getBean(HazelcastInstance.class);

        buildCache();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (this.hazelcastDelegate != null) {
            this.hazelcastDelegate.clear();
            this.hazelcastDelegate = null;
        }
    }

    public <K, V> Cache<K, V> getCache(ExecutionContext executionContext) {
        return hazelcastDelegate;
    }

    protected void buildCache() {
        Config config = hazelcastInstance.getConfig();

        if (!config.getMapConfigs().containsKey(cacheId)) {
            MapConfig resourceConfig = new MapConfig(cacheId);

            // Cache is standalone, no backup wanted.
            resourceConfig.setAsyncBackupCount(0);
            resourceConfig.setBackupCount(0);

            long desiredMaxSize = configuration().getMaxEntriesLocalHeap();
            resourceConfig.getEvictionConfig().setSize((int) desiredMaxSize);
            if (resourceConfig.getEvictionConfig().getEvictionPolicy().equals(EvictionPolicy.NONE)) {
                // Set "Least Recently Used" eviction policy if not have eviction configured
                resourceConfig.getEvictionConfig().setEvictionPolicy(EvictionPolicy.LRU);
            }

            resourceConfig.setMaxIdleSeconds((int) configuration().getTimeToIdleSeconds());
            resourceConfig.setTimeToLiveSeconds((int) configuration().getTimeToLiveSeconds());

            config.addMapConfig(resourceConfig);
        }

        this.hazelcastDelegate = new HazelcastDelegate<>(hazelcastInstance.getMap(cacheId), (int) configuration().getTimeToLiveSeconds());
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
