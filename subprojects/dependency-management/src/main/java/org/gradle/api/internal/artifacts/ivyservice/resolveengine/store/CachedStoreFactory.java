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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.store;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.gradle.api.internal.cache.HeapProportionalCacheSizer;
import org.gradle.api.internal.cache.Store;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Factory;

import java.io.Closeable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.gradle.util.Clock.prettyTime;

public class CachedStoreFactory<T> implements Closeable {

    private static final Logger LOG = Logging.getLogger(CachedStoreFactory.class);

    private final Cache<Object, T> cache;
    private final Cache<Object, T> secondLayerCache;
    private final Stats stats;
    private String displayName;

    public CachedStoreFactory(String displayName) {
        this.displayName = displayName;
        cache = CacheBuilder.newBuilder().maximumSize(100).expireAfterAccess(10000, TimeUnit.MILLISECONDS).build();
        secondLayerCache = CacheBuilder.newBuilder().maximumSize(new HeapProportionalCacheSizer().scaleCacheSize(1000)).softValues().build();
        stats = new Stats();
    }

    public Store<T> createCachedStore(final Object id) {
        return new SimpleStore<T>(cache, secondLayerCache, id, stats);
    }

    public void close() {
        if (LOG.isDebugEnabled()) {
            LOG.debug(displayName + " cache closed. Cache reads: "
                    + stats.readsFromCache + ", disk reads: "
                    + stats.readsFromDisk + " (avg: " + prettyTime(stats.getDiskReadsAvgMs()) + ", total: " + prettyTime(stats.diskReadsTotalMs.get()) + ")"
                    + " Cache size: " + cache.size()
                    + " 2nd layer Cache size: " + secondLayerCache.size());
        }
        cache.invalidateAll();
        secondLayerCache.invalidateAll();
    }

    private static class Stats {
        private final AtomicLong diskReadsTotalMs = new AtomicLong();
        private final AtomicLong readsFromCache = new AtomicLong();
        private final AtomicLong readsFromDisk = new AtomicLong();

        public void readFromDisk(long start) {
            long duration = System.currentTimeMillis() - start;
            readsFromDisk.incrementAndGet();
            diskReadsTotalMs.addAndGet(duration);
        }

        public void readFromCache() {
            readsFromCache.incrementAndGet();
        }

        public long getDiskReadsAvgMs() {
            if (readsFromDisk.get() == 0) {
                return 0;
            }
            return diskReadsTotalMs.get() / readsFromDisk.get();
        }
    }

    private static class SimpleStore<T> implements Store<T> {
        private final Cache<Object, T> cache;
        private final Cache<Object, T> secondLayerCache;
        private final Object id;
        private final Stats stats;

        public SimpleStore(Cache<Object, T> cache, Cache<Object, T> secondLayerCache, Object id, Stats stats) {
            this.cache = cache;
            this.secondLayerCache = secondLayerCache;
            this.id = id;
            this.stats = stats;
        }

        public T load(Factory<T> createIfNotPresent) {
            T out = cache.getIfPresent(id);
            if (out == null) {
                secondLayerCache.getIfPresent(id);
            }
            if (out != null) {
                stats.readFromCache();
                return out;
            }
            long start = System.currentTimeMillis();
            T value = createIfNotPresent.create();
            stats.readFromDisk(start);
            cache.put(id, value);
            secondLayerCache.put(id, value);
            return value;
        }
    }
}
