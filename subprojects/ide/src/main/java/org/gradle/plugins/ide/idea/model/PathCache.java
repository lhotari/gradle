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

package org.gradle.plugins.ide.idea.model;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.gradle.internal.Pair;
import org.gradle.internal.UncheckedException;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Caches and de-duplicates FilePath and Path instances referenced in the built IdeaModel and the IdeaProjects contained in it
 */
public class PathCache {
    final Cache<Pair<File, Boolean>, FilePath> fileToFilePathCache;
    final Cache<Pair<Pair<File, String>, File>, FilePath> relativeFilePathCache;
    final Cache<Pair<String, String>, Path> relativePathCache;

    public PathCache() {
        this(20000);
    }

    public PathCache(int cacheMaxSize) {
        fileToFilePathCache = CacheBuilder.newBuilder().maximumSize(cacheMaxSize).build();
        relativeFilePathCache = CacheBuilder.newBuilder().maximumSize(cacheMaxSize).build();
        relativePathCache = CacheBuilder.newBuilder().maximumSize(cacheMaxSize).build();
    }

    FilePath createFilePath(final File file, boolean useFileScheme, final String relPath, final String url) {
        try {
            return fileToFilePathCache.get(Pair.of(file, useFileScheme), new Callable<FilePath>() {
                @Override
                public FilePath call() throws Exception {
                    return new FilePath(file, url, url, relPath);
                }
            });
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    FilePath createRelativeFilePath(File rootDir, String rootDirName, final File file, final String relPath, final String url, final String canonicalUrl) {
        try {
            return relativeFilePathCache.get(Pair.of(Pair.of(rootDir, rootDirName), file), new Callable<FilePath>() {
                @Override
                public FilePath call() throws Exception {
                    return new FilePath(file, url, canonicalUrl, relPath);
                }
            });
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    Path createPath(final String url, final String relPath, final String expandedUrlResult) {
        try {
            return relativePathCache.get(Pair.of(url, relPath), new Callable<Path>() {
                @Override
                public Path call() throws Exception {
                    return new Path(url, expandedUrlResult, relPath);
                }
            });
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}