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

package org.gradle.tooling;

public interface CompositeBuildConnection {

    <T> T getModel(Class<T> modelType) throws GradleConnectionException, IllegalStateException;
    <T> ModelBuilder<T> model(Class<T> modelType);

    // Model methods to get model for all projects
    //<T> Map<ProjectIdentity, T> getModels(Class<T> modelType);
/*
    <T> Map<ProjectIdentity, T> getModels(Class<T> modelType);
    <T> void getModels(Class<T> modelType, CompositeResultHandler<T> handler) throws IllegalStateException;
    <T> CompositeModelBuilder<T> models(Class<T> modelType);
    */

/*
    // ???? Why do we want this?
    // Model methods to get model for root projects
    List<ProjectIdentity> getRootProjects();
    <T> Map<ProjectIdentity, T> getRootModels(Class<T> modelType);
    <T> void getRootModels(Class<T> modelType, CompositeResultHandler<T> handler) throws IllegalStateException;
    <T> CompositeModelBuilder<T> rootModels(Class<T> modelType);
*/
}


