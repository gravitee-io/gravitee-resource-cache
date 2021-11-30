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

import com.google.common.base.Verify;
import com.google.common.cache.CacheBuilder;
import io.gravitee.resource.cache.api.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import org.springframework.util.Assert;

/**
 * @author Kamiel Ahmadpour (kamiel.ahmadpour at graviteesource.com)
 * @author GraviteeSource Team
 */
public class StandaloneCache<K, V> implements Cache<K, V> {

    private final String name;
    private final int timeToLiveSeconds;
    private final com.google.common.cache.Cache<K, V> internalCache;
    Map<UUID, CacheListener<K, V>> cacheListenerMap = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "gio-standalone-cache"));

    public StandaloneCache(String name, int timeToLiveSeconds) {
        this.name = name;
        this.timeToLiveSeconds = timeToLiveSeconds;
        CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.newBuilder();
        if (timeToLiveSeconds > 0) {
            cacheBuilder.expireAfterAccess(timeToLiveSeconds, TimeUnit.SECONDS);
        }
        internalCache = cacheBuilder.build();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int size() {
        return ((Long) this.internalCache.size()).intValue();
    }

    @Override
    public Map<K, V> getNativeCache() {
        return this.internalCache.asMap();
    }

    @Override
    public Element<K, V> get(K key) {
        V value = internalCache.getIfPresent(key);
        return (value == null) ? null : new Element<>(key, value);
    }

    @Override
    public V put(Element<K, V> element) {
        // TODO - Kamiel - 29/11/2021: There are so different checks needs to be done for validation the element key, valye, ttl
        Assert.notNull(element, "Element can't be null");
        Verify.verify(
            element.getTimeToLive() <= this.timeToLiveSeconds,
            "Element time to live can't be bigger than time to live defined in the configuration"
        );

        V currentValue = this.internalCache.getIfPresent(element.getKey());
        executorService.execute(
            () -> {
                if (currentValue == null) {
                    cacheListenerMap.forEach(
                        (uuid, cacheListener) ->
                            new EntryEvent<>(
                                name,
                                EntryEventType.ADDED,
                                element.getKey(),
                                get(element.getKey()).getValue(),
                                element.getValue()
                            )
                    );
                } else {
                    cacheListenerMap.forEach(
                        (uuid, cacheListener) ->
                            new EntryEvent<>(
                                name,
                                EntryEventType.UPDATED,
                                element.getKey(),
                                get(element.getKey()).getValue(),
                                element.getValue()
                            )
                    );
                }
            }
        );

        this.internalCache.put(element.getKey(), element.getValue());
        return currentValue;
    }

    @Override
    public V evict(K key) {
        executorService.execute(
            () ->
                cacheListenerMap.forEach(
                    (uuid, cacheListener) -> new EntryEvent<>(name, EntryEventType.REMOVED, key, get(key).getValue(), null)
                )
        );
        V v = this.internalCache.getIfPresent(key);
        this.internalCache.invalidate(key);

        return v;
    }

    @Override
    public void clear() {
        // TODO - Kamiel - 29/11/2021: Check id it is needed
        getNativeCache()
            .forEach(
                (key, value) ->
                    executorService.execute(
                        () ->
                            cacheListenerMap.forEach(
                                (uuid, cacheListener) -> new EntryEvent<>(name, EntryEventType.REMOVED, key, value, null)
                            )
                    )
            );

        this.internalCache.invalidateAll();
    }

    @Override
    public UUID addCacheListener(CacheListener<K, V> cacheListener) {
        UUID uuid = io.gravitee.common.utils.UUID.random();
        cacheListenerMap.put(uuid, cacheListener);

        return uuid;
    }

    @Override
    public boolean removeCacheListener(UUID id) {
        if (!cacheListenerMap.containsKey(id)) {
            return false;
        } else {
            cacheListenerMap.remove(id);
            return true;
        }
    }
}
