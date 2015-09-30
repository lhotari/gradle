/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.util.ChangeListener;

import java.util.Iterator;
import java.util.Map;

class SnapshotChangeIterator implements FileCollectionSnapshot.ChangeIterator<Map.Entry<String, DefaultFileCollectionSnapshotter.IncrementalFileSnapshot>> {
    private final Iterator<Map.Entry<String, DefaultFileCollectionSnapshotter.IncrementalFileSnapshot>> thisIterator;
    private final Iterator<Map.Entry<String, DefaultFileCollectionSnapshotter.IncrementalFileSnapshot>> otherIterator;
    private Map.Entry<String, DefaultFileCollectionSnapshotter.IncrementalFileSnapshot> currentThis;
    private Map.Entry<String, DefaultFileCollectionSnapshotter.IncrementalFileSnapshot> currentOther;
    private boolean currentThisUsed = true;
    private boolean currentOtherUsed = true;

    public SnapshotChangeIterator(FileCollectionSnapshot snapshot, FileCollectionSnapshot oldSnapshot) {
        this.thisIterator = ((DefaultFileCollectionSnapshotter.FileCollectionSnapshotImpl) snapshot).snapshotMap.entrySet().iterator();
        this.otherIterator = ((DefaultFileCollectionSnapshotter.FileCollectionSnapshotImpl) oldSnapshot).snapshotMap.entrySet().iterator();
    }

    private boolean pickNext() {
        if (currentThisUsed && thisIterator.hasNext()) {
            currentThis = thisIterator.next();
            currentThisUsed = false;
        }
        if (currentOtherUsed && otherIterator.hasNext()) {
            currentOther = otherIterator.next();
            currentOtherUsed = false;
        }
        return !currentThisUsed || !currentOtherUsed;
    }

    public FileCollectionSnapshot.ChangeIterator<String> adaptToFilenameChangeIterator() {
        return new FileCollectionSnapshot.ChangeIterator<String>() {
            ChangeIteratorAdapter adapter = new ChangeIteratorAdapter();

            @Override
            public boolean next(final ChangeListener<String> listener) {
                adapter.listener = listener;
                return nextEntry(adapter);
            }
        };
    }

    public boolean next(ChangeListener<Map.Entry<String, DefaultFileCollectionSnapshotter.IncrementalFileSnapshot>> listener) {
        return nextEntry(listener);
    }

    boolean nextEntry(ChangeListener<Map.Entry<String, DefaultFileCollectionSnapshotter.IncrementalFileSnapshot>> listener) {
        while (pickNext()) {
            boolean listenerCalled = false;
            if (currentOtherUsed || currentThisUsed) {
                if (currentThisUsed) {
                    listener.added(currentOther);
                    listenerCalled = true;
                    currentOtherUsed = true;
                } else {
                    listener.removed(currentThis);
                    listenerCalled = true;
                    currentThisUsed = true;
                }
            } else {
                int compareResult = currentThis.getKey().compareTo(currentOther.getKey());
                if (compareResult > 0) {
                    listener.added(currentOther);
                    listenerCalled = true;
                    currentOtherUsed = true;
                } else if (compareResult < 0) {
                    listener.removed(currentThis);
                    listenerCalled = true;
                    currentThisUsed = true;
                } else {
                    if (!currentThis.getValue().isUpToDate(currentOther.getValue())) {
                        listener.changed(currentThis);
                        listenerCalled = true;
                    }
                    currentThisUsed = true;
                    currentOtherUsed = true;
                }
            }
            if (listenerCalled) {
                return true;
            }
        }
        return false;
    }

    private static class ChangeIteratorAdapter implements ChangeListener<Map.Entry<String, DefaultFileCollectionSnapshotter.IncrementalFileSnapshot>> {
        private ChangeListener<String> listener;

        @Override
        public void added(Map.Entry<String, DefaultFileCollectionSnapshotter.IncrementalFileSnapshot> element) {
            listener.added(element.getKey());
        }

        @Override
        public void removed(Map.Entry<String, DefaultFileCollectionSnapshotter.IncrementalFileSnapshot> element) {
            listener.removed(element.getKey());
        }

        @Override
        public void changed(Map.Entry<String, DefaultFileCollectionSnapshotter.IncrementalFileSnapshot> element) {
            listener.changed(element.getKey());
        }
    }
}
