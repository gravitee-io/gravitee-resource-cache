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
package io.gravitee.resource.cache.inmemory;

import static org.mockito.Mockito.*;

import io.gravitee.node.api.cache.Cache;
import io.gravitee.resource.cache.api.Element;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class InMemoryCacheDelegateTest {

    private static final int CACHE_TTL = 60;
    private static String ELEMENT_KEY = "test-key";
    private static String ELEMENT_VALUE = "test-value";

    private InMemoryCacheDelegate inMemoryCacheDelegate;

    private Cache mockCache;
    private Element mockElement;

    @Before
    public void setup() {
        mockCache = mock(Cache.class);
        inMemoryCacheDelegate = new InMemoryCacheDelegate("test-cache", CACHE_TTL, mockCache);
        mockElement = mock(Element.class);
        when(mockElement.key()).thenReturn(ELEMENT_KEY);
        when(mockElement.value()).thenReturn(ELEMENT_VALUE);
    }

    @Test
    public void shouldPutWithElementTtlLowerThanCacheTtl() {
        when(mockElement.timeToLive()).thenReturn(CACHE_TTL - 10);

        inMemoryCacheDelegate.put(mockElement);

        verify(mockCache).put(ELEMENT_KEY, ELEMENT_VALUE, CACHE_TTL - 10, TimeUnit.SECONDS);
    }

    @Test
    public void shouldPutWithElementTtlHigherThanCacheTtl() {
        when(mockElement.timeToLive()).thenReturn(CACHE_TTL + 10);

        inMemoryCacheDelegate.put(mockElement);

        verify(mockCache).put(ELEMENT_KEY, ELEMENT_VALUE, CACHE_TTL, TimeUnit.SECONDS);
    }

    @Test
    public void shouldPutWithElementTtlZero() {
        when(mockElement.timeToLive()).thenReturn(0);

        inMemoryCacheDelegate.put(mockElement);

        verify(mockCache).put(ELEMENT_KEY, ELEMENT_VALUE, CACHE_TTL, TimeUnit.SECONDS);
    }
}
