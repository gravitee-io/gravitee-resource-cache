package io.gravitee.resource.cache.rediscache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import redis.clients.jedis.JedisPoolConfig;

/**
 * @author daivx1
 * @version 1.0
 * @since Aug 2019
 */

public class RedisConfiguration {

    private final Logger LOGGER = LoggerFactory.getLogger(RedisConfiguration.class);

    private String NODE_1_HOST, NODE_2_HOST, NODE_3_HOST, MASTER_NAME, PASSWORD, USE_SENTINEL, AUTH;
    private int NODE_1_PORT, NODE_2_PORT, NODE_3_PORT, MAX_IDLE, MAX_ACTIVE;
    public static final String ENABLE = "enable";
    public static final String DISABLE = "disable";

    public RedisConfiguration(String host1, String host2, String host3, String masterName, String password,
                              String auth, int port1, int port2, int port3, int maxIdle, int maxActive) {
        this.NODE_1_HOST = host1;
        this.NODE_2_HOST = host2;
        this.NODE_3_HOST = host3;
        this.MASTER_NAME = masterName;
        this.PASSWORD = password;
        this.USE_SENTINEL = ENABLE;
        this.AUTH = auth;
        this.NODE_1_PORT = port1;
        this.NODE_2_PORT = port2;
        this.NODE_3_PORT = port3;
        this.MAX_IDLE = maxIdle;
        this.MAX_ACTIVE = maxActive;
    }

    public RedisConfiguration(String host, int port, int maxIdle, int maxActive) {
        this.NODE_1_HOST = host;
        this.NODE_1_PORT = port;
        this.MAX_IDLE = maxIdle;
        this.MAX_ACTIVE = maxActive;
        this.USE_SENTINEL = DISABLE;
    }


    protected JedisConnectionFactory jedisConnectionFactory() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration(this.NODE_1_HOST, this.NODE_1_PORT);
        redisStandaloneConfiguration.setPassword(this.PASSWORD);
        return new JedisConnectionFactory(redisStandaloneConfiguration);
    }

    protected JedisConnectionFactory jedisSentinelConnectionFactory() {
        RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration()
                .master(MASTER_NAME)
                .sentinel(NODE_1_HOST, Integer.valueOf(NODE_1_PORT))
                .sentinel(NODE_2_HOST, Integer.valueOf(NODE_2_PORT))
                .sentinel(NODE_3_HOST, Integer.valueOf(NODE_3_PORT));
        if (AUTH.equals(ENABLE)) {
            try {
                sentinelConfig.setPassword(RedisPassword.of(PASSWORD));
            } catch (Exception e) {
                LOGGER.error("Fail to set Redis password. Detail: {}", e.getMessage());
            }
        } else {
            LOGGER.info("Redis auth disable");
        }
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxIdle(Integer.valueOf(MAX_IDLE));
        poolConfig.setMaxTotal(Integer.valueOf(MAX_ACTIVE));
        JedisConnectionFactory factory = new JedisConnectionFactory(sentinelConfig, poolConfig);
        factory.afterPropertiesSet();
        return factory;
    }

    public RedisTemplate<Object,Object> redisTemplate() {

        final RedisTemplate<Object,Object> redisTemplate = new RedisTemplate<Object,Object>();
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new GenericToStringSerializer<Object>(Object.class));
        redisTemplate.setHashValueSerializer(new JdkSerializationRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        if (USE_SENTINEL.equals(ENABLE)) {
            redisTemplate.setConnectionFactory(jedisSentinelConnectionFactory());
        } else {
            redisTemplate.setConnectionFactory(jedisConnectionFactory());
        }
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}

