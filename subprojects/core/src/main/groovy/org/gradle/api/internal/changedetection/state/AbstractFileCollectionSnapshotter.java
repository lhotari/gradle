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

import com.google.common.collect.Lists;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.FileTreeElementHasher;
import org.gradle.internal.serialize.SerializerRegistry;

import java.io.File;
import java.util.*;

abstract class AbstractFileCollectionSnapshotter implements FileCollectionSnapshotter {
    protected final FileSnapshotter snapshotter;
    protected final StringInterner stringInterner;
    protected final FileResolver fileResolver;
    protected TaskArtifactStateCacheAccess cacheAccess;

    public AbstractFileCollectionSnapshotter(FileSnapshotter snapshotter, TaskArtifactStateCacheAccess cacheAccess, StringInterner stringInterner, FileResolver fileResolver) {
        this.snapshotter = snapshotter;
        this.cacheAccess = cacheAccess;
        this.stringInterner = stringInterner;
        this.fileResolver = fileResolver;
    }

    public void registerSerializers(SerializerRegistry registry) {
        registry.register(FileCollectionSnapshotImpl.class, new DefaultFileSnapshotterSerializer(stringInterner));
    }

    public FileCollectionSnapshot emptySnapshot() {
        return new FileCollectionSnapshotImpl(Collections.<String, IncrementalFileSnapshot>emptyMap());
    }

    public FileCollectionSnapshot.PreCheck preCheck(final FileCollection files, final boolean allowReuse) {
        return new DefaultFileCollectionSnapshotPreCheck(files, allowReuse);
    }

    private Integer calculatePreCheckHash(Collection<CachingTreeVisitor.VisitedTree> visitedTrees) {
        Collection<FileTreeElement> fileTreeElements = new ArrayList<FileTreeElement>();
        for (CachingTreeVisitor.VisitedTree tree : visitedTrees) {
            fileTreeElements.addAll(tree.getEntries());
        }
        return FileTreeElementHasher.calculateHashForFileMetadata(fileTreeElements);
    }


    public FileCollectionSnapshot snapshot(final FileCollectionSnapshot.PreCheck preCheck) {
        if (preCheck.isEmpty()) {
            return emptySnapshot();
        }

        final Map<String, IncrementalFileSnapshot> snapshots = new HashMap<String, IncrementalFileSnapshot>();

        cacheAccess.useCache("Create file snapshot", new Runnable() {
            public void run() {
                for (CachingTreeVisitor.VisitedTree tree : preCheck.getVisitedTrees()) {
                    for (FileTreeElement fileDetails : tree.getEntries()) {
                        String absolutePath = getInternedAbsolutePath(fileDetails.getFile());
                        if (!snapshots.containsKey(absolutePath)) {
                            if (fileDetails.isDirectory()) {
                                snapshots.put(absolutePath, DirSnapshot.getInstance());
                            } else {
                                snapshots.put(absolutePath, new FileHashSnapshot(snapshotter.snapshot(fileDetails).getHash(), fileDetails.getLastModified()));
                            }
                        }
                    }
                }
                for (File missingFile : preCheck.getMissingFiles()) {
                    String absolutePath = getInternedAbsolutePath(missingFile);
                    if (!snapshots.containsKey(absolutePath)) {
                        snapshots.put(absolutePath, MissingFileSnapshot.getInstance());
                    }
                }
            }
        });

        return new FileCollectionSnapshotImpl(snapshots);
    }

    private String getInternedAbsolutePath(File file) {
        return stringInterner.intern(file.getAbsolutePath());
    }

    abstract protected void visitFiles(FileCollection input, List<CachingTreeVisitor.VisitedTree> visitedTrees, List<File> missingFiles, boolean allowReuse);

    private final class DefaultFileCollectionSnapshotPreCheck implements FileCollectionSnapshot.PreCheck {
        private final List<CachingTreeVisitor.VisitedTree> visitedTrees;
        private final List<File> missingFiles;
        private final FileCollection files;
        private Integer hash;

        public DefaultFileCollectionSnapshotPreCheck(FileCollection files, boolean allowReuse) {
            this.files = files;
            visitedTrees = Lists.newLinkedList();
            missingFiles = Lists.newArrayList();
            visitFiles(files, visitedTrees, missingFiles, allowReuse);
        }

        @Override
        public Integer getHash() {
            if (hash == null) {
                hash = calculatePreCheckHash(visitedTrees);
            }
            return hash;
        }

        @Override
        public Collection<CachingTreeVisitor.VisitedTree> getVisitedTrees() {
            return visitedTrees;
        }

        @Override
        public Collection<File> getMissingFiles() {
            return missingFiles;
        }

        @Override
        public boolean isEmpty() {
            for (CachingTreeVisitor.VisitedTree tree : visitedTrees) {
                if (!tree.getEntries().isEmpty()) {
                    return false;
                }
            }
            return missingFiles.isEmpty();
        }
    }
}
