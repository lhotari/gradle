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

import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
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
        final FileTree fileTree = input.getAsFileTree();
        final SortedMap<String, IncrementalFileSnapshot> snapshots = new TreeMap<String, IncrementalFileSnapshot>();
        cacheAccess.useCache("Create file snapshot", new Runnable() {
            public void run() {
                fileTree.visit(new EmptyFileVisitor() {
                    @Override
                    public void visitFile(FileVisitDetails fileDetails) {
                        File file = fileDetails.getFile();
                        String absolutePath = file.getAbsolutePath();
                        if (file.isFile()) {
                            snapshots.put(absolutePath, new FileHashSnapshot(snapshotter.snapshot(file).getHash()));
                        } else if (file.isDirectory()) {
                            snapshots.put(absolutePath, new DirSnapshot());
                        } else {
                            snapshots.put(absolutePath, new MissingFileSnapshot());
                        }
                    }
                });
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
        final SortedMap<String, IncrementalFileSnapshot> snapshotMap;

        public FileCollectionSnapshotImpl(SortedMap<String, IncrementalFileSnapshot> snapshotMap) {
            this.snapshotMap = snapshotMap;
        }

        public FileCollection getFiles() {
            List<File> files = new ArrayList<File>();
            for (Map.Entry<String, IncrementalFileSnapshot> entry : snapshotMap.entrySet()) {
                if (entry.getValue() instanceof FileHashSnapshot) {
                    files.add(new File(entry.getKey()));
                }
            }
            return new SimpleFileCollection(files);
        }

        public SortedMap<String, IncrementalFileSnapshot> getSnapshotMap() {
            return snapshotMap;
        }

        public FilesSnapshotSet getSnapshot() {
            return new FilesSnapshotSet() {
                public FileSnapshot findSnapshot(File file) {
                    IncrementalFileSnapshot s = snapshotMap.get(file.getAbsolutePath());
                    if (s instanceof FileSnapshot) {
                        return (FileSnapshot) s;
                    }
                    return null;
                }
            };
        }

        public ChangeIterator<String> iterateChangesSince(FileCollectionSnapshot oldSnapshot) {
            return iterateEntryChangesSince(oldSnapshot).adaptToFilenameChangeIterator();
        }

        private SnapshotChangeIterator iterateEntryChangesSince(FileCollectionSnapshot oldSnapshot) {
            return new SnapshotChangeIterator(oldSnapshot, this);
        }

        public Diff changesSince(final FileCollectionSnapshot oldSnapshot) {
            return new Diff() {
                public FileCollectionSnapshot applyTo(FileCollectionSnapshot snapshot) {
                    return applyTo(snapshot, new NoOpChangeListener<Merge>());
                }

                public FileCollectionSnapshot applyTo(FileCollectionSnapshot snapshot, final ChangeListener<Merge> listener) {
                    FileCollectionSnapshotImpl target = (FileCollectionSnapshotImpl) snapshot;
                    final SortedMap<String, IncrementalFileSnapshot> newSnapshots = new TreeMap<String, IncrementalFileSnapshot>(target.snapshotMap);
                    handleChanges(iterateEntryChangesSince(oldSnapshot), new MapMergeChangeListener<String, IncrementalFileSnapshot>(listener, newSnapshots));
                    return new FileCollectionSnapshotImpl(newSnapshots);
                }
            };
        }

        static <T> void handleChanges(ChangeIterator<Map.Entry<String, T>> changeIterator,
                                      ChangeListener<Map.Entry<String, T>> listener) {
            while (changeIterator.next(listener)) {
                ;
            }
        }
    }


}

