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
package org.gradle.api.internal.file;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.util.GUtil;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class UnionFileCollection extends CompositeFileCollection {
    private final Set<FileCollection> source;
    private final Factory<PatternSet> patternSetFactory;

    public UnionFileCollection(Factory<PatternSet> patternSetFactory, FileCollection... source) {
        this(patternSetFactory, Arrays.asList(source));
    }

    public UnionFileCollection(Factory<PatternSet> patternSetFactory, Iterable<? extends FileCollection> source) {
        this.source = GUtil.addToCollection(new LinkedHashSet<FileCollection>(), source);
        this.patternSetFactory = patternSetFactory;
    }

    public String getDisplayName() {
        return "file collection";
    }

    public Set<FileCollection> getSources() {
        return source;
    }

    @Override
    public FileCollection add(FileCollection collection) {
        source.add(collection);
        return this;
    }

    @Override
    public void visitContents(FileCollectionResolveContext context) {
        context.add(source);
    }

    @Override
    protected Factory<PatternSet> getPatternSetFactory() {
        return patternSetFactory;
    }
}
