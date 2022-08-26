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
package io.gravitee.resource.cache.inmemory;

import io.gravitee.resource.cache.api.Cache;
import io.gravitee.resource.cache.api.Element;
import java.util.concurrent.TimeUnit;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class InMemoryCacheDelegate implements Cache {

    private final String name;
    private final long timeToLiveSeconds;
    private final io.gravitee.node.api.cache.Cache wrapped;

    public InMemoryCacheDelegate(final String name, long timeToLiveSeconds, final io.gravitee.node.api.cache.Cache wrapped) {
        this.name = name;
        this.timeToLiveSeconds = timeToLiveSeconds;
        this.wrapped = wrapped;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return wrapped;
    }

    @Override
    public Element get(Object o) {
        final Object value = wrapped.get(o);
        if (value != null) {
            return new Element() {
                @Override
                public Object key() {
                    return o;
                }

                @Override
                public Object value() {
                    return value;
                }
            };
        }

        return null;
    }

    @Override
    public void put(Element element) {
        long timeToLive = this.timeToLiveSeconds;
        if (
            (timeToLive == 0 && element.timeToLive() > 0) ||
            (timeToLive > 0 && element.timeToLive() > 0 && timeToLive > element.timeToLive())
        ) {
            timeToLive = element.timeToLive();
        }
        wrapped.put(element.key(), element.value(), timeToLive, TimeUnit.SECONDS);
    }

    @Override
    public void evict(Object o) {
        wrapped.evict(o);
    }

    @Override
    public void clear() {
        wrapped.clear();
    }
}
