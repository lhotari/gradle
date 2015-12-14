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

import com.google.common.collect.Sets;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

public class UnionFileTree extends CompositeFileTree {
    private final Set<FileTreeInternal> sourceTrees;
    private final String displayName;
    private final Factory<PatternSet> patternSetFactory;

    public UnionFileTree(Factory<PatternSet> patternSetFactory, FileTreeInternal... sourceTrees) {
        this("file tree", patternSetFactory, Arrays.asList(sourceTrees));
    }

    public UnionFileTree(String displayName, Factory<PatternSet> patternSetFactory, FileTreeInternal... sourceTrees) {
        this(displayName, patternSetFactory, Arrays.asList(sourceTrees));
    }

    public UnionFileTree(String displayName, Factory<PatternSet> patternSetFactory, Collection<? extends FileTreeInternal> sourceTrees) {
        this.displayName = displayName;
        this.sourceTrees = Sets.newLinkedHashSet(sourceTrees);
        this.patternSetFactory = patternSetFactory;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public void visitContents(FileCollectionResolveContext context) {
        context.add(sourceTrees);
    }

    @Override
    public UnionFileTree add(FileCollection source) {
        if (!(source instanceof FileTree)) {
            throw new UnsupportedOperationException(String.format("Can only add FileTree instances to %s.", getDisplayName()));
        }

        sourceTrees.add(Cast.cast(FileTreeInternal.class, source));
        return this;
    }

    @Override
    protected Factory<PatternSet> getPatternSetFactory() {
        return patternSetFactory;
    }
}
