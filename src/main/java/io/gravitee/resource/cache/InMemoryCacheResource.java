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

import io.gravitee.common.utils.UUID;
import io.gravitee.gateway.reactive.api.context.ExecutionContext;
import io.gravitee.node.api.cache.CacheConfiguration;
import io.gravitee.node.api.cache.CacheManager;
import io.gravitee.resource.cache.api.Cache;
import io.gravitee.resource.cache.api.CacheResource;
import io.gravitee.resource.cache.configuration.CacheResourceConfiguration;
import io.gravitee.resource.cache.inmemory.InMemoryCacheDelegate;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class InMemoryCacheResource extends CacheResource<CacheResourceConfiguration> implements ApplicationContextAware {

    private static final String MAP_PREFIX = "cache-resources" + CacheResourceConfiguration.KEY_SEPARATOR;

    private ApplicationContext applicationContext;

    private String cacheId;
    private CacheManager cacheManager;
    private Cache cache;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        final CacheResourceConfiguration configuration = configuration();
        this.cacheId = MAP_PREFIX + configuration.getName() + CacheResourceConfiguration.KEY_SEPARATOR + UUID.random().toString();
        this.cacheManager = this.applicationContext.getBean(CacheManager.class);

        buildCache();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        this.cacheManager.destroy(this.cacheId);
        if (this.cache != null) {
            this.cache.clear();
            this.cache = null;
        }
    }

    @Override
    public io.gravitee.resource.cache.api.Cache getCache(ExecutionContext ctx) {
        return this.cache;
    }

    @Override
    public io.gravitee.resource.cache.api.Cache getCache(io.gravitee.gateway.api.ExecutionContext ctx) {
        return this.cache;
    }

    protected void buildCache() {
        CacheConfiguration configuration = new CacheConfiguration();

        configuration.setMaxSize(configuration().getMaxEntriesLocalHeap());

        configuration.setTimeToIdleSeconds((int) configuration().getTimeToIdleSeconds());
        configuration.setTimeToLiveSeconds((int) configuration().getTimeToLiveSeconds());

        this.cache = new InMemoryCacheDelegate(cacheId, cacheManager.getOrCreateCache(cacheId, configuration));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
