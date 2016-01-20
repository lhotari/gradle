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

import org.gradle.api.Incubating;
import org.gradle.tooling.*;

import java.awt.*;
import java.util.Map;

/**
 * A {@code ModelBuilder} allows you to fetch a snapshot of some model for a project or a build.
 * Instances of {@code ModelBuilder} are not thread-safe.
 * <p>
 * You use a {@code ModelBuilder} as follows:
 *
 * <ul>
 * <li>Create an instance of {@code ModelBuilder} by calling {@link ProjectConnection#model(Class)}.
 * <li>Configure the builder as appropriate.
 * <li>Call either {@link #get()} or {@link #get(ResultHandler)} to build the model.
 * <li>Optionally, you can reuse the builder to build the model multiple times.
 * </ul>
 *
 * Example:
 * <pre autoTested=''>
 * ProjectConnection connection = GradleConnector.newConnector()
 *    .forProjectDirectory(new File("someFolder"))
 *    .connect();
 *
 * try {
 *    ModelBuilder&lt;GradleProject&gt; builder = connection.model(GradleProject.class);
 *
 *    //if you use a different than usual build file name:
 *    builder.withArguments("--build-file", "theBuild.gradle");
 *
 *    //configure the standard input in case your build is interactive:
 *    builder.setStandardInput(new ByteArrayInputStream("consume this!".getBytes()));
 *
 *    //if you want to listen to the progress events:
 *    ProgressListener listener = null; // use your implementation
 *    builder.addProgressListener(listener);
 *
 *    //get the model:
 *    GradleProject project = builder.get();
 *
 *    //query the model for information:
 *    System.out.println("Available tasks: " + project.getTasks());
 * } finally {
 *    connection.close();
 * }
 * </pre>
 *
 * @param <T> The type of model to build
 * @since 1.0-milestone-3
 */
public interface CompositeModelBuilder<T> extends ConfigurableLauncher<CompositeModelBuilder<T>> {
    CompositeModelBuilder<T> forTasks(String... tasks);
    CompositeModelBuilder<T> forTasks(Iterable<String> tasks);

    Map<ProjectIdentity, T> get() throws GradleConnectionException, IllegalStateException;
    void get(CompositeResultHandler<? super T> handler) throws IllegalStateException;
}
