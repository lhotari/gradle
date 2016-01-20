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

package org.gradle.composite;

import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.ResultHandler;

import java.util.List;

public interface CompositeBuildConnectionInternal extends CompositeBuildConnection {
    List<ProjectIdentity> getProjects();

    // Direct pass-through to ProjectConnection
    ProjectConnection project(ProjectIdentity id);

    // Model methods targeting a single project
    <T> T getModel(ProjectIdentity id, Class<T> modelType);
    <T> void getModel(Class<T> modelType, ResultHandler<? super T> handler) throws IllegalStateException;
    <T> ModelBuilder<T> model(ProjectIdentity id, Class<T> modelType);

/*
    // ???? Why do we want this?
    // Model methods to get model for root projects
    List<ProjectIdentity> getRootProjects();
    <T> Map<ProjectIdentity, T> getRootModels(Class<T> modelType);
    <T> void getRootModels(Class<T> modelType, CompositeResultHandler<T> handler) throws IllegalStateException;
    <T> CompositeModelBuilder<T> rootModels(Class<T> modelType);
*/
}


