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

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.replicatedmap.ReplicatedMap;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.resource.cache.configuration.CacheResourceConfiguration;
import java.io.*;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * @author Guillaume Cusnieux (guillaume.cusnieux at gravitee.io)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class CacheResourceTest {

    private static final String API_ID = "my-api";
    private static final String RESOURCE_NAME = "my-cache-resource";
    private static final Long TIME_TO_LIVE = 60L;

    @Mock
    HazelcastInstance hazelcastInstance;

    @Mock
    ReplicatedMap<Object, Object> replicatedMap;

    @Mock
    ExecutionContext executionContext;

    @Mock
    CacheResourceConfiguration configuration;

    @InjectMocks
    CacheResource cacheResource;

    @Before
    public void setup() {
        when(hazelcastInstance.getReplicatedMap(anyString())).thenReturn(replicatedMap);
        when(executionContext.getAttribute(ExecutionContext.ATTR_API)).thenReturn(API_ID);
        when(configuration.getName()).thenReturn(RESOURCE_NAME);
        when(configuration.getTimeToLiveSeconds()).thenReturn(TIME_TO_LIVE);
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

        verify(hazelcastInstance, times(1)).getReplicatedMap(API_ID + "_" + RESOURCE_NAME);
        verify(replicatedMap, times(1)).put("foobar", "value", TIME_TO_LIVE, TimeUnit.SECONDS);
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

        verify(hazelcastInstance, times(1)).getReplicatedMap(API_ID + "_" + RESOURCE_NAME);
        verify(replicatedMap, times(1)).put("foobar", "value", 30, TimeUnit.SECONDS);
    }
}
