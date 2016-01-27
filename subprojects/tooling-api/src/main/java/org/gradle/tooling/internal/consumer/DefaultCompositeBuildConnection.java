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
package org.gradle.tooling.internal.consumer;

import org.gradle.tooling.CompositeBuildConnection;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;

class DefaultCompositeBuildConnection implements CompositeBuildConnection {
    private final AsyncConsumerActionExecutor connection;
    private final CompositeConnectionParameters parameters;

    public DefaultCompositeBuildConnection(AsyncConsumerActionExecutor connection, CompositeConnectionParameters parameters) {
        this.connection = connection;
        this.parameters = parameters;
    }

    public void close() {
        connection.stop();
    }

    public <T> ModelBuilder<T> model(Class<T> modelType) {
        if (!modelType.isInterface()) {
            throw new IllegalArgumentException(String.format("Cannot fetch a model of type '%s' as this type is not an interface.", modelType.getName()));
        }
        return new DefaultModelBuilder<T>(modelType, connection, parameters);
    }

    @Override
    public <T> T getModel(Class<T> modelType) throws GradleConnectionException, IllegalStateException {
        return model(modelType).get();
    }
}
