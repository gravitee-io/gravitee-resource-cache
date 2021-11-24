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

import io.gravitee.resource.cache.api.Cache;
import io.gravitee.resource.cache.api.Element;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * @author Kamiel Ahmadpour (kamiel.ahmadpour at graviteesource.com)
 * @author GraviteeSource Team
 */
public class StandaloneCache implements Cache {

    private final String name;
    private final int timeToLiveSeconds;
    private final Map<Object, Object> internalMap = new ConcurrentHashMap<>();
    private final Map<Object, CacheDelayed> expiringKeys = new ConcurrentHashMap<>();
    private final DelayQueue<CacheDelayed> delayQueue = new DelayQueue<>();

    public StandaloneCache(String name, int timeToLiveSeconds) {
        this.name = name;
        this.timeToLiveSeconds = timeToLiveSeconds;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Map<Object, Object> getNativeCache() {
        cleanup();
        return internalMap;
    }

    @Override
    public Element get(Object key) {
        cleanup();
        renewKey(key);
        Serializable value = (Serializable) this.internalMap.get(key);
        return (value == null)
            ? null
            : new Element() {
                @Override
                public Object key() {
                    return key;
                }

                @Override
                public Serializable value() {
                    return value;
                }
            };
    }

    @Override
    public void put(Element element) {
        cleanup();

        int ttl = this.timeToLiveSeconds;
        if ((ttl == 0 && element.timeToLive() > 0) || (ttl > 0 && element.timeToLive() > 0 && ttl > element.timeToLive())) {
            ttl = element.timeToLive();
        }

        CacheDelayed delayedKey = new CacheDelayed(element.key(), ttl);
        CacheDelayed oldKey = expiringKeys.put(element.key(), delayedKey);
        if (oldKey != null) {
            expireKey(oldKey);
            expiringKeys.put(element.key(), delayedKey);
        }
        delayQueue.offer(delayedKey);

        internalMap.put(element.key(), element.value());
    }

    @Override
    public void evict(Object key) {
        expireKey(expiringKeys.remove(key));
        internalMap.remove(key);
    }

    @Override
    public void clear() {
        delayQueue.clear();
        expiringKeys.clear();
        internalMap.clear();
    }

    private void renewKey(Object key) {
        CacheDelayed delayedKey = expiringKeys.get(key);
        if (delayedKey != null) {
            delayedKey.renew();
        }
    }

    private void expireKey(CacheDelayed delayedKey) {
        if (delayedKey != null) {
            delayedKey.expire();
            cleanup();
        }
    }

    private void cleanup() {
        CacheDelayed delayedKey = delayQueue.poll();
        while (delayedKey != null) {
            internalMap.remove(delayedKey.getKey());
            expiringKeys.remove(delayedKey.getKey());
            delayedKey = delayQueue.poll();
        }
    }

    private static class CacheDelayed implements Delayed {

        private long startTime = System.currentTimeMillis();
        private final long maxLifeTimeMillis;
        private final Object key;

        public CacheDelayed(Object key, int timeToLiveSeconds) {
            this.maxLifeTimeMillis = timeToLiveSeconds * 1000L;
            this.key = key;
        }

        public Object getKey() {
            return key;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(getDelayMillis(), TimeUnit.MILLISECONDS);
        }

        private long getDelayMillis() {
            return (startTime + maxLifeTimeMillis) - System.currentTimeMillis();
        }

        public void renew() {
            startTime = System.currentTimeMillis();
        }

        public void expire() {
            startTime = Long.MIN_VALUE;
        }

        @Override
        public int compareTo(Delayed that) {
            return Long.compare(this.getDelayMillis(), ((CacheDelayed) that).getDelayMillis());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheDelayed that = (CacheDelayed) o;
            return startTime == that.startTime && maxLifeTimeMillis == that.maxLifeTimeMillis && key.equals(that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(startTime, maxLifeTimeMillis, key);
        }
    }
}
