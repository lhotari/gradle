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

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.api.Transformer;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.InternalCancellableConnection;

import java.util.LinkedHashSet;
import java.util.Set;

public class CompositeAwareModelProducer extends CancellableModelBuilderBackedModelProducer implements MultiModelProducer {
    public CompositeAwareModelProducer(ProtocolToModelAdapter adapter, VersionDetails versionDetails, ModelMapping modelMapping, InternalCancellableConnection builder, Transformer<RuntimeException, RuntimeException> exceptionTransformer) {
        super(adapter, versionDetails, modelMapping, builder, exceptionTransformer);
    }

    @Override
    public <T> Set<T> produceModels(Class<T> elementType, ConsumerOperationParameters operationParameters) {
        BuildResult<?> result = buildModel(elementType, operationParameters);
        Set<T> models = new LinkedHashSet<T>();
        if (result.getModel() instanceof Iterable) {
            adapter.convertCollection(models, elementType, Iterable.class.cast(result.getModel()), getCompatibilityMapperAction());
        }
        return models;
    }
}
