package io.gravitee.resource.cache.rediscache;

import io.gravitee.resource.cache.Cache;
import io.gravitee.resource.cache.Element;
import io.gravitee.resource.cache.rediscache.RedisUtil;

/**
 * @author daivx1
 * @version 1.0
 * @since Aug 2019
 */

public class RedisCacheDelegate implements Cache {

    private final RedisUtil rediscache;

    public RedisCacheDelegate(RedisUtil rediscache) {
        this.rediscache = rediscache;
    }

    @Override
    public String getName() {
        return rediscache.getName();
    }

    @Override
    public Object getNativeCache() {
        return this.rediscache;
    }

    @Override
    public Element get(Object key) {
        Object result = rediscache.getValue(key);
        return (result == null) ? null : (Element) result;
    }

    @Override
    public void put(Element element) {
        rediscache.putValue(element.key(), element, element.timeToLive());
    }

    @Override
    public void evict(Object key) {
        rediscache.evict(key);
    }

    @Override
    public void clear() {
        rediscache.clear();
    }
}
