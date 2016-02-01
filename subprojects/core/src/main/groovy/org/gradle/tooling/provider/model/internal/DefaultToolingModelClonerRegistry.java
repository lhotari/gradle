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

package org.gradle.tooling.provider.model.internal;

import org.gradle.tooling.provider.model.ToolingModelCloner;
import org.gradle.tooling.provider.model.ToolingModelClonerRegistry;
import org.gradle.tooling.provider.model.UnknownModelException;

import java.util.ArrayList;
import java.util.List;

public class DefaultToolingModelClonerRegistry implements ToolingModelClonerRegistry {
    private final List<ToolingModelCloner> cloners = new ArrayList<ToolingModelCloner>();

    public DefaultToolingModelClonerRegistry() {

    }

    public void register(ToolingModelCloner builder) {
        cloners.add(builder);
    }

    public ToolingModelCloner getCloner(String modelName) throws UnsupportedOperationException {
        ToolingModelCloner match = null;
        for (ToolingModelCloner cloner : cloners) {
            if (cloner.canClone(modelName)) {
                if (match != null) {
                    throw new UnsupportedOperationException(String.format("Multiple cloners are available to clone a model of type '%s'.", modelName));
                }
                match = cloner;
            }
        }
        if (match != null) {
            return match;
        }

        throw new UnknownModelException(String.format("No cloners are available to clone a model of type '%s'.", modelName));
    }
}
