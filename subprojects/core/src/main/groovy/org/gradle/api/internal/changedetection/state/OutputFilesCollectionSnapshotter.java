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
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.SerializerRegistry;
import org.gradle.util.ChangeListener;

import java.io.File;
import java.util.*;

/**
 * Takes a snapshot of the output files of a task.
 */
public class OutputFilesCollectionSnapshotter implements FileCollectionSnapshotter {
    private final FileCollectionSnapshotter snapshotter;
    private final StringInterner stringInterner;

    public OutputFilesCollectionSnapshotter(FileCollectionSnapshotter snapshotter, StringInterner stringInterner) {
        this.snapshotter = snapshotter;
        this.stringInterner = stringInterner;
    }

    public void registerSerializers(SerializerRegistry registry) {
        DefaultSerializerRegistry nested = new DefaultSerializerRegistry();
        snapshotter.registerSerializers(nested);
        registry.register(OutputFilesSnapshot.class, new OutputFilesSnapshotSerializer(nested.build(FileCollectionSnapshot.class), stringInterner));
    }

    public FileCollectionSnapshot emptySnapshot() {
        return new OutputFilesSnapshot(Collections.<String>emptySet(), snapshotter.emptySnapshot());
    }

    @Override
    public FileCollectionSnapshot.PreCheck preCheck(FileCollection files, boolean allowReuse) {
        return snapshotter.preCheck(files, allowReuse);
    }

    private Set<String> getRoots(FileCollection files) {
        Set<String> roots = new LinkedHashSet<String>();
        for (File file : files.getFiles()) {
            roots.add(stringInterner.intern(file.getAbsolutePath()));
        }
        return roots;
    }

    /**
     * Returns a new snapshot that ignores new files between 2 previous snapshots
     */
    public OutputFilesSnapshot createOutputSnapshot(FileCollectionSnapshot previous, FileCollectionSnapshot before, FileCollectionSnapshot after, FileCollection roots) {
        FileCollectionSnapshot lastExecutionFilesUpdatedToStateBeforeTask = updateFrom(previous, before);
        final FileCollectionSnapshot filesSnapshot = applyAllChangesSince(after, before, lastExecutionFilesUpdatedToStateBeforeTask);
        return new OutputFilesSnapshot(getRoots(roots), filesSnapshot);
    }

    private static FileCollectionSnapshot updateFrom(FileCollectionSnapshot fileCollectionSnapshot, FileCollectionSnapshot newSnapshot) {
        if (fileCollectionSnapshot.isEmpty()) {
            // Nothing to update
            return fileCollectionSnapshot;
        }
        if (newSnapshot.isEmpty()) {
            // Everything has been removed
            return newSnapshot;
        }

        // Update entries from new snapshot
        Map<String, IncrementalFileSnapshot> newSnapshots = new HashMap<String, IncrementalFileSnapshot>(fileCollectionSnapshot.getSnapshots().size());
        for (String path : fileCollectionSnapshot.getSnapshots().keySet()) {
            IncrementalFileSnapshot newValue = newSnapshot.getSnapshots().get(path);
            if (newValue != null) {
                newSnapshots.put(path, newValue);
            }
        }
        return new FileCollectionSnapshotImpl(newSnapshots);
    }

    private static FileCollectionSnapshot applyAllChangesSince(FileCollectionSnapshot fileCollectionSnapshot, FileCollectionSnapshot oldSnapshot, FileCollectionSnapshot target) {
        Map<String, IncrementalFileSnapshot> newSnapshots = new HashMap<String, IncrementalFileSnapshot>(target.getSnapshots());
        diff(fileCollectionSnapshot.getSnapshots(), oldSnapshot.getSnapshots(), newSnapshots);
        return new FileCollectionSnapshotImpl(newSnapshots);
    }

    private static void diff(Map<String, IncrementalFileSnapshot> snapshots, Map<String, IncrementalFileSnapshot> oldSnapshots, Map<String, IncrementalFileSnapshot> target) {
        if (oldSnapshots.isEmpty()) {
            // Everything is new
            target.putAll(snapshots);
        } else {
            Map<String, IncrementalFileSnapshot> otherSnapshots = new HashMap<String, IncrementalFileSnapshot>(oldSnapshots);
            for (Map.Entry<String, IncrementalFileSnapshot> entry : snapshots.entrySet()) {
                IncrementalFileSnapshot otherFile = otherSnapshots.remove(entry.getKey());
                if (otherFile == null) {
                    target.put(entry.getKey(), entry.getValue());
                } else if (!entry.getValue().isContentAndMetadataUpToDate(otherFile)) {
                    target.put(entry.getKey(), entry.getValue());
                }
            }
            for (Map.Entry<String, IncrementalFileSnapshot> entry : otherSnapshots.entrySet()) {
                target.remove(entry.getKey());
            }
        }
    }

    @Override
    public FileCollectionSnapshot snapshot(FileCollectionSnapshot.PreCheck preCheck) {
        return new OutputFilesSnapshot(getRoots(preCheck.getFiles()), snapshotter.snapshot(preCheck));
    }

    static class OutputFilesSnapshot implements FileCollectionSnapshot {
        final Set<String> roots;
        final FileCollectionSnapshot filesSnapshot;

        public OutputFilesSnapshot(Set<String> roots, FileCollectionSnapshot filesSnapshot) {
            this.roots = roots;
            this.filesSnapshot = filesSnapshot;
        }

        public Collection<File> getFiles() {
            return filesSnapshot.getFiles();
        }

        @Override
        public Map<String, IncrementalFileSnapshot> getSnapshots() {
            return filesSnapshot.getSnapshots();
        }

        public FilesSnapshotSet getSnapshot() {
            return filesSnapshot.getSnapshot();
        }

        @Override
        public boolean isEmpty() {
            return filesSnapshot.isEmpty();
        }

        @Override
        public ChangeIterator<String> iterateContentChangesSince(FileCollectionSnapshot oldSnapshot, Set<ChangeFilter> filters) {
            final OutputFilesSnapshot other = (OutputFilesSnapshot) oldSnapshot;
            final ChangeIterator<String> rootFileIdIterator = iterateRootFileIdChanges(other);
            final ChangeIterator<String> fileIterator = filesSnapshot.iterateContentChangesSince(other.filesSnapshot, filters);

            return new ChangeIterator<String>() {
                public boolean next(final ChangeListener<String> listener) {
                    if (rootFileIdIterator.next(listener)) {
                        return true;
                    }
                    if (fileIterator.next(listener)) {
                        return true;
                    }
                    return false;
                }
            };
        }

        private ChangeIterator<String> iterateRootFileIdChanges(final OutputFilesSnapshot other) {
            Set<String> added = new LinkedHashSet<String>(roots);
            added.removeAll(other.roots);
            final Iterator<String> addedIterator = added.iterator();

            Set<String> removed = new LinkedHashSet<String>(other.roots);
            removed.removeAll(roots);
            final Iterator<String> removedIterator = removed.iterator();

            return new ChangeIterator<String>() {
                public boolean next(ChangeListener<String> listener) {
                    if (addedIterator.hasNext()) {
                        listener.added(addedIterator.next());
                        return true;
                    }
                    if (removedIterator.hasNext()) {
                        listener.removed(removedIterator.next());
                        return true;
                    }

                    return false;
                }
            };
        }
    }
}
