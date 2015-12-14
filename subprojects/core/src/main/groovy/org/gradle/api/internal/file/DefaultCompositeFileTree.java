/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;

import java.util.Collection;

public class DefaultCompositeFileTree extends CompositeFileTree {
    private final Collection<? extends FileTreeInternal> fileTrees;
    private final Factory<PatternSet> patternSetFactory;

    public DefaultCompositeFileTree(Collection<? extends FileTreeInternal> fileTrees, Factory<PatternSet> patternSetFactory) {
        this.fileTrees = fileTrees;
        this.patternSetFactory = patternSetFactory;
    }

    @Override
    public void visitContents(FileCollectionResolveContext context) {
        context.add(fileTrees);
    }

    @Override
    public String getDisplayName() {
        return "file tree";
    }

    @Override
    protected Factory<PatternSet> getPatternSetFactory() {
        return patternSetFactory;
    }
}
