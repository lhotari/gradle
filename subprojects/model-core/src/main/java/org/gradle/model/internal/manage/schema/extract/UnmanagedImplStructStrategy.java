/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.manage.schema.extract;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import org.gradle.api.Action;
import org.gradle.internal.Actions;
import org.gradle.model.internal.core.UnmanagedStruct;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.UnmanagedImplStructSchema;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.Method;
import java.util.Collection;

public class UnmanagedImplStructStrategy extends StructSchemaExtractionStrategySupport {

    public UnmanagedImplStructStrategy(ModelSchemaAspectExtractor aspectExtractor) {
        super(aspectExtractor);
    }

    @Override
    protected boolean isTarget(ModelType<?> type) {
        // Everything is an unmanaged struct that hasn't been handled before
        return true;
    }

    @Override
    protected <R> void validateTypeHierarchy(ModelSchemaExtractionContext<R> extractionContext, ModelType<R> type) {
    }

    @Override
    protected void validateMethodDeclarationHierarchy(ModelSchemaExtractionContext<?> context, CandidateMethods candidateMethods) {
    }

    @Override
    protected void handleNonPropertyMethod(ModelSchemaExtractionContext<?> context, Collection<Method> nonPropertyMethodsWithEqualSignature) {
    }

    @Override
    protected boolean selectProperty(ModelSchemaExtractionContext<?> context, ModelPropertyExtractionContext property) {
        return property.isReadable();
    }

    @Override
    protected void handleInvalidGetter(ModelSchemaExtractionContext<?> extractionContext, Method getter, String message) {
    }

    @Override
    protected void validateProperty(ModelSchemaExtractionContext<?> context, ModelPropertyExtractionContext property) {
    }

    @Override
    protected ModelProperty.StateManagementType determineStateManagementType(ModelSchemaExtractionContext<?> extractionContext, PropertyAccessorExtractionContext getterContext) {
        return ModelProperty.StateManagementType.UNMANAGED;
    }

    @Override
    protected <P> Action<ModelSchema<P>> createPropertyValidator(ModelSchemaExtractionContext<?> extractionContext, ModelPropertyExtractionResult<P> propertyResult) {
        return Actions.doNothing();
    }

    @Override
    protected <R> ModelSchema<R> createSchema(final ModelSchemaExtractionContext<R> extractionContext, Iterable<ModelPropertyExtractionResult<?>> propertyResults, Iterable<ModelSchemaAspect> aspects) {
        Iterable<ModelProperty<?>> properties = Iterables.transform(propertyResults, new Function<ModelPropertyExtractionResult<?>, ModelProperty<?>>() {
            @Override
            public ModelProperty<?> apply(ModelPropertyExtractionResult<?> propertyResult) {
                return propertyResult.getProperty();
            }
        });
        return new UnmanagedImplStructSchema<R>(extractionContext.getType(), properties, aspects, extractionContext.getType().getConcreteClass().getAnnotation(UnmanagedStruct.class) != null);
    }
}
