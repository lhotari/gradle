/*
 * Copyright 2010 the original author or authors.
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

import groovy.lang.*;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.gradle.api.Nullable;
import org.gradle.api.internal.coerce.MethodArgumentsTransformer;
import org.gradle.api.internal.coerce.PropertySetTransformer;
import org.gradle.api.internal.coerce.StringToEnumTransformer;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.JavaReflectionUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * A {@link DynamicObject} which uses groovy reflection to provide access to the properties and methods of a bean.
 */
public class BeanDynamicObject extends AbstractDynamicObject {
    private static final Method META_PROP_METHOD;
    private static final Field MISSING_PROPERTY_GET_METHOD;
    private final Object bean;
    private final boolean includeProperties;
    private final MetaClassAdapter delegate;
    private final boolean implementsMissing;
    @Nullable
    private final Class<?> publicType;

    // NOTE: If this guy starts caching internally, consider sharing an instance
    private final MethodArgumentsTransformer argsTransformer = StringToEnumTransformer.INSTANCE;
    private final PropertySetTransformer propertySetTransformer = StringToEnumTransformer.INSTANCE;

    static {
        try {
            META_PROP_METHOD = MetaClassImpl.class.getDeclaredMethod("getMetaProperty", String.class, boolean.class);
            META_PROP_METHOD.setAccessible(true);
            MISSING_PROPERTY_GET_METHOD = MetaClassImpl.class.getDeclaredField("propertyMissingGet");
            MISSING_PROPERTY_GET_METHOD.setAccessible(true);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    protected boolean directMetaPropertyLookup = true;

    public BeanDynamicObject(Object bean) {
        this(bean, null, true, true);
    }

    public BeanDynamicObject(Object bean, @Nullable Class<?> publicType) {
        this(bean, publicType, true, true);
    }

    private BeanDynamicObject(Object bean, @Nullable Class<?> publicType, boolean includeProperties, boolean implementsMissing) {
        if (bean == null) {
            throw new IllegalArgumentException("Value is null");
        }
        this.bean = bean;
        this.publicType = publicType;
        this.includeProperties = includeProperties;
        this.implementsMissing = implementsMissing;
        this.delegate = determineDelegate(bean);
    }

    public MetaClassAdapter determineDelegate(Object bean) {
        if (bean instanceof DynamicObject || bean instanceof DynamicObjectAware || !(bean instanceof GroovyObject)) {
            return new MetaClassAdapter();
        } else {
            return new GroovyObjectAdapter();
        }
    }

    public BeanDynamicObject withNoProperties() {
        return new BeanDynamicObject(bean, publicType, false, implementsMissing);
    }

    public BeanDynamicObject withNotImplementsMissing() {
        return new BeanDynamicObject(bean, publicType, includeProperties, false);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    protected String getDisplayName() {
        return bean.toString();
    }

    @Nullable
    @Override
    protected Class<?> getPublicType() {
        return publicType != null ? publicType : bean.getClass();
    }

    @Override
    protected boolean hasUsefulDisplayName() {
        return !JavaReflectionUtil.hasDefaultToString(bean);
    }

    private MetaClass getMetaClass() {
        if (bean instanceof GroovyObject) {
            return ((GroovyObject) bean).getMetaClass();
        } else {
            return GroovySystem.getMetaClassRegistry().getMetaClass(bean.getClass());
        }
    }

    @Override
    public boolean hasProperty(String name) {
        return delegate.hasProperty(name);
    }

    @Override
    public void getProperty(String name, GetPropertyResult result) {
        delegate.getProperty(name, result);
    }

    @Override
    public void setProperty(String name, Object value, SetPropertyResult result) {
        delegate.setProperty(name, value, result);
    }

    @Override
    public Map<String, ?> getProperties() {
        return delegate.getProperties();
    }

    @Override
    public boolean hasMethod(String name, Object... arguments) {
        return delegate.hasMethod(name, arguments);
    }

    @Override
    public void invokeMethod(String name, InvokeMethodResult result, Object... arguments) {
        delegate.invokeMethod(name, result, arguments);
        if (result.isFound()) {
            return;
        }

        Object[] transformedArguments = argsTransformer.transform(bean, name, arguments);
        if (transformedArguments != arguments) {
            delegate.invokeMethod(name, result, transformedArguments);
        }
    }

    private class MetaClassAdapter {
        protected String getDisplayName() {
            return BeanDynamicObject.this.getDisplayName();
        }

        public boolean hasProperty(String name) {
            return includeProperties && lookupProperty(getMetaClass(), name) != null;
        }

        public void getProperty(String name, GetPropertyResult result) {
            if (!includeProperties) {
                return;
            }

            MetaClass metaClass = getMetaClass();

            // First look for a property known to the meta-class
            MetaProperty property = lookupProperty(metaClass, name);
            if (property != null) {
                if (property instanceof MetaBeanProperty && ((MetaBeanProperty) property).getGetter() == null) {
                    throw getWriteOnlyProperty(name);
                }

                try {
                    result.result(property.getProperty(bean));
                } catch (InvokerInvocationException e) {
                    if (e.getCause() instanceof RuntimeException) {
                        throw (RuntimeException) e.getCause();
                    }
                    throw e;
                }
                return;
            }

            if (!implementsMissing) {
                return;
            }

            // Fall back to propertyMissing, if available
            MetaMethod propertyMissing = findPropertyMissingMethod(metaClass);
            if (propertyMissing != null) {
                try {
                    result.result(propertyMissing.invoke(bean, new Object[]{name}));
                } catch (MissingPropertyException e) {
                    if (!name.equals(e.getProperty())) {
                        throw e;
                    }
                }
            } else if (bean instanceof GroovyObject && !(bean instanceof DynamicObjectAware)) {
                try {
                    result.result(((GroovyObject) bean).getProperty(name));
                } catch (MissingPropertyException e) {
                    if (!name.equals(e.getProperty())) {
                        throw e;
                    }
                }
            }
        }

        @Nullable
        private MetaMethod findPropertyMissingMethod(MetaClass metaClass) {
            if (metaClass instanceof MetaClassImpl) {
                // Reach into meta class to avoid lookup
                try {
                    return (MetaMethod) MISSING_PROPERTY_GET_METHOD.get(metaClass);
                } catch (IllegalAccessException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }

            // Query the declared methods of the meta class
            for (MetaMethod method : metaClass.getMethods()) {
                if (method.getName().equals("propertyMissing") && method.getParameterTypes().length == 1) {
                    return method;
                }
            }
            return null;
        }

        @Nullable
        private MetaProperty lookupProperty(MetaClass metaClass, String name) {
            if (directMetaPropertyLookup && metaClass instanceof MetaClassImpl) {
                // MetaClass.getMetaProperty(name) is very expensive when the property is not known. Instead, reach into the meta class to call a much more efficient lookup method
                try {
                    return (MetaProperty) META_PROP_METHOD.invoke(metaClass, name, false);
                } catch (Throwable e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }

            // Some other meta-class implementation - fall back to the public API
            return metaClass.getMetaProperty(name);
        }

        public void setProperty(final String name, Object value, SetPropertyResult result) {
            if (!includeProperties) {
                return;
            }

            MetaClass metaClass = getMetaClass();
            MetaProperty property = lookupProperty(metaClass, name);
            if (property != null) {
                if (property instanceof MetaBeanProperty && ((MetaBeanProperty) property).getSetter() == null) {
                    throw setReadOnlyProperty(name);
                }
                try {
                    value = propertySetTransformer.transformValue(bean, property, value);
                    metaClass.setProperty(bean, name, value);
                    result.found();
                    return;
                } catch (InvokerInvocationException e) {
                    if (e.getCause() instanceof RuntimeException) {
                        throw (RuntimeException) e.getCause();
                    }
                    throw e;
                }
            }
            if (!implementsMissing) {
                return;
            }

            try {
                setOpaqueProperty(name, value, metaClass);
                result.found();
            } catch (MissingPropertyException e) {
                if (!name.equals(e.getProperty())) {
                    throw e;
                }
            }
        }

        protected void setOpaqueProperty(String name, Object value, MetaClass metaClass) {
            metaClass.invokeMissingProperty(bean, name, value, false);
        }

        public Map<String, ?> getProperties() {
            if (!includeProperties) {
                return Collections.emptyMap();
            }

            Map<String, Object> properties = new HashMap<String, Object>();
            List<MetaProperty> classProperties = getMetaClass().getProperties();
            for (MetaProperty metaProperty : classProperties) {
                if (metaProperty.getName().equals("properties")) {
                    properties.put("properties", properties);
                    continue;
                }
                if (metaProperty instanceof MetaBeanProperty) {
                    MetaBeanProperty beanProperty = (MetaBeanProperty) metaProperty;
                    if (beanProperty.getGetter() == null) {
                        continue;
                    }
                }
                properties.put(metaProperty.getName(), metaProperty.getProperty(bean));
            }
            return properties;
        }

        public boolean hasMethod(final String name, final Object... arguments) {
            return lookupMethod(name, arguments) != null;
        }

        public void invokeMethod(String name, InvokeMethodResult result, Object... arguments) {
            MetaMethod metaMethod = lookupMethod(name, arguments);
            if (metaMethod != null) {
                result.result(metaMethod.doMethodInvoke(bean, arguments));
                return;
            }
            if (!implementsMissing) {
                return;
            }

            try {
                try {
                    result.result(invokeOpaqueMethod(name, arguments));
                } catch (InvokerInvocationException e) {
                    if (e.getCause() instanceof RuntimeException) {
                        throw (RuntimeException) e.getCause();
                    }
                    throw e;
                }
            } catch (MissingMethodException e) {
                if (!e.getMethod().equals(name) || !Arrays.equals(e.getArguments(), arguments)) {
                    throw e;
                }
                // Ignore
            }
        }

        private MetaMethod lookupMethod(String name, Object[] arguments) {
            return getMetaClass().getMetaMethod(name, arguments);
        }

        protected Object invokeOpaqueMethod(String name, Object[] arguments) {
            return getMetaClass().invokeMethod(bean, name, arguments);
        }
    }

    /*
       The GroovyObject interface defines dynamic property and dynamic method methods. Implementers
       are free to implement their own logic in these methods which makes it invisible to the metaclass.

       The most notable case of this is Closure.

       So in this case we use these methods directly on the GroovyObject in case it does implement logic at this level.
     */
    private class GroovyObjectAdapter extends MetaClassAdapter {
        private final GroovyObject groovyObject = (GroovyObject) bean;

        @Override
        protected void setOpaqueProperty(String name, Object value, MetaClass metaClass) {
            groovyObject.setProperty(name, value);
        }

        @Override
        protected Object invokeOpaqueMethod(String name, Object[] arguments) {
            return groovyObject.invokeMethod(name, arguments);
        }
    }
}
