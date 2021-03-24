package io.gravitee.resource.cache.rediscache;

import java.util.Base64;
import java.util.concurrent.TimeUnit;

import io.gravitee.policy.cache.CacheResponse;
import io.gravitee.policy.cache.resource.CacheElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.gravitee.gateway.api.buffer.Buffer;

public class RedisUtil {
    private final Logger LOGGER = LoggerFactory.getLogger(RedisUtil.class);
    private HashOperations<Object,Object,Object> hashOperation;
    private ValueOperations<Object,Object> valueOperations;
    private RedisTemplate<Object,Object> redisTemplate;
    private String name;
    private static final String CONTENT_PREFIX_KEY = "fb4f66c1-802b-441b-9ab3-04d0fcc93ffz";
    private static final String RESPONSE_PREFIX_KEY = "b7e6c997-21eb-4dd0-ac1d-f32c32b4048z";



    public RedisUtil(RedisTemplate<Object,Object> redisTemplate){
        this.redisTemplate = redisTemplate;
        this.hashOperation = redisTemplate.opsForHash();
        this.valueOperations = redisTemplate.opsForValue();
    }

    public void putValue(Object key, Object value, int TTL) {
        /*
         * CacheElement
         *   Key
         *   CacheResponse
         *     Header
         *     Status
         *     Buffer
         * */

        String CONTENT_KEY = CONTENT_PREFIX_KEY + key;
        String RESPONSE_KEY = RESPONSE_PREFIX_KEY + key;
        try {
            CacheElement result = (CacheElement) value;
            CacheResponse cacheResponse = (CacheResponse) result.value();
            Response response = new Response(cacheResponse.getStatus(), cacheResponse.getHeaders(), TTL);
            if (cacheResponse != null) {
                Buffer content = cacheResponse.getContent();
                if (content != null) {
                    String encode = Base64.getEncoder().encodeToString(content.getBytes());
                    valueOperations.set(CONTENT_KEY, encode, TTL, TimeUnit.SECONDS);
                }
            }
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonResponse = objectMapper.writeValueAsString(response);
            valueOperations.set(RESPONSE_KEY, jsonResponse, TTL, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    public Object getValue(Object key) {
        /*
         * CacheElement
         *   Key
         *   CacheResponse
         *     Header
         *     Status
         *     Buffer
         * */

        String CONTENT_KEY = CONTENT_PREFIX_KEY + key;
        String RESPONSE_KEY = RESPONSE_PREFIX_KEY + key;
        Object result = null;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonContent = (String) valueOperations.get(CONTENT_KEY);
            String jsonResponse = (String) valueOperations.get(RESPONSE_KEY);
            if (jsonContent == null && jsonResponse == null) {
                return result;
            }
            CacheResponse cacheResponse = new CacheResponse();
            Buffer buffer = Buffer.buffer(Base64.getDecoder().decode(jsonContent));
            cacheResponse.setContent(buffer);
            Response response = objectMapper.readValue(jsonResponse, Response.class);
            cacheResponse.setHeaders(response.getHeaders());
            cacheResponse.setStatus(response.getStatus());

            CacheElement cacheElement = new CacheElement((String) key, cacheResponse);
            cacheElement.setTimeToLive(response.getTimeToLive());

            result = cacheElement;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return result;
    }

    public void setExpire(Object key,long timeout,TimeUnit unit) {
        redisTemplate.expire(key, timeout, unit);
    }

    public void evict(Object key) {
        redisTemplate.delete(key);
    }

    public void clear() {
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.flushDb();
            return null;
        });
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

