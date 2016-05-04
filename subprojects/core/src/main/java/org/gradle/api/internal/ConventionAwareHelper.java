/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import org.codehaus.groovy.runtime.metaclass.MissingPropertyExceptionNoStack;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.plugins.DefaultConvention;
import org.gradle.api.plugins.Convention;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.JavaReflectionUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import static org.gradle.util.GUtil.uncheckedCall;

public class ConventionAwareHelper extends GroovyObjectSupport implements ConventionMapping, HasConvention {
    private static Cache<Class<?>, Set<String>> allPropertyNamesCache = CacheBuilder.newBuilder().weakKeys().maximumSize(10000).build();

    //prefix internal fields with _ so that they don't get into the way of propertyMissing()
    private final Convention _convention;
    private final IConventionAware _source;
    private final Map<String, MappedPropertyImpl> _mappings = new HashMap<String, MappedPropertyImpl>();
    private final Set<String> allPropertyNames;

    /**
     * @see org.gradle.api.internal.AsmBackedClassGenerator.ClassBuilderImpl#mixInConventionAware()
     */
    public ConventionAwareHelper(IConventionAware source) {
        this(source, new DefaultConvention());
    }

    public ConventionAwareHelper(IConventionAware source, Convention convention) {
        this._source = source;
        this._convention = convention;
        this.allPropertyNames = allPropertyNamesCached(source.getClass());
    }

    private static Set<String> allPropertyNamesCached(final Class<?> target) {
        try {
            return allPropertyNamesCache.get(target, new Callable<Set<String>>() {
                @Override
                public Set<String> call() throws Exception {
                    return JavaReflectionUtil.allPropertyNames(target);
                }
            });
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private static interface Value<T> {
        T getValue(Convention convention, IConventionAware conventionAwareObject);
    }

    private boolean doesPropertyExist(String propertyName) {
        return allPropertyNames.contains(propertyName);
    }

    private MappedProperty map(String propertyName, Value<?> value) {
        if (!doesPropertyExist(propertyName)) {
            throw new InvalidUserDataException(
                    "You can't map a property that does not exist: propertyName=" + propertyName);
        }

        MappedPropertyImpl mappedProperty = new MappedPropertyImpl(value);
        _mappings.put(propertyName, mappedProperty);
        return mappedProperty;
    }

    public MappedProperty map(String propertyName, final Closure<?> value) {
        return map(propertyName, new Value<Object>() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                switch (value.getMaximumNumberOfParameters()) {
                    case 0:
                        return value.call();
                    case 1:
                        return value.call(convention);
                    default:
                        return value.call(convention, conventionAwareObject);
                }
            }
        });
    }

    public MappedProperty map(String propertyName, final Callable<?> value) {
        return map(propertyName, new Value<Object>() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return uncheckedCall(value);
            }
        });
    }

    @Override
    public Object getProperty(String property) {
        throw new MissingPropertyExceptionNoStack(property, getClass());
    }

    @Override
    public void setProperty(String property, Object newValue) {
        if (newValue instanceof Closure) {
            map(property, (Closure) newValue);
        } else {
            throw new MissingPropertyExceptionNoStack(property, getClass());
        }
    }

    public <T> T getConventionValue(T actualValue, String propertyName, boolean isExplicitValue) {
        if (isExplicitValue) {
            return actualValue;
        }

        Object returnValue = actualValue;
        if (_mappings.containsKey(propertyName)) {
            boolean useMapping = true;
            if (actualValue instanceof Collection && !((Collection<?>) actualValue).isEmpty()) {
                useMapping = false;
            } else if (actualValue instanceof Map && !((Map<?, ?>) actualValue).isEmpty()) {
                useMapping = false;
            }
            if (useMapping) {
                returnValue = _mappings.get(propertyName).getValue(_convention, _source);
            }
        }
        return (T) returnValue;
    }

    public Convention getConvention() {
        return _convention;
    }

    private static class MappedPropertyImpl implements MappedProperty {
        private final Value<?> value;
        private boolean haveValue;
        private boolean cache;
        private Object cachedValue;

        private MappedPropertyImpl(Value<?> value) {
            this.value = value;
        }

        public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
            if (!cache) {
                return value.getValue(convention, conventionAwareObject);
            }
            if (!haveValue) {
                cachedValue = value.getValue(convention, conventionAwareObject);
                haveValue = true;
            }
            return cachedValue;
        }

        public void cache() {
            cache = true;
            cachedValue = null;
        }
    }
}
