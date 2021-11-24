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
package io.gravitee.resource.cache.standalone;

import io.gravitee.common.utils.UUID;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.resource.cache.api.Cache;
import io.gravitee.resource.cache.api.CacheResource;
import io.gravitee.resource.cache.configuration.CacheResourceConfiguration;

/**
 * @author Kamiel Ahmadpour (kamiel.ahmadpour at graviteesource.com)
 * @author GraviteeSource Team
 */
public class StandaloneCacheResource extends CacheResource<CacheResourceConfiguration> {

    private static final char KEY_SEPARATOR = '_';
    private static final String MAP_PREFIX = "cache-resources" + KEY_SEPARATOR;
    private Cache cache;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        final CacheResourceConfiguration configuration = configuration();
        String cacheId = MAP_PREFIX + configuration.getName() + KEY_SEPARATOR + UUID.random().toString();
        cache = new StandaloneCache(cacheId, (int) configuration().getTimeToLiveSeconds());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (this.cache != null) {
            this.cache = null;
        }
    }

    @Override
    public Cache getCache(ExecutionContext executionContext) {
        return cache;
    }
}
