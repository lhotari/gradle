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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.gradle.api.Transformer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.CachingFileVisitDetails;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.*;
import org.gradle.internal.Pair;
import org.gradle.internal.serialize.SerializerRegistry;
import org.gradle.util.ChangeListener;
import org.gradle.util.CollectionUtils;
import org.gradle.util.NoOpChangeListener;

import java.io.File;
import java.math.BigInteger;
import java.util.*;

public class DefaultFileCollectionSnapshotter implements FileCollectionSnapshotter {
    private final FileTreeElementSnapshotter snapshotter;
    private TaskArtifactStateCacheAccess cacheAccess;
    private final StringInterner stringInterner;

    public DefaultFileCollectionSnapshotter(FileTreeElementSnapshotter snapshotter, TaskArtifactStateCacheAccess cacheAccess, StringInterner stringInterner) {
        this.snapshotter = snapshotter;
        this.cacheAccess = cacheAccess;
        this.stringInterner = stringInterner;
    }

    public void registerSerializers(SerializerRegistry<FileCollectionSnapshot> registry) {
        registry.register(FileCollectionSnapshotImpl.class, new DefaultFileSnapshotterSerializer(stringInterner));
    }

    public FileCollectionSnapshot emptySnapshot() {
        return new FileCollectionSnapshotImpl(new HashMap<String, IncrementalFileSnapshot>());
    }

    public FileCollectionSnapshot snapshot(final FileCollection input) {
        final Pair<Iterable<FileVisitDetails>, Iterable<FileVisitDetails>> allFileVisitDetails = visitFiles(input);
        final Map<String, IncrementalFileSnapshot> snapshots = new HashMap<String, IncrementalFileSnapshot>();

        for (Pair<String, FileVisitDetails> pathAndDir : CollectionUtils.collect(allFileVisitDetails.left(), internedPathAndDetailsPairTransformer)) {
            snapshots.put(pathAndDir.left(), new DirSnapshot());
        }

        final List<Pair<String, FileVisitDetails>> fileHashingWorkList = CollectionUtils.collect(allFileVisitDetails.right(), internedPathAndDetailsPairTransformer);

        cacheAccess.useCache("Create file snapshot", new Runnable() {
            public void run() {
                for (Pair<String, FileVisitDetails> workItem : fileHashingWorkList) {
                    snapshots.put(workItem.left(), new FileHashSnapshot(snapshotter.snapshot(workItem.right()).getHash()));
                }
            }
        });

        return new FileCollectionSnapshotImpl(snapshots);
    }

    final Transformer<Pair<String, FileVisitDetails>, FileVisitDetails> internedPathAndDetailsPairTransformer = new Transformer<Pair<String, FileVisitDetails>, FileVisitDetails>() {
        @Override
        public Pair<String, FileVisitDetails> transform(FileVisitDetails fileVisitDetails) {
            String absolutePath = stringInterner.intern(fileVisitDetails.getFile().getAbsolutePath());
            return Pair.of(absolutePath, fileVisitDetails);
        }
    };

    private Pair<Iterable<FileVisitDetails>, Iterable<FileVisitDetails>> visitFiles(FileCollection input) {
        final List<FileVisitDetails> dirVisitDetails = new LinkedList<FileVisitDetails>();
        final List<FileVisitDetails> fileVisitDetails = new LinkedList<FileVisitDetails>();

        DefaultFileCollectionResolveContext context = new DefaultFileCollectionResolveContext();
        context.add(input);
        List<FileTreeInternal> fileTrees = context.resolveAsFileTrees();

        for (FileTreeInternal fileTree : fileTrees) {
            Set<File> fileTreeSourceFiles = unwrapFileTreeSourceFilesIfAvailable(fileTree);
            if (fileTreeSourceFiles != null) {
                for (File fileTreeSourceFile : fileTreeSourceFiles) {
                    fileVisitDetails.add(new CachingFileVisitDetails(fileTreeSourceFile));
                }
            } else {
                fileTree.visit(new FileVisitor() {
                    @Override
                    public void visitDir(FileVisitDetails dirDetails) {
                        dirVisitDetails.add(dirDetails);
                    }

                    @Override
                    public void visitFile(FileVisitDetails fileDetails) {
                        fileVisitDetails.add(fileDetails);
                    }
                });
            }
        }

        return Pair.of(Iterables.filter(dirVisitDetails, new UniqueFilePredicate()), Iterables.filter(fileVisitDetails, new UniqueFilePredicate()));
    }

    private static class UniqueFilePredicate implements Predicate<FileVisitDetails> {
        private final Set<String> handledFiles = new HashSet<String>();

        @Override
        public boolean apply(FileVisitDetails input) {
            final String absolutePath = input.getFile().getAbsolutePath();
            if (!handledFiles.contains(absolutePath)) {
                handledFiles.add(absolutePath);
                return true;
            } else {
                return false;
            }
        }
    }

    private Set<File> unwrapFileTreeSourceFilesIfAvailable(Object fileTree) {
        if (fileTree instanceof FileTreeWithSourceFile) {
            File sourceFile = ((FileTreeWithSourceFile) fileTree).getSourceFile();
            if (sourceFile == null && fileTree instanceof FileSystemMirroringFileTree) {
                // custom resource as source for TarFileTree, fallback to snapshotting files in archive
                return new FileTreeAdapter((FileSystemMirroringFileTree) fileTree).getFiles();
            }
            return Collections.singleton(sourceFile);
        } else if (fileTree instanceof FileTreeAdapter) {
            return unwrapFileTreeSourceFilesIfAvailable(((FileTreeAdapter) fileTree).getTree());
        } else if (fileTree instanceof FilteredFileTree) {
            return unwrapFileTreeSourceFilesIfAvailable(((FilteredFileTree) fileTree).getOriginalFileTree());
        }
        return null;
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
        final Map<String, IncrementalFileSnapshot> snapshots;

        public FileCollectionSnapshotImpl(Map<String, IncrementalFileSnapshot> snapshots) {
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
            FileCollectionSnapshotImpl other = (FileCollectionSnapshotImpl) oldSnapshot;
            final Map<String, IncrementalFileSnapshot> otherSnapshots = new HashMap<String, IncrementalFileSnapshot>(other.snapshots);
            final Iterator<String> currentFiles = snapshots.keySet().iterator();

            return new ChangeIterator<String>() {
                private Iterator<String> removedFiles;

                public boolean next(ChangeListener<String> listener) {
                    while (currentFiles.hasNext()) {
                        String currentFile = currentFiles.next();
                        IncrementalFileSnapshot otherFile = otherSnapshots.remove(currentFile);

                        if (otherFile == null) {
                            listener.added(currentFile);
                            return true;
                        } else if (!snapshots.get(currentFile).isUpToDate(otherFile)) {
                            listener.changed(currentFile);
                            return true;
                        }
                    }

                    // Create a single iterator to use for all of the removed files
                    if (removedFiles == null) {
                        removedFiles = otherSnapshots.keySet().iterator();
                    }

                    if (removedFiles.hasNext()) {
                        listener.removed(removedFiles.next());
                        return true;
                    }

                    return false;
                }
            };
        }

        public Diff changesSince(final FileCollectionSnapshot oldSnapshot) {
            final FileCollectionSnapshotImpl other = (FileCollectionSnapshotImpl) oldSnapshot;
            return new Diff() {
                public FileCollectionSnapshot applyTo(FileCollectionSnapshot snapshot) {
                    return applyTo(snapshot, new NoOpChangeListener<Merge>());
                }

                public FileCollectionSnapshot applyTo(FileCollectionSnapshot snapshot, final ChangeListener<Merge> listener) {
                    FileCollectionSnapshotImpl target = (FileCollectionSnapshotImpl) snapshot;
                    final Map<String, IncrementalFileSnapshot> newSnapshots = new HashMap<String, IncrementalFileSnapshot>(target.snapshots);
                    diff(snapshots, other.snapshots, new MapMergeChangeListener<String, IncrementalFileSnapshot>(listener, newSnapshots));
                    return new FileCollectionSnapshotImpl(newSnapshots);
                }
            };
        }

        private void diff(Map<String, IncrementalFileSnapshot> snapshots, Map<String, IncrementalFileSnapshot> oldSnapshots,
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
}
