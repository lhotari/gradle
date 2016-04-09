/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.util.HashMap;
import java.util.Map;

class DefaultFileSnapshotterSerializer implements Serializer<FileCollectionSnapshotImpl> {
    private final StringInterner stringInterner;
    private final TreeSnapshotCache treeSnapshotCache;
    private final IncrementalFileSnapshotSerializer incrementalFileSnapshotSerializer = new IncrementalFileSnapshotSerializer();

    public DefaultFileSnapshotterSerializer(StringInterner stringInterner, TreeSnapshotCache treeSnapshotCache) {
        this.stringInterner = stringInterner;
        this.treeSnapshotCache = treeSnapshotCache;
    }

    public FileCollectionSnapshotImpl read(Decoder decoder) throws Exception {
        Map<String, IncrementalFileSnapshot> snapshots = new HashMap<String, IncrementalFileSnapshot>();
        FileCollectionSnapshotImpl snapshot = new FileCollectionSnapshotImpl(snapshots);
        int snapshotsCount = decoder.readSmallInt();
        for (int i = 0; i < snapshotsCount; i++) {
            String key = stringInterner.intern(decoder.readString());
            snapshots.put(key, incrementalFileSnapshotSerializer.read(decoder));
        }
        return snapshot;
    }

    public void write(Encoder encoder, FileCollectionSnapshotImpl value) throws Exception {
        encoder.writeSmallInt(value.snapshots.size());
        for (Map.Entry<String, IncrementalFileSnapshot> entry : value.snapshots.entrySet()) {
            encoder.writeString(entry.getKey());
            incrementalFileSnapshotSerializer.write(encoder, entry.getValue());
        }
    }

}
