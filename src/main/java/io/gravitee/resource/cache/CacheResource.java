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

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.replicatedmap.ReplicatedMap;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.resource.api.AbstractConfigurableResource;
import io.gravitee.resource.cache.configuration.CacheResourceConfiguration;
import io.gravitee.resource.cache.hazelcast.HazelcastDelegate;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * @author David BRASSELY (david at gravitee.io)
 * @author GraviteeSource Team
 */
public class CacheResource extends AbstractConfigurableResource<CacheResourceConfiguration> implements ApplicationContextAware {

    private static final char KEY_SEPARATOR = '_';

    private HazelcastInstance hazelcastInstance;
    private ApplicationContext applicationContext;

    /**
     * Generate a unique identifier for the resource cache.
     *
     * @param executionContext
     * @return
     */
    private String hash(ExecutionContext executionContext) {
        StringBuilder sb = new StringBuilder();
        Object attribute = executionContext.getAttribute(ExecutionContext.ATTR_API);
        if (attribute != null) {
            sb.append(attribute).append(KEY_SEPARATOR);
        }
        sb.append(configuration().getName());
        return sb.toString();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        hazelcastInstance = this.applicationContext.getBean(HazelcastInstance.class);
    }

    public Cache getCache(ExecutionContext executionContext) {
        ReplicatedMap<Object, Object> map = hazelcastInstance.getReplicatedMap(hash(executionContext));
        return new HazelcastDelegate(map, (int) configuration().getTimeToLiveSeconds());
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
