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

package org.gradle.groovy.scripts.internal;

import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import org.gradle.api.Nullable;
import org.gradle.api.internal.*;

import java.util.*;

public class ScriptClosureContextStack {
    private final Deque<ScriptClosureContext> stack = new LinkedList<ScriptClosureContext>();
    private static final ThreadLocal<Object> fallbackDynamicTargetThreadLocal = new ThreadLocal<Object>();
    private static final ThreadLocal<Set<DynamicObject>> previousDynamicObjects = new ThreadLocal<Set<DynamicObject>>() {
        @Override
        protected Set<DynamicObject> initialValue() {
            return new HashSet<DynamicObject>();
        }
    };

    public static Object setFallbackDynamicTarget(Object delegate) {
        Object previous = fallbackDynamicTargetThreadLocal.get();
        fallbackDynamicTargetThreadLocal.set(delegate);
        return previous;
    }

    public static DynamicObject createDynamicObjectInstance(Object delegate) {
        return new ClosureContextDynamicObject(delegate);
    }

    private static DynamicObject findParent(Object originalDelegate) {
        DynamicObject current = resolveDynamicObject(originalDelegate);
        final Set<DynamicObject> usedDynamicObjects = previousDynamicObjects.get();
        if (current != null) {
            usedDynamicObjects.add(current);
        }
        ScriptClosureContextStack contextStack = Holder.currentStack();
        if (contextStack != null) {
            boolean returnNextFound = false;
            for (ScriptClosureContext context : contextStack.stack) {
                DynamicObject dynamicObject = null;
                if (context.getDelegate() == originalDelegate) {
                    returnNextFound = true;
                    if (context.getOwner() != context.getDelegate()) {
                        dynamicObject = resolveUnusedDynamicObject(context.getOwner(), usedDynamicObjects);
                    }
                } else if (returnNextFound) {
                    dynamicObject = resolveUnusedDynamicObject(context.getDelegate(), usedDynamicObjects);
                    if (dynamicObject == null && context.getOwner() != context.getDelegate()) {
                        dynamicObject = resolveUnusedDynamicObject(context.getOwner(), usedDynamicObjects);
                    }
                }
                if (dynamicObject != null) {
                    return dynamicObject;
                }
            }
            if (returnNextFound) {
                Object fallbackTarget = fallbackDynamicTargetThreadLocal.get();
                if (fallbackTarget != originalDelegate) {
                    DynamicObject dynamicObject = resolveUnusedDynamicObject(fallbackTarget, usedDynamicObjects);
                    if (dynamicObject != null) {
                        return dynamicObject;
                    }
                }
            }
        }
        return null;
    }

    private static DynamicObject resolveUnusedDynamicObject(Object object, Set<DynamicObject> usedDynamicObjects) {
        DynamicObject dynamicObject = resolveDynamicObject(object);
        if (dynamicObject != null && !usedDynamicObjects.contains(dynamicObject)) {
            usedDynamicObjects.add(dynamicObject);
            return dynamicObject;
        }
        return null;
    }

    @Nullable
    private static DynamicObject resolveDynamicObject(Object object) {
        if (object instanceof DynamicObject) {
            return (DynamicObject) object;
        }
        if (object instanceof DynamicObjectAware) {
            return ((DynamicObjectAware) object).getAsDynamicObject();
        }
        return null;
    }

    private static class ScriptClosureContext {
        private final Object owner;
        private final Object delegate;

        private ScriptClosureContext(Object owner, Object delegate) {
            this.owner = owner;
            this.delegate = delegate;
        }

        public Object getOwner() {
            return owner;
        }

        public Object getDelegate() {
            return delegate;
        }
    }

    public void push(Object owner, Object delegate) {
        stack.push(new ScriptClosureContext(owner, delegate));
    }

    public void pop() {
        stack.pop();
    }

    public ScriptClosureContext current() {
        return stack.peek();
    }

    public static class Holder {
        private static final ThreadLocal<ScriptClosureContextStack> currentStackThreadLocal = new ThreadLocal<ScriptClosureContextStack>();

        public static ScriptClosureContextStack create() {
            ScriptClosureContextStack previous = currentStackThreadLocal.get();
            currentStackThreadLocal.set(new ScriptClosureContextStack());
            return previous;
        }

        public static void reset(ScriptClosureContextStack previous) {
            currentStackThreadLocal.set(previous);
        }

        public static void push(Object owner, Object delegate) {
            ScriptClosureContextStack currentStack = currentStackThreadLocal.get();
            if (currentStack != null) {
                currentStack.push(owner, delegate);
            }
        }

        public static void pop() {
            ScriptClosureContextStack currentStack = currentStackThreadLocal.get();
            if (currentStack != null) {
                currentStack.pop();
            }
        }

        public static ScriptClosureContextStack currentStack() {
            return currentStackThreadLocal.get();
        }
    }

    private static class ClosureContextDynamicObject extends AbstractDynamicObject {
        final Object originalDelegate;

        public ClosureContextDynamicObject(Object originalDelegate) {
            super();
            this.originalDelegate = originalDelegate;
        }

        @Override
        protected String getDisplayName() {
            try {
                DynamicObject delegate = findParent(originalDelegate);
                return (delegate != null) ? "Proxy to " + delegate.toString() : "Proxy without target from threadlocal stack";
            } finally {
                previousDynamicObjects.remove();
            }
        }

        @Override
        public boolean hasProperty(String name) {
            try {
                DynamicObject delegate = findParent(originalDelegate);
                if (delegate != null) {
                    return delegate.hasProperty(name);
                } else {
                    return false;
                }
            } finally {
                previousDynamicObjects.remove();
            }
        }

        @Override
        public void getProperty(String name, GetPropertyResult result) {
            try {
                DynamicObject delegate = findParent(originalDelegate);
                if (delegate != null) {
                    delegate.getProperty(name, result);
                }
            } finally {
                previousDynamicObjects.remove();
            }
        }

        @Override
        public Object getProperty(String name) throws MissingPropertyException {
            try {
                DynamicObject delegate = findParent(originalDelegate);
                if (delegate != null) {
                    return delegate.getProperty(name);
                } else {
                    throw getMissingProperty(name);
                }
            } finally {
                previousDynamicObjects.remove();
            }
        }

        @Override
        public void setProperty(String name, Object value) throws MissingPropertyException {
            try {
                DynamicObject delegate = findParent(originalDelegate);
                if (delegate != null) {
                    delegate.setProperty(name, value);
                } else {
                    throw setMissingProperty(name);
                }
            } finally {
                previousDynamicObjects.remove();
            }
        }

        @Override
        public void setProperty(String name, Object value, SetPropertyResult result) {
            try {
                DynamicObject delegate = findParent(originalDelegate);
                if (delegate != null) {
                    delegate.setProperty(name, value, result);
                }
            } finally {
                previousDynamicObjects.remove();
            }
        }

        @Override
        public Map<String, ?> getProperties() {
            try {
                DynamicObject delegate = findParent(originalDelegate);
                if (delegate != null) {
                    return delegate.getProperties();
                } else {
                    return Collections.emptyMap();
                }
            } finally {
                previousDynamicObjects.remove();
            }
        }

        @Override
        public boolean hasMethod(String name, Object... arguments) {
            try {
                DynamicObject delegate = findParent(originalDelegate);
                if (delegate != null) {
                    return delegate.hasMethod(name, arguments);
                }
                return false;
            } finally {
                previousDynamicObjects.remove();
            }
        }

        @Override
        public Object invokeMethod(String name, Object... arguments) throws MissingMethodException {
            try {
                DynamicObject delegate = findParent(originalDelegate);
                if (delegate != null) {
                    return delegate.invokeMethod(name, arguments);
                }
                throw methodMissingException(name, arguments);
            } finally {
                previousDynamicObjects.remove();
            }
        }

        @Override
        public void invokeMethod(String name, InvokeMethodResult result, Object... arguments) {
            try {
                DynamicObject delegate = findParent(originalDelegate);
                if (delegate != null) {
                    delegate.invokeMethod(name, result, arguments);
                }
            } finally {
                previousDynamicObjects.remove();
            }
        }
    }
}
