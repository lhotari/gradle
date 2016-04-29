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

public class NonThrowingClassLoaderWrapper implements NonThrowingClassLoader {
    private final NonThrowingClassLoader delegate;
    private final ClassLoader classLoader;

    public NonThrowingClassLoaderWrapper(final ClassLoader classLoader) {
        this.classLoader = classLoader;
        if (classLoader instanceof NonThrowingClassLoader) {
            this.delegate = (NonThrowingClassLoader) classLoader;
        } else {
            this.delegate = new DirectNonThrowingClassLoader(classLoader);
        }
    }

    @Override
    public Class<?> loadClassOrReturnNull(String name) {
        return delegate.loadClassOrReturnNull(name);
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NonThrowingClassLoaderWrapper that = (NonThrowingClassLoaderWrapper) o;
        return classLoader != null ? classLoader.equals(that.classLoader) : that.classLoader == null;
    }

    @Override
    public int hashCode() {
        return classLoader.hashCode();
    }

    public static class DirectNonThrowingClassLoader implements NonThrowingClassLoader {
        private final ClassLoader classLoader;

        public DirectNonThrowingClassLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public Class<?> loadClassOrReturnNull(String name) {
            try {
                return classLoader.loadClass(name);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
    }
}
