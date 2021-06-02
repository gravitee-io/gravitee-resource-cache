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

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MaxSizePolicy;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.resource.cache.api.Cache;
import io.gravitee.resource.cache.api.Element;
import io.gravitee.resource.cache.configuration.CacheResourceConfiguration;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

/**
 * @author Guillaume Cusnieux (guillaume.cusnieux at gravitee.io)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class CacheResourceTest {

    private static final String API_ID = "my-api";
    private static final String RESOURCE_NAME = "my-cache-resource";
    private static final Long TIME_TO_LIVE = 60L;

    Config config;

    @Mock
    HazelcastInstance hazelcastInstance;

    @Mock
    IMap<Object, Object> map;

    @Mock
    ExecutionContext executionContext;

    @Mock
    CacheResourceConfiguration configuration;

    @Mock
    ApplicationContext applicationContext;

    @InjectMocks
    HazelcastCacheResource cacheResource;

    @Before
    public void setup() throws Exception {
        config = new Config();
        when(hazelcastInstance.getConfig()).thenReturn(config);
        when(hazelcastInstance.getMap(anyString())).thenReturn(map);
        when(configuration.getName()).thenReturn(RESOURCE_NAME);
        when(configuration.getTimeToLiveSeconds()).thenReturn(TIME_TO_LIVE);

        MapConfig mapConfig = new MapConfig();
        mapConfig.setName("cache-resources_*");
        mapConfig.setTimeToLiveSeconds(600);
        mapConfig.setMaxIdleSeconds(600);
        EvictionConfig evictionConfig = new EvictionConfig();
        evictionConfig.setMaxSizePolicy(MaxSizePolicy.PER_NODE);
        evictionConfig.setSize(200);
        mapConfig.setEvictionConfig(evictionConfig);

        config.addMapConfig(mapConfig);

        when(applicationContext.getBean(HazelcastInstance.class)).thenReturn(hazelcastInstance);

        cacheResource.doStart();
    }

    @After
    public void end() throws Exception {
        cacheResource.doStop();
    }

    @Test
    public void shouldPutToCache() {
        Cache cache = cacheResource.getCache(executionContext);

        Element element = new Element() {
            @Override
            public Object key() {
                return "foobar";
            }

            @Override
            public Serializable value() {
                return "value";
            }

            @Override
            public int timeToLive() {
                return 120;
            }
        };
        cache.put(element);

        verify(hazelcastInstance, times(1)).getMap(argThat(cacheName -> cacheName.startsWith("cache-resources_" + RESOURCE_NAME + "_")));
        verify(map, times(1)).put("foobar", "value", TIME_TO_LIVE, TimeUnit.SECONDS);
    }

    @Test
    public void shouldPutToCacheWithTtl() {
        Cache cache = cacheResource.getCache(executionContext);

        Element element = buildElement();
        cache.put(element);

        verify(hazelcastInstance, times(1)).getMap(argThat(cacheName -> cacheName.startsWith("cache-resources_" + RESOURCE_NAME + "_")));
        verify(map, times(1)).put("foobar", "value", 30, TimeUnit.SECONDS);
    }

    @Test
    public void shouldDestroyCache() throws Exception {
        final Cache cache = cacheResource.getCache(executionContext);
        assertNotNull(cache);
        cacheResource.doStop();
        final Cache cacheAfterDoStop = cacheResource.getCache(executionContext);
        assertNull(cacheAfterDoStop);
    }

    private Element buildElement() {
        return new Element() {
            @Override
            public Object key() {
                return "foobar";
            }

            @Override
            public Serializable value() {
                return "value";
            }

            @Override
            public int timeToLive() {
                return 30;
            }
        };
    }
}
