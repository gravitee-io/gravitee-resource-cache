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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.resource.cache.api.Cache;
import io.gravitee.resource.cache.api.Element;
import io.gravitee.resource.cache.configuration.CacheResourceConfiguration;
import java.io.Serializable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * @author Kamiel Ahmadpour (kamiel.ahmadpour at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class StandaloneCacheResourceTest {

    private static final String RESOURCE_NAME = "my-cache-resource";
    private static final Long TIME_TO_LIVE = 6L;
    private static final int TIME_TO_SLEEP = 1;

    @Mock
    ExecutionContext executionContext;

    @Mock
    CacheResourceConfiguration configuration;

    @InjectMocks
    StandaloneCacheResource cacheResource;

    @Before
    public void setup() throws Exception {
        when(configuration.getName()).thenReturn(RESOURCE_NAME);
        when(configuration.getTimeToLiveSeconds()).thenReturn(TIME_TO_LIVE);

        cacheResource.doStart();
    }

    @After
    public void end() throws Exception {
        cacheResource.doStop();
    }

    @Test
    public void shouldPutToCache() throws InterruptedException {
        Cache cache = cacheResource.getCache(executionContext);
        Element element = buildElement(2 * TIME_TO_SLEEP);
        cache.put(element);
        Thread.sleep(TIME_TO_SLEEP * 1000L);

        assertNotNull(cache.get(element.key()));
    }

    @Test
    public void shouldBeCleanedUpFromCache() throws InterruptedException {
        Cache cache = cacheResource.getCache(executionContext);

        Element element = buildElement(TIME_TO_SLEEP);
        cache.put(element);

        Thread.sleep(TIME_TO_SLEEP * 1000L);
        assertNull(cache.get(element.key()));
    }

    @Test
    public void shouldBeRenewed() throws InterruptedException {
        Cache cache = cacheResource.getCache(executionContext);
        Element element = buildElement(2 * TIME_TO_SLEEP);
        cache.put(element);
        Thread.sleep(TIME_TO_SLEEP * 1000L);
        cache.get(element.key());
        Thread.sleep(TIME_TO_SLEEP * 1000L);

        assertNotNull(cache.get(element.key()));
    }

    private Element buildElement(int timeToLive) {
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
                return timeToLive;
            }
        };
    }
}
