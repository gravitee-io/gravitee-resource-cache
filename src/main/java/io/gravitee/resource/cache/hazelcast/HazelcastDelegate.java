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

import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.IMap;
import com.hazelcast.map.impl.MapListenerAdapter;
import io.gravitee.resource.cache.api.Cache;
import io.gravitee.resource.cache.api.CacheListener;
import io.gravitee.resource.cache.api.Element;
import io.gravitee.resource.cache.api.EntryEventType;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.util.Assert;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class HazelcastDelegate<K, V> implements Cache<K, V> {

    private final IMap<K, V> cache;
    private final int timeToLiveSeconds;

    public HazelcastDelegate(IMap<K, V> cache, int timeToLiveSeconds) {
        this.cache = cache;
        this.timeToLiveSeconds = timeToLiveSeconds;
    }

    @Override
    public String getName() {
        return cache.getName();
    }

    @Override
    public int size() {
        return cache.size();
    }

    @Override
    public IMap<K, V> getNativeCache() {
        return cache;
    }

    @Override
    public Element<K, V> get(K key) {
        V value = this.cache.get(key);
        return (value == null) ? null : new Element<>(key, value);
    }

    @Override
    public V put(Element<K, V> element) {
        // TODO - Kamiel - 26/11/2021: There are so different checks needs to be done for validation the element key, valye, ttl
        Assert.notNull(element, "Element can't be null");
        if (element.getTimeToLive() > this.timeToLiveSeconds) {
            throw new RuntimeException("Element time to live can't be bigger than time to live defined in the configuration");
        }

        int ttl = Math.min(element.getTimeToLive(), this.timeToLiveSeconds);
        return cache.put(element.getKey(), element.getValue(), ttl, TimeUnit.SECONDS);
    }

    @Override
    public V evict(K key) {
        V v = cache.get(key);
        cache.remove(key);

        return v;
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public UUID addCacheListener(CacheListener<K, V> cacheListener) {
        return this.cache.addEntryListener(
                new MapListenerAdapter<K, V>() {
                    @Override
                    public void onEntryEvent(EntryEvent<K, V> event) {
                        cacheListener.onEvent(
                            new io.gravitee.resource.cache.api.EntryEvent<>(
                                event.getSource(),
                                EntryEventType.getByType(event.getEventType().getType()),
                                event.getKey(),
                                event.getOldValue(),
                                event.getValue()
                            )
                        );
                    }
                },
                true
            );
    }

    @Override
    public boolean removeCacheListener(UUID id) {
        return this.cache.removeEntryListener(id);
    }
}
