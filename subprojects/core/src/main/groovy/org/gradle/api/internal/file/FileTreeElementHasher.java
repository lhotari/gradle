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

package org.gradle.api.internal.file;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.gradle.api.file.FileTreeElement;

import java.util.*;

public class FileTreeElementHasher {
    private static final byte HASH_PATH_SEPARATOR = (byte) '/';
    private static final byte HASH_FIELD_SEPARATOR = (byte) '\t';
    private static final byte HASH_RECORD_SEPARATOR = (byte) '\n';

    public static final int calculateHashForFileMetadata(Collection<? extends FileTreeElement> allFileTreeElements) {
        Collection<FileTreeElement> sortedFileTreeElement = sortForHashing(allFileTreeElements);

        Hasher hasher = createHasher();
        for (FileTreeElement fileTreeElement : sortedFileTreeElement) {
            for (String pathPart : fileTreeElement.getRelativePath().getSegments()) {
                hasher.putUnencodedChars(pathPart);
                hasher.putByte(HASH_PATH_SEPARATOR);
            }
            if (!fileTreeElement.isDirectory()) {
                hasher.putByte(HASH_FIELD_SEPARATOR);
                hasher.putLong(fileTreeElement.getSize());
                hasher.putByte(HASH_FIELD_SEPARATOR);
                hasher.putLong(fileTreeElement.getLastModified());
            }
            hasher.putByte(HASH_RECORD_SEPARATOR);
        }
        return hasher.hash().asInt();
    }

    private static Hasher createHasher() {
        return Hashing.murmur3_32().newHasher();
    }

    public static final int calculateHashForFilePaths(Collection<? extends FileTreeElement> allFileTreeElements) {
        Collection<FileTreeElement> sortedFileTreeElement = sortForHashing(allFileTreeElements);

        Hasher hasher = createHasher();
        for (FileTreeElement fileTreeElement : sortedFileTreeElement) {
            for (String pathPart : fileTreeElement.getRelativePath().getSegments()) {
                hasher.putUnencodedChars(pathPart);
                hasher.putByte(HASH_PATH_SEPARATOR);
            }
            hasher.putByte(HASH_RECORD_SEPARATOR);
        }
        return hasher.hash().asInt();
    }

    private static Collection<FileTreeElement> sortForHashing(Collection<? extends FileTreeElement> allFileTreeElements) {
        List<FileTreeElement> sortedFileTreeElement = new ArrayList<FileTreeElement>(allFileTreeElements);
        Collections.sort(sortedFileTreeElement, FastFileTreeElementComparator.INSTANCE);
        return sortedFileTreeElement;
    }

    // Comparator that is fast and produces stable order for FileTreeElements for hashing
    private static class FastFileTreeElementComparator implements Comparator<FileTreeElement> {
        public static final FastFileTreeElementComparator INSTANCE = new FastFileTreeElementComparator();

        private FastFileTreeElementComparator() {
        }

        @Override
        public int compare(FileTreeElement o1, FileTreeElement o2) {
            int compareResult = (o1.isDirectory() == o2.isDirectory()) ? 0 : (o1.isDirectory() ? 1 : -1);
            if (compareResult != 0) {
                return compareResult;
            }
            if (!o1.isDirectory() && !o2.isDirectory()) {
                compareResult = (o1.getLastModified() < o2.getLastModified()) ? -1 : ((o1.getLastModified() == o2.getLastModified()) ? 0 : 1);
                if (compareResult != 0) {
                    return compareResult;
                }
                compareResult = (o1.getSize() < o2.getSize()) ? -1 : ((o1.getSize() == o2.getSize()) ? 0 : 1);
                if (compareResult != 0) {
                    return compareResult;
                }
            }
            compareResult = o1.getRelativePath().getLastName().compareTo(o2.getRelativePath().getLastName());
            if (compareResult != 0) {
                return compareResult;
            }
            return o1.getRelativePath().compareTo(o2.getRelativePath());
        }
    }
}
