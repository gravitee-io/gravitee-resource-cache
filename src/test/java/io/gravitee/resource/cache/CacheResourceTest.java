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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.hazelcast.config.*;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.resource.cache.configuration.CacheResourceConfiguration;
import java.io.*;
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
    CacheResource cacheResource;

    @Before
    public void setup() throws Exception {
        config = new Config();
        when(hazelcastInstance.getConfig()).thenReturn(config);
        when(hazelcastInstance.getMap(anyString())).thenReturn(map);
        when(executionContext.getAttribute(ExecutionContext.ATTR_API)).thenReturn(API_ID);
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

        verify(hazelcastInstance, times(1)).getMap("cache-resources_" + RESOURCE_NAME + "_" + API_ID);
        verify(map, times(1)).put("foobar", "value", TIME_TO_LIVE, TimeUnit.SECONDS);
    }

    @Test
    public void shouldPutToCacheWithTtl() {
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
                return 30;
            }
        };
        cache.put(element);

        verify(hazelcastInstance, times(1)).getMap("cache-resources_" + RESOURCE_NAME + "_" + API_ID);
        verify(map, times(1)).put("foobar", "value", 30, TimeUnit.SECONDS);
    }

    @Test
    public void shouldCreateMapConfig() {
        when(configuration.getMaxEntriesLocalHeap()).thenReturn(150L);
        when(configuration.getTimeToIdleSeconds()).thenReturn(20L);
        when(configuration.getTimeToLiveSeconds()).thenReturn(10L);

        cacheResource.getCache(executionContext);

        assertEquals(config.getMapConfigs().size(), 2);
        MapConfig resourceConfig = config.getMapConfig("cache-resources_" + RESOURCE_NAME + "_" + API_ID);
        assertEquals(resourceConfig.getMaxIdleSeconds(), 20);
        assertEquals(resourceConfig.getTimeToLiveSeconds(), 10);
        assertEquals(resourceConfig.getEvictionConfig().getMaxSizePolicy(), MaxSizePolicy.PER_NODE);
        assertEquals(resourceConfig.getEvictionConfig().getSize(), 150);
    }

    @Test
    public void shouldNotOverrideMapConfig() {
        MapConfig explicitResourceConfig = new MapConfig();
        explicitResourceConfig.setName("cache-resources_" + RESOURCE_NAME + "_" + API_ID);
        EvictionConfig evictionConfig = new EvictionConfig();
        evictionConfig.setMaxSizePolicy(MaxSizePolicy.FREE_HEAP_SIZE);
        explicitResourceConfig.setEvictionConfig(evictionConfig);
        config.addMapConfig(explicitResourceConfig);

        when(configuration.getTimeToLiveSeconds()).thenReturn(10L);

        cacheResource.getCache(executionContext);

        assertEquals(config.getMapConfigs().size(), 2);
        MapConfig resourceConfig = config.getMapConfig("cache-resources_" + RESOURCE_NAME + "_" + API_ID);
        assertEquals(resourceConfig.getMaxIdleSeconds(), 0);
        assertEquals(resourceConfig.getTimeToLiveSeconds(), 0);
        assertEquals(resourceConfig.getEvictionConfig().getMaxSizePolicy(), MaxSizePolicy.FREE_HEAP_SIZE);
    }

    @Test
    public void shouldCreateMapConfigOnlyForSmallerValues() {
        when(configuration.getMaxEntriesLocalHeap()).thenReturn(250L);
        when(configuration.getTimeToIdleSeconds()).thenReturn(700L);
        when(configuration.getTimeToLiveSeconds()).thenReturn(60L);

        cacheResource.getCache(executionContext);

        assertEquals(config.getMapConfigs().size(), 2);
        MapConfig resourceConfig = config.getMapConfig("cache-resources_" + RESOURCE_NAME + "_" + API_ID);
        assertEquals(resourceConfig.getMaxIdleSeconds(), 600);
        assertEquals(resourceConfig.getTimeToLiveSeconds(), 60);
        assertEquals(resourceConfig.getEvictionConfig().getMaxSizePolicy(), MaxSizePolicy.PER_NODE);
        assertEquals(resourceConfig.getEvictionConfig().getSize(), 200);
    }
}
