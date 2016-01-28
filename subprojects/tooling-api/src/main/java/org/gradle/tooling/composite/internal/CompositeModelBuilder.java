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

package org.gradle.tooling.composite.internal;

import org.gradle.tooling.*;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.consumer.CompositeConnectionParameters;
import org.gradle.tooling.internal.consumer.DefaultModelBuilder;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.internal.model.SetOfEclipseProjects;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

public class CompositeModelBuilder<T> implements ModelBuilder<Set<T>> {
    private final ModelBuilder<SetOfEclipseProjects> delegate;

    protected CompositeModelBuilder(Class<T> modelType, AsyncConsumerActionExecutor asyncConnection, CompositeConnectionParameters parameters) {
        delegate = new DefaultModelBuilder<SetOfEclipseProjects>(SetOfEclipseProjects.class, asyncConnection, parameters);
    }

    @Override
    public ModelBuilder<Set<T>> forTasks(String... tasks) {
        delegate.forTasks(tasks);
        return this;
    }

    @Override
    public ModelBuilder<Set<T>> forTasks(Iterable<String> tasks) {
        return this;
    }

    @Override
    public Set<T> get() throws GradleConnectionException, IllegalStateException {
        return (Set<T>) delegate.get().getProjects();
    }

    @Override
    public void get(final ResultHandler<? super Set<T>> handler) throws IllegalStateException {
        delegate.get(new ResultHandler<SetOfEclipseProjects>() {
                         @Override
                         public void onComplete(SetOfEclipseProjects result) {
                             handler.onComplete((Set<T>) result.getProjects());
                         }

                         @Override
                         public void onFailure(GradleConnectionException failure) {
                             handler.onFailure(failure);
                         }
                     }
        );
    }

    @Override
    public ModelBuilder<Set<T>> withArguments(String... arguments) {
        delegate.withArguments(arguments);
        return this;
    }

    @Override
    public ModelBuilder<Set<T>> withArguments(Iterable<String> arguments) {
        delegate.withArguments(arguments);
        return this;
    }

    @Override
    public ModelBuilder<Set<T>> setStandardOutput(OutputStream outputStream) {
        delegate.setStandardOutput(outputStream);
        return this;
    }

    @Override
    public ModelBuilder<Set<T>> setStandardError(OutputStream outputStream) {
        delegate.setStandardError(outputStream);
        return this;
    }

    @Override
    public ModelBuilder<Set<T>> setColorOutput(boolean colorOutput) {
        delegate.setColorOutput(colorOutput);
        return this;
    }

    @Override
    public ModelBuilder<Set<T>> setStandardInput(InputStream inputStream) {
        delegate.setStandardInput(inputStream);
        return this;
    }

    @Override
    public ModelBuilder<Set<T>> setJavaHome(File javaHome) {
        delegate.setJavaHome(javaHome);
        return this;
    }

    @Override
    public ModelBuilder<Set<T>> setJvmArguments(String... jvmArguments) {
        delegate.setJvmArguments(jvmArguments);
        return this;
    }

    @Override
    public ModelBuilder<Set<T>> setJvmArguments(Iterable<String> jvmArguments) {
        delegate.setJvmArguments(jvmArguments);
        return this;
    }

    @Override
    public ModelBuilder<Set<T>> addProgressListener(ProgressListener listener) {
        delegate.addProgressListener(listener);
        return this;
    }

    @Override
    public ModelBuilder<Set<T>> addProgressListener(org.gradle.tooling.events.ProgressListener listener) {
        delegate.addProgressListener(listener);
        return this;
    }

    @Override
    public ModelBuilder<Set<T>> addProgressListener(org.gradle.tooling.events.ProgressListener listener, Set<OperationType> eventTypes) {
        delegate.addProgressListener(listener, eventTypes);
        return this;
    }

    @Override
    public ModelBuilder<Set<T>> addProgressListener(org.gradle.tooling.events.ProgressListener listener, OperationType... operationTypes) {
        delegate.addProgressListener(listener, operationTypes);
        return this;
    }

    @Override
    public ModelBuilder<Set<T>> withCancellationToken(CancellationToken cancellationToken) {
        delegate.withCancellationToken(cancellationToken);
        return this;
    }
}
