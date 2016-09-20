/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.changedetection.state;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import org.gradle.api.internal.cache.HeapProportionalCacheSizer;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.CacheAccess;
import org.gradle.cache.internal.CacheDecorator;
import org.gradle.cache.internal.FileLock;
import org.gradle.cache.internal.MultiProcessSafePersistentIndexedCache;
import org.gradle.cache.internal.ThreadSafeCache;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.concurrent.StoppableExecutor;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class InMemoryTaskArtifactCache implements CacheDecorator, Stoppable {
    private final static Logger LOG = Logging.getLogger(InMemoryTaskArtifactCache.class);
    private final static Object NULL = new Object();
    private static final Map<String, Integer> CACHE_CAPS = new CacheCapSizer().calculateCaps();
    private final StoppableExecutor cacheUpdateExecutor;
    private final long batchWindow;

    public InMemoryTaskArtifactCache(ExecutorFactory executorFactory) {
        this(executorFactory, 5000L);
    }

    public InMemoryTaskArtifactCache(ExecutorFactory executorFactory, long batchWindow) {
        this.cacheUpdateExecutor = executorFactory.create("Cache update executor");
        this.batchWindow = batchWindow;
    }

    @Override
    public void stop() {
        cacheUpdateExecutor.stop();
    }

    static class CacheCapSizer {
        private static final Map<String, Integer> DEFAULT_CAP_SIZES = new HashMap<String, Integer>();

        static {
            DEFAULT_CAP_SIZES.put("fileSnapshots", 10000);
            DEFAULT_CAP_SIZES.put("taskArtifacts", 2000);
            DEFAULT_CAP_SIZES.put("fileHashes", 400000);
            DEFAULT_CAP_SIZES.put("compilationState", 1000);
        }

        final HeapProportionalCacheSizer sizer;

        CacheCapSizer(int maxHeapMB) {
            this.sizer = maxHeapMB > 0 ? new HeapProportionalCacheSizer(maxHeapMB) : new HeapProportionalCacheSizer();
        }

        CacheCapSizer() {
            this(0);
        }

        public Map<String, Integer> calculateCaps() {
            Map<String, Integer> capSizes = new HashMap<String, Integer>();
            for (Map.Entry<String, Integer> entry : DEFAULT_CAP_SIZES.entrySet()) {
                capSizes.put(entry.getKey(), sizer.scaleCacheSize(entry.getValue()));
            }
            return capSizes;
        }
    }


    private final Object lock = new Object();
    private final Cache<String, Cache<Object, Object>> cache = CacheBuilder.newBuilder()
            .maximumSize(CACHE_CAPS.size() * 2) //X2 to factor in a child build (for example buildSrc)
            .build();

    private final Map<String, FileLock.State> states = new HashMap<String, FileLock.State>();

    private CacheAccessWorker createCacheAccessWorker(CacheAccess cacheAccess) {
        CacheAccessWorker cacheAccessWorker = new CacheAccessWorker(cacheAccess, 1024, batchWindow, 10000L);
        cacheUpdateExecutor.execute(cacheAccessWorker);
        return cacheAccessWorker;
    }

    public <K, V> MultiProcessSafePersistentIndexedCache<K, V> decorate(final String cacheId, String cacheName, final MultiProcessSafePersistentIndexedCache<K, V> original, final CacheAccess cacheAccess) {
        final Cache<Object, Object> data = loadData(cacheId, cacheName);
        final CacheAccessWorker cacheAccessWorker = createCacheAccessWorker(cacheAccess);
        return new InMemoryDecoratedCache<K, V>(cacheAccessWorker, original, data, cacheId);
    }

    private class InMemoryDecoratedCache<K, V> implements MultiProcessSafePersistentIndexedCache<K, V>, ThreadSafeCache {
        private final CacheAccessWorker cacheAccessWorker;
        private final MultiProcessSafePersistentIndexedCache<K, V> original;
        private final Cache<Object, Object> data;
        private final String cacheId;

        public InMemoryDecoratedCache(CacheAccessWorker cacheAccessWorker, MultiProcessSafePersistentIndexedCache<K, V> original, Cache<Object, Object> data, String cacheId) {
            this.cacheAccessWorker = cacheAccessWorker;
            this.original = original;
            this.data = data;
            this.cacheId = cacheId;
        }

        public void close() {
            cacheAccessWorker.stop();
            original.close();
        }

        public V get(final K key) {
            assert key instanceof String || key instanceof Long || key instanceof File : "Unsupported key type: " + key;
            Object value = data.getIfPresent(key);
            if (value == NULL) {
                return null;
            }
            if (value != null) {
                return (V) value;
            }
            V out = cacheAccessWorker.read(new Callable<V>() {
                @Override
                public V call() throws Exception {
                    return original.get(key);
                }
            });
            data.put(key, out == null ? NULL : out);
            return out;
        }

        public void put(final K key, final V value) {
            data.put(key, value);
            cacheAccessWorker.enqueue(new Runnable() {
                @Override
                public void run() {
                    original.put(key, value);
                }
            });
        }

        public void remove(final K key) {
            data.put(key, NULL);
            cacheAccessWorker.enqueue(new Runnable() {
                @Override
                public void run() {
                    original.remove(key);
                }
            });
        }

        public void onStartWork(String operationDisplayName, FileLock.State currentCacheState) {
            boolean outOfDate = false;
            synchronized (lock) {
                FileLock.State previousState = states.get(cacheId);
                if (previousState == null) {
                    outOfDate = true;
                } else if (currentCacheState.hasBeenUpdatedSince(previousState)) {
                    LOG.info("Invalidating in-memory cache of {}", cacheId);
                    outOfDate = true;
                }
            }

            if (outOfDate) {
                data.invalidateAll();
            }
        }

        public void onEndWork(FileLock.State currentCacheState) {
            synchronized (lock) {
                states.put(cacheId, currentCacheState);
            }
        }
    }

    private Cache<Object, Object> loadData(String cacheId, String cacheName) {
        Cache<Object, Object> theData;
        synchronized (lock) {
            theData = this.cache.getIfPresent(cacheId);
            if (theData != null) {
                LOG.info("In-memory cache of {}: Size{{}}, {}", cacheId, theData.size() , theData.stats());
            } else {
                Integer maxSize = CACHE_CAPS.get(cacheName);
                assert maxSize != null : "Unknown cache.";
                LOG.debug("Creating In-memory cache of {}: MaxSize{{}}", cacheId, maxSize);
                LoggingEvictionListener evictionListener = new LoggingEvictionListener(cacheId, maxSize);
                theData = CacheBuilder.newBuilder().maximumSize(maxSize).recordStats().removalListener(evictionListener).build();
                evictionListener.setCache(theData);
                this.cache.put(cacheId, theData);
            }
        }
        return theData;
    }

    private static class LoggingEvictionListener implements RemovalListener<Object, Object> {
        private static Logger logger = Logging.getLogger(LoggingEvictionListener.class);
        private static final String EVICTION_MITIGATION_MESSAGE = "\nPerformance may suffer from in-memory cache misses. Increase max heap size of Gradle build process to reduce cache misses.";
        volatile int evictionCounter;
        private final String cacheId;
        private Cache<Object, Object> cache;
        private final int maxSize;
        private final int logInterval;

        private LoggingEvictionListener(String cacheId, int maxSize) {
            this.cacheId = cacheId;
            this.maxSize = maxSize;
            this.logInterval = maxSize / 10;
        }

        public void setCache(Cache<Object, Object> cache) {
            this.cache = cache;
        }

        @Override
        public void onRemoval(RemovalNotification<Object, Object> notification) {
            if (notification.getCause() == RemovalCause.SIZE) {
                if (evictionCounter % logInterval == 0) {
                    logger.log(LogLevel.INFO, "Cache entries evicted. In-memory cache of {}: Size{{}} MaxSize{{}}, {} {}", cacheId, cache.size(), maxSize, cache.stats(), EVICTION_MITIGATION_MESSAGE);
                }
                evictionCounter++;
            }
        }
    }
}
