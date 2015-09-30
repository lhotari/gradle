/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.internal.serialize.SerializerRegistry;
import org.gradle.util.ChangeListener;
import org.gradle.util.NoOpChangeListener;

import java.io.File;
import java.math.BigInteger;
import java.util.*;

public class DefaultFileCollectionSnapshotter implements FileCollectionSnapshotter {
    private final FileSnapshotter snapshotter;
    private TaskArtifactStateCacheAccess cacheAccess;

    public DefaultFileCollectionSnapshotter(FileSnapshotter snapshotter, TaskArtifactStateCacheAccess cacheAccess) {
        this.snapshotter = snapshotter;
        this.cacheAccess = cacheAccess;
    }

    public void registerSerializers(SerializerRegistry<FileCollectionSnapshot> registry) {
        registry.register(FileCollectionSnapshotImpl.class, new DefaultFileSnapshotterSerializer());
    }

    public FileCollectionSnapshot emptySnapshot() {
        return new FileCollectionSnapshotImpl(new TreeMap<String, IncrementalFileSnapshot>());
    }

    public FileCollectionSnapshot snapshot(FileCollection input) {
        final Set<File> files = input.getAsFileTree().getFiles();
        if (files.isEmpty()) {
            return new FileCollectionSnapshotImpl(new TreeMap<String, IncrementalFileSnapshot>());
        }
        final SortedMap<String, IncrementalFileSnapshot> snapshots = new TreeMap<String, IncrementalFileSnapshot>();
        cacheAccess.useCache("Create file snapshot", new Runnable() {
            public void run() {
                for (File file : files) {
                    if (file.isFile()) {
                        snapshots.put(file.getAbsolutePath(), new FileHashSnapshot(snapshotter.snapshot(file).getHash()));
                    } else if (file.isDirectory()) {
                        snapshots.put(file.getAbsolutePath(), new DirSnapshot());
                    } else {
                        snapshots.put(file.getAbsolutePath(), new MissingFileSnapshot());
                    }
                }
            }
        });
        return new FileCollectionSnapshotImpl(snapshots);
    }

    static interface IncrementalFileSnapshot {
        boolean isUpToDate(IncrementalFileSnapshot snapshot);
    }

    static class FileHashSnapshot implements IncrementalFileSnapshot, FileSnapshot {
        final byte[] hash;

        public FileHashSnapshot(byte[] hash) {
            this.hash = hash;
        }

        public boolean isUpToDate(IncrementalFileSnapshot snapshot) {
            if (!(snapshot instanceof FileHashSnapshot)) {
                return false;
            }

            FileHashSnapshot other = (FileHashSnapshot) snapshot;
            return Arrays.equals(hash, other.hash);
        }

        @Override
        public String toString() {
            return new BigInteger(1, hash).toString(16);
        }

        public byte[] getHash() {
            return hash;
        }
    }

    static class DirSnapshot implements IncrementalFileSnapshot {
        public boolean isUpToDate(IncrementalFileSnapshot snapshot) {
            return snapshot instanceof DirSnapshot;
        }
    }

    static class MissingFileSnapshot implements IncrementalFileSnapshot {
        public boolean isUpToDate(IncrementalFileSnapshot snapshot) {
            return snapshot instanceof MissingFileSnapshot;
        }
    }

    static class FileCollectionSnapshotImpl implements FileCollectionSnapshot {
        final SortedMap<String, IncrementalFileSnapshot> snapshots;

        public FileCollectionSnapshotImpl(SortedMap<String, IncrementalFileSnapshot> snapshots) {
            this.snapshots = snapshots;
        }

        public FileCollection getFiles() {
            List<File> files = new ArrayList<File>();
            for (Map.Entry<String, IncrementalFileSnapshot> entry : snapshots.entrySet()) {
                if (entry.getValue() instanceof FileHashSnapshot) {
                    files.add(new File(entry.getKey()));
                }
            }
            return new SimpleFileCollection(files);
        }

        public FilesSnapshotSet getSnapshot() {
            return new FilesSnapshotSet() {
                public FileSnapshot findSnapshot(File file) {
                    IncrementalFileSnapshot s = snapshots.get(file.getAbsolutePath());
                    if (s instanceof FileSnapshot) {
                        return (FileSnapshot) s;
                    }
                    return null;
                }
            };
        }

        public ChangeIterator<String> iterateChangesSince(FileCollectionSnapshot oldSnapshot) {
            return new SnapshotChangeIterator(this, oldSnapshot);
        }

        public Diff changesSince(final FileCollectionSnapshot oldSnapshot) {
            final FileCollectionSnapshotImpl other = (FileCollectionSnapshotImpl) oldSnapshot;
            return new Diff() {
                public FileCollectionSnapshot applyTo(FileCollectionSnapshot snapshot) {
                    return applyTo(snapshot, new NoOpChangeListener<Merge>());
                }

                public FileCollectionSnapshot applyTo(FileCollectionSnapshot snapshot, final ChangeListener<Merge> listener) {
                    FileCollectionSnapshotImpl target = (FileCollectionSnapshotImpl) snapshot;
                    final SortedMap<String, IncrementalFileSnapshot> newSnapshots = new TreeMap<String, IncrementalFileSnapshot>(target.snapshots);
                    diff(snapshots, other.snapshots, new MapMergeChangeListener<String, IncrementalFileSnapshot>(listener, newSnapshots));
                    return new FileCollectionSnapshotImpl(newSnapshots);
                }
            };
        }

        private void diff(SortedMap<String, IncrementalFileSnapshot> snapshots, SortedMap<String, IncrementalFileSnapshot> oldSnapshots,
                          ChangeListener<Map.Entry<String, IncrementalFileSnapshot>> listener) {
            Map<String, IncrementalFileSnapshot> otherSnapshots = new HashMap<String, IncrementalFileSnapshot>(oldSnapshots);
            for (Map.Entry<String, IncrementalFileSnapshot> entry : snapshots.entrySet()) {
                IncrementalFileSnapshot otherFile = otherSnapshots.remove(entry.getKey());
                if (otherFile == null) {
                    listener.added(entry);
                } else if (!entry.getValue().isUpToDate(otherFile)) {
                    listener.changed(entry);
                }
            }
            for (Map.Entry<String, IncrementalFileSnapshot> entry : otherSnapshots.entrySet()) {
                listener.removed(entry);
            }
        }
    }

    private static class SnapshotChangeIterator implements FileCollectionSnapshot.ChangeIterator<String> {
        private final Iterator<Map.Entry<String, IncrementalFileSnapshot>> thisIterator;
        private final Iterator<Map.Entry<String, IncrementalFileSnapshot>> otherIterator;
        private Iterator<String> removedFiles;

        public SnapshotChangeIterator(FileCollectionSnapshot snapshot, FileCollectionSnapshot oldSnapshot) {
            this.thisIterator = ((FileCollectionSnapshotImpl) snapshot).snapshots.entrySet().iterator();
            this.otherIterator = ((FileCollectionSnapshotImpl) oldSnapshot).snapshots.entrySet().iterator();
        }

        public boolean next(ChangeListener<String> listener) {
            while (thisIterator.hasNext()) {
                Map.Entry<String, IncrementalFileSnapshot> entry = thisIterator.next();
                Map.Entry<String, IncrementalFileSnapshot> otherEntry = otherIterator.hasNext() ? otherIterator.next() : null;

                if (otherEntry == null || !otherEntry.getKey().equals(entry.getKey())) {
                    listener.added(entry.getKey());
                    return true;
                } else if (!entry.getValue().isUpToDate(otherEntry.getValue())) {
                    listener.changed(entry.getKey());
                    return true;
                }
            }

            // Create a single iterator to use for all of the removed files
            if (removedFiles == null) {
                //removedFiles = otherSnapshots.keySet().iterator();
            }

            if (removedFiles.hasNext()) {
                listener.removed(removedFiles.next());
                return true;
            }

            return false;
        }
    }

}
