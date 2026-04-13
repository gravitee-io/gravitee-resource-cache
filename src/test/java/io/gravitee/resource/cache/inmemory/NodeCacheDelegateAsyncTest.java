/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.resource.cache.inmemory;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import io.gravitee.node.api.cache.Cache;
import io.gravitee.resource.cache.NodeCacheDelegate;
import io.gravitee.resource.cache.api.Element;
import io.vertx.core.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NodeCacheDelegateAsyncTest {

    private static final int CACHE_TTL = 60;
    private static final String ELEMENT_KEY = "test-key";
    private static final String ELEMENT_VALUE = "test-value";

    private NodeCacheDelegate delegate;
    private Cache mockCache;

    @Before
    public void setup() {
        mockCache = mock(Cache.class);
        delegate = new NodeCacheDelegate("test-cache", CACHE_TTL, mockCache);
    }

    @Test
    public void shouldGetElementViaAsyncFuture() {
        when(mockCache.get(ELEMENT_KEY)).thenReturn(ELEMENT_VALUE);

        Future<Element> future = delegate.getAsync(ELEMENT_KEY);

        assertTrue(future.succeeded());
        assertNotNull(future.result());
        assertEquals(ELEMENT_KEY, future.result().key());
        assertEquals(ELEMENT_VALUE, future.result().value());
    }

    @Test
    public void shouldReturnNullElementForMissingKey() {
        when(mockCache.get("missing-key")).thenReturn(null);

        Future<Element> future = delegate.getAsync("missing-key");

        assertTrue(future.succeeded());
        assertNull(future.result());
    }

    @Test
    public void shouldPutElementViaAsyncFuture() {
        var element = mock(Element.class);
        when(element.key()).thenReturn(ELEMENT_KEY);
        when(element.value()).thenReturn(ELEMENT_VALUE);
        when(element.timeToLive()).thenReturn(30);

        Future<Void> future = delegate.putAsync(element);

        assertTrue(future.succeeded());
        verify(mockCache).put(ELEMENT_KEY, ELEMENT_VALUE, 30, TimeUnit.SECONDS);
    }

    @Test
    public void shouldEvictElementViaAsyncFuture() {
        Future<Void> future = delegate.evictAsync(ELEMENT_KEY);

        assertTrue(future.succeeded());
        verify(mockCache).evict(ELEMENT_KEY);
    }

    @Test
    public void shouldClearViaAsyncFuture() {
        Future<Void> future = delegate.clearAsync();

        assertTrue(future.succeeded());
        verify(mockCache).clear();
    }

    @Test
    public void shouldHandleGetErrorViaFailedFuture() {
        when(mockCache.get(ELEMENT_KEY)).thenThrow(new RuntimeException("cache failure"));

        Future<Element> future = delegate.getAsync(ELEMENT_KEY);

        assertTrue(future.failed());
        assertEquals("cache failure", future.cause().getMessage());
    }

    @Test
    public void shouldHandlePutErrorViaFailedFuture() {
        var element = mock(Element.class);
        when(element.key()).thenReturn(ELEMENT_KEY);
        when(element.value()).thenReturn(ELEMENT_VALUE);
        when(element.timeToLive()).thenReturn(30);
        doThrow(new RuntimeException("put failure")).when(mockCache).put(ELEMENT_KEY, ELEMENT_VALUE, 30, TimeUnit.SECONDS);

        Future<Void> future = delegate.putAsync(element);

        assertTrue(future.failed());
        assertEquals("put failure", future.cause().getMessage());
    }

    @Test
    public void shouldHandleEvictErrorViaFailedFuture() {
        doThrow(new RuntimeException("evict failure")).when(mockCache).evict(ELEMENT_KEY);

        Future<Void> future = delegate.evictAsync(ELEMENT_KEY);

        assertTrue(future.failed());
        assertEquals("evict failure", future.cause().getMessage());
    }
}
