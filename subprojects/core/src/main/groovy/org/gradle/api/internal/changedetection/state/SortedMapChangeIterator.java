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
import java.util.SortedMap;

class SortedMapChangeIterator<T> implements FileCollectionSnapshot.ChangeIterator<Map.Entry<String, T>> {
    private final Iterator<Map.Entry<String, T>> thisIterator;
    private final Iterator<Map.Entry<String, T>> otherIterator;
    private Map.Entry<String, T> currentThis;
    private Map.Entry<String, T> currentOther;
    private boolean currentThisUsed = true;
    private boolean currentOtherUsed = true;

    public SortedMapChangeIterator(SortedMap<String, T> snapshot, SortedMap<String, T> oldSnapshot) {
        this.thisIterator = snapshot.entrySet().iterator();
        this.otherIterator = oldSnapshot.entrySet().iterator();
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
            ChangeIteratorAdapter<T> adapter = new ChangeIteratorAdapter<T>();

            @Override
            public boolean next(final ChangeListener<String> listener) {
                adapter.listener = listener;
                return nextEntry(adapter);
            }
        };
    }

    public boolean next(ChangeListener<Map.Entry<String, T>> listener) {
        return nextEntry(listener);
    }

    boolean nextEntry(ChangeListener<Map.Entry<String, T>> listener) {
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
                    if (!compareValues(currentThis.getValue(), currentOther.getValue())) {
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

    protected boolean compareValues(T a, T b) {
        return a.equals(b);
    }

    private static class ChangeIteratorAdapter<T> implements ChangeListener<Map.Entry<String, T>> {
        private ChangeListener<String> listener;

        @Override
        public void added(Map.Entry<String, T> element) {
            listener.added(element.getKey());
        }

        @Override
        public void removed(Map.Entry<String, T> element) {
            listener.removed(element.getKey());
        }

        @Override
        public void changed(Map.Entry<String, T> element) {
            listener.changed(element.getKey());
        }
    }
}
