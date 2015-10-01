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

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.googlecode.concurrentlinkedhashmap.EvictionListener;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.internal.CacheDecorator;
import org.gradle.cache.internal.FileLock;
import org.gradle.cache.internal.MultiProcessSafePersistentIndexedCache;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class InMemoryTaskArtifactCache implements CacheDecorator {
    private final static Logger LOG = Logging.getLogger(InMemoryTaskArtifactCache.class);
    private final static Object NULL = new Object();
    private static final Map<String, Integer> CACHE_CAPS = new CacheCapSizer().calculateCaps();

    static class CacheCapSizer {
        private static final Map<String, Integer> DEFAULT_CAP_SIZES = new HashMap<String, Integer>();

        private static final int DEFAULT_SIZES_MAX_HEAP_MB = 910; // when -Xmx1024m, Runtime.maxMemory() returns about 910
        private static final int ASSUMED_USED_HEAP = 150; // assume that Gradle itself uses about 150MB heap

        private static final double MIN_RATIO = 0.2d;

        static {
            DEFAULT_CAP_SIZES.put("fileSnapshots", 10000);
            DEFAULT_CAP_SIZES.put("taskArtifacts", 2000);
            DEFAULT_CAP_SIZES.put("outputFileStates", 200000);
            DEFAULT_CAP_SIZES.put("fileHashes", 400000);
            DEFAULT_CAP_SIZES.put("compilationState", 1000);
        }

        private final int maxHeapMB;

        CacheCapSizer(int maxHeapMB) {
            this.maxHeapMB = maxHeapMB;
        }

        CacheCapSizer() {
            this(calculateMaxHeapMB());
        }

        private static int calculateMaxHeapMB() {
            return (int) (Runtime.getRuntime().maxMemory() / (1024 * 1024));
        }

        public Map<String, Integer> calculateCaps() {
            double ratio = calculateRatio();
            Map<String, Integer> capSizes = new HashMap<String, Integer>();
            for (Map.Entry<String, Integer> entry : DEFAULT_CAP_SIZES.entrySet()) {
                capSizes.put(entry.getKey(), calculateNewSize(entry.getValue(), ratio));
            }
            return capSizes;
        }

        private int calculateNewSize(int oldvalue, double ratio) {
            return (int) ((double) oldvalue * ratio) / 100 * 100;
        }

        private double calculateRatio() {
            return Math.max((double) (maxHeapMB - ASSUMED_USED_HEAP) / (double) (DEFAULT_SIZES_MAX_HEAP_MB - ASSUMED_USED_HEAP), MIN_RATIO);
        }
    }


    private final Object lock = new Object();
    private final ConcurrentMap<String, ConcurrentMap<Object, Object>> caches = new ConcurrentLinkedHashMap.Builder<String, ConcurrentMap<Object, Object>>()
        .maximumWeightedCapacity(CACHE_CAPS.size() * 2) //X2 to factor in a child build (for example buildSrc)
        .build();

    private final Map<String, FileLock.State> states = new HashMap<String, FileLock.State>();

    public <K, V> MultiProcessSafePersistentIndexedCache<K, V> decorate(final String cacheId, String cacheName, final MultiProcessSafePersistentIndexedCache<K, V> original) {
        final ConcurrentMap<Object, Object> data = loadData(cacheId, cacheName);

        return new MultiProcessSafePersistentIndexedCache<K, V>() {
            public void close() {
                original.close();
            }

            public V get(K key) {
                assert key instanceof String || key instanceof Long || key instanceof File : "Unsupported key type: " + key;
                Object value = data.get(key);
                if (value == NULL) {
                    return null;
                } else
                if (value != null) {
                    return (V) value;
                }
                V out = original.get(key);
                data.put(key, out == null ? NULL : out);
                return out;
            }

            public void put(K key, V value) {
                original.put(key, value);
                data.put(key, value);
            }

            public void remove(K key) {
                data.put(key, NULL);
                original.remove(key);
            }

            public void onStartWork(String operationDisplayName, FileLock.State currentCacheState) {
                boolean outOfDate;
                synchronized (lock) {
                    FileLock.State previousState = states.get(cacheId);
                    outOfDate = previousState == null || currentCacheState.hasBeenUpdatedSince(previousState);
                }

                if (outOfDate) {
                    LOG.info("Invalidating in-memory ConcurrentMap of {}", cacheId);
                    data.clear();
                }
            }

            public void onEndWork(FileLock.State currentCacheState) {
                synchronized (lock) {
                    states.put(cacheId, currentCacheState);
                }
            }
        };
    }

    private ConcurrentMap<Object, Object> loadData(String cacheId, String cacheName) {
        ConcurrentMap<Object, Object> theData;
        synchronized (lock) {
            theData = this.caches.get(cacheId);
            if (theData != null) {
                LOG.info("In-memory ConcurrentMap of {}: Size{{}}", cacheId, theData.size());
            } else {
                Integer maxSize = CACHE_CAPS.get(cacheName);
                assert maxSize != null : "Unknown ConcurrentMap.";
                LOG.info("Creating In-memory ConcurrentMap of {}: MaxSize{{}}", cacheId, maxSize);
                LoggingEvictionListener evictionListener = new LoggingEvictionListener(cacheId, maxSize);
                theData = new ConcurrentLinkedHashMap.Builder<Object, Object>().maximumWeightedCapacity(maxSize).initialCapacity(maxSize).listener(evictionListener).build();
                evictionListener.setCache(theData);
                ConcurrentMap<Object, Object> previousEntry = this.caches.putIfAbsent(cacheId, theData);
                if (previousEntry != null) {
                    theData = previousEntry;
                }
            }
        }
        return theData;
    }

    private static class LoggingEvictionListener implements EvictionListener<Object, Object> {
        private static Logger logger = Logging.getLogger(LoggingEvictionListener.class);
        private static final String EVICTION_MITIGATION_MESSAGE = "\nPerformance may suffer from in-memory ConcurrentMap misses. Increase max heap size of Gradle build process to reduce ConcurrentMap misses.";
        volatile int evictionCounter;
        private final String cacheId;
        private ConcurrentMap<Object, Object> ConcurrentMap;
        private final int maxSize;
        private final int logInterval;

        private LoggingEvictionListener(String cacheId, int maxSize) {
            this.cacheId = cacheId;
            this.maxSize = maxSize;
            this.logInterval = maxSize / 10;
        }

        public void setCache(ConcurrentMap<Object, Object> ConcurrentMap) {
            this.ConcurrentMap = ConcurrentMap;
        }

        @Override
        public void onEviction(Object key, Object value) {
            if (evictionCounter % logInterval == 0) {
                logger.log(LogLevel.INFO, "ConcurrentMap entries evicted. In-memory ConcurrentMap of {}: Size{{}} MaxSize{{}}, {}", cacheId, ConcurrentMap.size(), maxSize, EVICTION_MITIGATION_MESSAGE);
            }
            evictionCounter++;
        }
    }
}
