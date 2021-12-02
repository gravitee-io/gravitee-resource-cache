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
import java.util.stream.Collectors;

/**
 * @author Kamiel Ahmadpour (kamiel.ahmadpour at graviteesource.com)
 * @author GraviteeSource Team
 */
public class StandaloneCache<K, V> implements Cache<K, V> {

    private final String name;
    private final int timeToLiveSeconds;
    private final com.google.common.cache.Cache<K, ValueWrapper<V>> internalCache;
    Map<UUID, CacheListener<K, V>> cacheListenerMap = new ConcurrentHashMap<>();
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "gio-standalone-cache"));

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
        return getNativeCache().size();
    }

    @Override
    public Map<K, V> getNativeCache() {
        return this.internalCache.asMap()
            .entrySet()
            .stream()
            .filter(e -> e.getValue().expirationTimeMillis == 0 || System.currentTimeMillis() <= e.getValue().expirationTimeMillis)
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().value));
    }

    @Override
    public Element<K, V> get(K key) {
        ValueWrapper<V> vw = internalCache.getIfPresent(key);
        if (vw != null) {
            if (vw.expirationTimeMillis == 0) {
                return new Element<>(key, vw.value);
            } else if (System.currentTimeMillis() <= vw.expirationTimeMillis) {
                vw.expirationTimeMillis = System.currentTimeMillis() + vw.timeToLiveMillis;
                return new Element<>(key, vw.value);
            } else {
                this.internalCache.invalidate(key);
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public V put(Element<K, V> element) {
        Verify.verify(
            TimeUnit.MILLISECONDS.convert(element.getTimeToLive(), TimeUnit.SECONDS) <=
            TimeUnit.MILLISECONDS.convert(this.timeToLiveSeconds, TimeUnit.SECONDS),
            "single ttl can't be bigger than ttl defined in the configuration"
        );

        Element<K, V> currentValue = this.get(element.getKey());
        long ttlMillis = TimeUnit.MILLISECONDS.convert(element.getTimeToLive(), TimeUnit.SECONDS);
        long expirationTimeMillis = ttlMillis != 0 ? System.currentTimeMillis() + ttlMillis : 0;
        this.internalCache.put(element.getKey(), new ValueWrapper<>(element.getValue(), ttlMillis, expirationTimeMillis));

        executorService.execute(
            () -> {
                if (currentValue == null) {
                    cacheListenerMap.forEach(
                        (uuid, cacheListener) -> new EntryEvent<>(name, EntryEventType.ADDED, element, null, element.getKey())
                    );
                } else {
                    cacheListenerMap.forEach(
                        (uuid, cacheListener) ->
                            new EntryEvent<>(name, EntryEventType.UPDATED, element.getKey(), currentValue.getValue(), element.getValue())
                    );
                }
            }
        );

        return currentValue == null ? null : currentValue.getValue();
    }

    @Override
    public V evict(K key) {
        Element<K, V> element = this.get(key);
        this.internalCache.invalidate(key);

        executorService.execute(
            () ->
                cacheListenerMap.forEach(
                    (uuid, cacheListener) -> new EntryEvent<>(name, EntryEventType.REMOVED, key, element.getValue(), null)
                )
        );

        return element.getValue();
    }

    @Override
    public void clear() {
        this.internalCache.invalidateAll();
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

    private static class ValueWrapper<T> {

        private final T value;
        private final long timeToLiveMillis;
        private long expirationTimeMillis;

        public ValueWrapper(T value, long timeToLiveMillis, long expirationTimeMillis) {
            this.value = value;
            this.timeToLiveMillis = timeToLiveMillis;
            this.expirationTimeMillis = expirationTimeMillis;
        }
    }
}
