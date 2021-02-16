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

import com.hazelcast.replicatedmap.ReplicatedMap;
import io.gravitee.resource.cache.Cache;
import io.gravitee.resource.cache.Element;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

public class HazelcastDelegate implements Cache {

    private final ReplicatedMap<Object, Object> cache;
    private final int timeToLiveSeconds;

    public HazelcastDelegate(ReplicatedMap<Object, Object> cache, int timeToLiveSeconds) {
        this.cache = cache;
        this.timeToLiveSeconds = timeToLiveSeconds;
    }

    @Override
    public String getName() {
        return cache.getName();
    }

    @Override
    public Object getNativeCache() {
        return cache;
    }

    @Override
    public Element get(Object key) {
        Serializable o = (Serializable) this.cache.get(key);
        return (o == null)
            ? null
            : new Element() {
                @Override
                public Object key() {
                    return key;
                }

                @Override
                public Serializable value() {
                    return o;
                }
            };
    }

    @Override
    public void put(Element element) {
        int ttl = this.timeToLiveSeconds;
        if ((ttl == 0 && element.timeToLive() > 0) || (ttl > 0 && element.timeToLive() > 0 && ttl > element.timeToLive())) {
            ttl = element.timeToLive();
        }
        cache.put(element.key(), element.value(), ttl, TimeUnit.SECONDS);
    }

    @Override
    public void evict(Object key) {
        cache.remove(key);
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
