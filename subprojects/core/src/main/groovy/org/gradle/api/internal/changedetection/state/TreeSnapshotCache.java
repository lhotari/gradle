/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentStore;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.EOFException;
import java.util.Collection;

public class TreeSnapshotCache {
    private final PersistentIndexedCache<Long, TreeSnapshot> cache;

    public TreeSnapshotCache(PersistentStore store, StringInterner stringInterner) {
        this.cache = store.createCache("treeSnapshots", Long.class, new TreeSnapshotSerializer(stringInterner));
    }

    public TreeSnapshot getTreeSnapshot(Long id) {
        return cache.get(id);
    }

    public long maybeStoreTreeSnapshot(final TreeSnapshot treeSnapshot) {
        return treeSnapshot.maybeStoreEntry(new Action<Long>() {
            @Override
            public void execute(Long assignedId) {
                cache.put(assignedId, treeSnapshot);
            }
        });
    }

    private static class TreeSnapshotSerializer implements org.gradle.internal.serialize.Serializer<TreeSnapshot> {
        private final IncrementalFileSnapshotSerializer incrementalFileSnapshotSerializer = new IncrementalFileSnapshotSerializer();
        private final StringInterner stringInterner;

        public TreeSnapshotSerializer(StringInterner stringInterner) {
            this.stringInterner = stringInterner;
        }

        @Override
        public TreeSnapshot read(Decoder decoder) throws EOFException, Exception {
            final long assignedId = decoder.readLong();
            final int entryCount = decoder.readSmallInt();
            ImmutableList.Builder<FileSnapshotWithKey> fileSnapshotWithKeyListBuilder = ImmutableList.builder();
            for (int i = 0; i < entryCount; i++) {
                String key = decoder.readString();
                fileSnapshotWithKeyListBuilder.add(new FileSnapshotWithKey(key, incrementalFileSnapshotSerializer.read(decoder)));
            }
            final ImmutableList<FileSnapshotWithKey> fileSnapshotWithKeyList = fileSnapshotWithKeyListBuilder.build();
            return new StoredTreeSnapshot(fileSnapshotWithKeyList, assignedId);
        }

        @Override
        public void write(Encoder encoder, TreeSnapshot value) throws Exception {
            encoder.writeLong(value.getAssignedId());
            encoder.writeSmallInt(value.getFileSnapshots().size());
            for (FileSnapshotWithKey fileSnapshotWithKey : value.getFileSnapshots()) {
                encoder.writeString(fileSnapshotWithKey.getKey());
                incrementalFileSnapshotSerializer.write(encoder, fileSnapshotWithKey.getIncrementalFileSnapshot());
            }
        }

    }

    private static class StoredTreeSnapshot implements TreeSnapshot {
        private final ImmutableList<FileSnapshotWithKey> fileSnapshotWithKeyList;
        private final long assignedId;

        public StoredTreeSnapshot(ImmutableList<FileSnapshotWithKey> fileSnapshotWithKeyList, long assignedId) {
            this.fileSnapshotWithKeyList = fileSnapshotWithKeyList;
            this.assignedId = assignedId;
        }

        @Override
        public Collection<FileSnapshotWithKey> getFileSnapshots() {
            return fileSnapshotWithKeyList;
        }

        @Override
        public Long getAssignedId() {
            return assignedId;
        }

        @Override
        public Long maybeStoreEntry(Action<Long> storeEntryAction) {
            return assignedId;
        }
    }
}
