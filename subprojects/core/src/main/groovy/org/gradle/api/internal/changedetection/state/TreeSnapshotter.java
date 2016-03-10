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
import com.google.common.collect.MapMaker;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.internal.file.collections.MinimalFileTree;
import org.gradle.api.internal.file.collections.SingletonFileTree;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.ConcurrentMap;

// Visits a FileTreeInternal for snapshotting, caches some directory scans
public class TreeSnapshotter {
    private final File fileStoreDirectory;
    private ConcurrentMap<String, Collection<FileTreeElement>> cachedFileStoreFiles = new MapMaker().weakValues().makeMap();
    private ConcurrentMap<String, Collection<FileTreeElement>> cachedTrees = new MapMaker().weakValues().makeMap();

    public TreeSnapshotter(File fileStoreDirectory) {
        this.fileStoreDirectory = fileStoreDirectory;
    }

    public Collection<FileTreeElement> visitTreeForSnapshotting(FileTreeInternal fileTree, boolean allowReuse) {
        if (fileTree instanceof FileTreeAdapter) {
            MinimalFileTree minimalFileTree = ((FileTreeAdapter) fileTree).getTree();
            if (minimalFileTree instanceof DirectoryFileTree) {
                DirectoryFileTree directoryFileTree = DirectoryFileTree.class.cast(minimalFileTree);
                if (isEligibleForCaching(directoryFileTree)) {
                    final String absolutePath = directoryFileTree.getDir().getAbsolutePath();
                    return handleVisitingAndCaching(absolutePath, fileTree, allowReuse, cachedTrees);
                }
            } else if (minimalFileTree instanceof SingletonFileTree) {
                File file = ((SingletonFileTree) minimalFileTree).getFile().getAbsoluteFile();
                if (isEligibleForCaching(file)) {
                    return handleVisitingAndCaching(file.getAbsolutePath(), fileTree, allowReuse, cachedFileStoreFiles);
                }
            }
        }
        return doVisitTree(fileTree);
    }

    private Collection<FileTreeElement> handleVisitingAndCaching(String absolutePath, FileTreeInternal fileTree, boolean allowReuse, ConcurrentMap<String, Collection<FileTreeElement>> cache) {
        Collection<FileTreeElement> cachedTree = allowReuse ? cache.get(absolutePath) : null;
        if (cachedTree != null) {
            return cachedTree;
        } else {
            cachedTree = doVisitTree(fileTree);
            cache.put(absolutePath, cachedTree);
            return cachedTree;
        }
    }

    private boolean isEligibleForCaching(File file) {
        return isInFileStoreDirectory(file);
    }

    private boolean isInFileStoreDirectory(File file) {
        File currentFile = file.getParentFile();
        while (currentFile != null) {
            if (currentFile.equals(fileStoreDirectory)) {
                return true;
            }
            currentFile = currentFile.getParentFile();
        }
        return false;
    }

    private boolean isEligibleForCaching(DirectoryFileTree directoryFileTree) {
        return directoryFileTree.getPatterns().isEmpty();
    }

    private Collection<FileTreeElement> doVisitTree(FileTreeInternal fileTree) {
        final ImmutableList.Builder<FileTreeElement> fileTreeElements = ImmutableList.builder();
        fileTree.visitTreeOrBackingFile(new FileVisitor() {
            @Override
            public void visitDir(FileVisitDetails dirDetails) {
                fileTreeElements.add(dirDetails);
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                fileTreeElements.add(fileDetails);
            }
        });
        return fileTreeElements.build();
    }

    public void clearCache() {
        cachedTrees.clear();
    }
}
