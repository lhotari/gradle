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
import org.gradle.api.Action;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.util.Collection;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

// Visits a FileTreeInternal for snapshotting, caches some directory scans
public class CachingTreeVisitor {
    private final static Logger LOG = Logging.getLogger(CachingTreeVisitor.class);
    private ConcurrentMap<String, VisitedTree> cachedTrees = new MapMaker().weakValues().makeMap();
    private AtomicLong nextId = new AtomicLong(System.currentTimeMillis());

    public interface VisitedTree {
        Collection<FileTreeElement> getEntries();

        boolean isShareable();

        Long getAssignedId();

        Long maybeStoreEntry(Action<Long> storeEntryAction);
    }

    public VisitedTree visitTreeForSnapshotting(FileTreeInternal fileTree, boolean allowReuse) {
        if (isDirectoryFileTree(fileTree)) {
            DirectoryFileTree directoryFileTree = DirectoryFileTree.class.cast(((FileTreeAdapter) fileTree).getTree());
            if (isEligibleForCaching(directoryFileTree)) {
                final String absolutePath = directoryFileTree.getDir().getAbsolutePath();
                VisitedTree cachedTree = allowReuse ? cachedTrees.get(absolutePath) : null;
                if (cachedTree != null) {
                    recordCacheHit(directoryFileTree);
                    return cachedTree;
                } else {
                    recordCacheMiss(directoryFileTree, allowReuse);
                    cachedTree = doVisitTree(fileTree, true);
                    cachedTrees.put(absolutePath, cachedTree);
                    return cachedTree;
                }
            }
        }
        return doVisitTree(fileTree, false);
    }

    protected void recordCacheHit(DirectoryFileTree directoryFileTree) {
        // method added also for interception with bytebuddy in integtest
        LOG.debug("Cache hit {}", directoryFileTree);
    }

    protected void recordCacheMiss(DirectoryFileTree directoryFileTree, boolean allowReuse) {
        // method added also for interception with bytebuddy in integtest
        if (allowReuse) {
            LOG.debug("Cache miss {}", directoryFileTree);
        } else {
            LOG.debug("Visiting {}", directoryFileTree);
        }
    }

    private boolean isEligibleForCaching(DirectoryFileTree directoryFileTree) {
        return directoryFileTree.getPatterns().isEmpty();
    }

    private boolean isDirectoryFileTree(FileTreeInternal fileTree) {
        return fileTree instanceof FileTreeAdapter && ((FileTreeAdapter) fileTree).getTree() instanceof DirectoryFileTree;
    }

    private VisitedTree doVisitTree(FileTreeInternal fileTree, boolean shareable) {
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
        return new DefaultVisitedTree(fileTreeElements.build(), shareable, nextId);
    }

    public void clearCache() {
        cachedTrees.clear();
    }

    private static class DefaultVisitedTree implements VisitedTree {
        private final ImmutableList<FileTreeElement> entries;
        private final boolean shareable;
        private final AtomicLong nextId;
        private Long assignedId;

        public DefaultVisitedTree(ImmutableList<FileTreeElement> entries, boolean shareable, AtomicLong nextId) {
            this.entries = entries;
            this.shareable = shareable;
            this.nextId = nextId;
        }

        @Override
        public Collection<FileTreeElement> getEntries() {
            return entries;
        }

        @Override
        public boolean isShareable() {
            return shareable;
        }

        @Override
        public Long getAssignedId() {
            return assignedId;
        }

        @Override
        public synchronized Long maybeStoreEntry(Action<Long> storeEntryAction) {
            if (assignedId == null) {
                assignedId = nextId.incrementAndGet();
                storeEntryAction.execute(assignedId);
            }
            return assignedId;
        }
    }
}
