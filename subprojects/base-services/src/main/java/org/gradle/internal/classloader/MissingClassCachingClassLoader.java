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

package org.gradle.internal.classloader;

import java.util.HashSet;

public class MissingClassCachingClassLoader extends ClassLoader implements ClassLoaderHierarchy, NonThrowingClassLoader {
    private final HashSet<String> missingClassNames = new HashSet<String>();
    private final NonThrowingClassLoaderWrapper parentWrapper;

    public MissingClassCachingClassLoader(ClassLoader parent) {
        super(parent);
        this.parentWrapper = new NonThrowingClassLoaderWrapper(parent);
    }

    @Override
    public Class<?> loadClassOrReturnNull(String name) {
        synchronized (missingClassNames) {
            if (missingClassNames.contains(name)) {
                return null;
            }
            Class<?> result = parentWrapper.loadClassOrReturnNull(name);
            if (result == null) {
                missingClassNames.add(name);
                return null;
            }
            return result;
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> cl = loadClassOrReturnNull(name);
        if (cl == null) {
            throw new ClassNotFoundException(name + " not found.");
        }
        if (resolve) {
            resolveClass(cl);
        }
        return cl;
    }

    public void visit(ClassLoaderVisitor visitor) {
        visitor.visitSpec(new Spec());
        visitor.visitParent(getParent());
    }

    public static class Spec extends ClassLoaderSpec {
        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass().equals(Spec.class);
        }

        @Override
        public int hashCode() {
            return getClass().getName().hashCode();
        }
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MissingClassCachingClassLoader)) {
            return false;
        }

        MissingClassCachingClassLoader that = (MissingClassCachingClassLoader) o;
        return parentWrapper.equals(that.parentWrapper);
    }

    public int hashCode() {
        return parentWrapper.hashCode();
    }
}
