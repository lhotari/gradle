/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.internal;

import com.google.common.collect.Lists;
import org.gradle.api.GradleException;
import org.gradle.api.Transformer;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.*;

public class FileUtils {
    public static final int WINDOWS_PATH_LIMIT = 260;

    /**
     * Converts a string into a string that is safe to use as a file name. The result will only include ascii
     * characters and numbers, and the "-","_", #, $ and "." characters.
     */
    public static String toSafeFileName(String name) {
        int size = name.length();
        StringBuffer rc = new StringBuffer(size * 2);
        for (int i = 0; i < size; i++) {
            char c = name.charAt(i);
            boolean valid = c >= 'a' && c <= 'z';
            valid = valid || (c >= 'A' && c <= 'Z');
            valid = valid || (c >= '0' && c <= '9');
            valid = valid || (c == '_') || (c == '-') || (c == '.') || (c == '$');
            if (valid) {
                rc.append(c);
            } else {
                // Encode the character using hex notation
                rc.append('#');
                rc.append(Integer.toHexString(c));
            }
        }
        return rc.toString();
    }

    public static File assertInWindowsPathLengthLimitation(File file){
        if(file.getAbsolutePath().length() > WINDOWS_PATH_LIMIT){
            throw new GradleException(String.format("Cannot create file. '%s' exceeds windows path limitation of %d character.", file.getAbsolutePath(), WINDOWS_PATH_LIMIT));

        }
        return file;
    }

    public static Collection<? extends File> findRoots(Iterable<? extends File> files) {
        List<File> roots = Lists.newLinkedList();

        Set<File> directories = CollectionUtils.collect(files, new LinkedHashSet<File>(), new Transformer<File, File>() {
                @Override
                public File transform(File file) {
                    return file.isDirectory() ? file.getAbsoluteFile() : file.getAbsoluteFile().getParentFile();
                }
            });

        files:
        for (File dir : directories) {
            String path = dir.getPath() + File.separator;
            Iterator<File> rootsIterator = roots.iterator();

            while (rootsIterator.hasNext()) {
                File root = rootsIterator.next();
                String rootPath = root.getPath() + File.separator;
                if (path.startsWith(rootPath)) { // is lower than root
                    continue files;
                }

                if (rootPath.startsWith(path)) { // is higher than root
                    rootsIterator.remove();
                }
            }

            roots.add(dir);
        }

        return roots;
    }

}
