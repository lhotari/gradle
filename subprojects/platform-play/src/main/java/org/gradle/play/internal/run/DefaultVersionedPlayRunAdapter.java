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

package org.gradle.play.internal.run;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.jvm.Classpath;
import org.gradle.scala.internal.reflect.ScalaMethod;
import org.gradle.scala.internal.reflect.ScalaReflectionUtil;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;

public abstract class DefaultVersionedPlayRunAdapter implements VersionedPlayRunAdapter, Serializable {
    private final AtomicReference<Object> reloadObject = new AtomicReference<Object>();

    protected abstract Class<?> getBuildLinkClass(ClassLoader classLoader) throws ClassNotFoundException;

    protected abstract Class<?> getDocHandlerFactoryClass(ClassLoader classLoader) throws ClassNotFoundException;

    protected abstract Class<?> getBuildDocHandlerClass(ClassLoader docsClassLoader) throws ClassNotFoundException;

    public Object getBuildLink(ClassLoader classLoader, final File projectPath, final Iterable<File> classpath) throws ClassNotFoundException {
        reloadWithClasspath(classpath);
        return Proxy.newProxyInstance(classLoader, new Class<?>[]{getBuildLinkClass(classLoader)}, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getName().equals("projectPath")) {
                    return projectPath;
                } else if (method.getName().equals("reload")) {
                    Object result = reloadObject.getAndSet(null);
                    if (result == null) {
                        return null;
                    } else if (result instanceof DefaultClassPath) {
                        DefaultClassPath projectClasspath = (DefaultClassPath)result;
                        return new URLClassLoader(projectClasspath.getAsURLs().toArray(new URL[]{}), Thread.currentThread().getContextClassLoader());
                    } else if (result instanceof ReloadErrorInfo) {
                        ReloadErrorInfo reloadErrorInfo = (ReloadErrorInfo)result;
                        return createReloadException(Thread.currentThread().getContextClassLoader(), reloadErrorInfo.getTitle(), reloadErrorInfo.getDescription());
                    } else {
                        throw new IllegalStateException();
                    }
                } else if (method.getName().equals("settings")) {
                    return new HashMap<String, String>();
                }
                //TODO: all methods
                return null;
            }
        });
    }

    @Override
    public void reloadWithClasspath(Iterable<File> classpath) {
        reloadObject.set(new DefaultClassPath(classpath));
    }

    @Override
    public void reloadWithError(String title, String description) {
        reloadObject.set(new ReloadErrorInfo(title, description));
    }

    Throwable createReloadException(ClassLoader classLoader, String title, String description) {
        // TODO: create play.api.PlayException by reflection
        // https://github.com/playframework/playframework/blob/master/framework/src/play-exceptions/src/main/java/play/api/PlayException.java
        return new Exception(title + " " + description);
    }

    public Object getBuildDocHandler(ClassLoader docsClassLoader, Iterable<File> classpath) throws NoSuchMethodException, ClassNotFoundException, IOException, IllegalAccessException {
        Class<?> docHandlerFactoryClass = getDocHandlerFactoryClass(docsClassLoader);
        Method docHandlerFactoryMethod = docHandlerFactoryClass.getMethod("fromJar", JarFile.class, String.class);
        JarFile documentationJar = findDocumentationJar(classpath);
        try {
            return docHandlerFactoryMethod.invoke(null, documentationJar, "play/docs/content");
        } catch (InvocationTargetException e) {
            throw UncheckedException.unwrapAndRethrow(e);
        }
    }

    private JarFile findDocumentationJar(Iterable<File> classpath) throws IOException {
        // TODO:DAZ Use the location of the DocHandlerFactoryClass instead.
        File docJarFile = null;
        for (File file : classpath) {
            if (file.getName().startsWith("play-docs")) {
                docJarFile = file;
                break;
            }
        }
        return new JarFile(docJarFile);
    }


    public ScalaMethod getNettyServerDevHttpMethod(ClassLoader classLoader, ClassLoader docsClassLoader) throws ClassNotFoundException {
        return ScalaReflectionUtil.scalaMethod(classLoader, "play.core.server.NettyServer", "mainDevHttpMode", getBuildLinkClass(classLoader), getBuildDocHandlerClass(docsClassLoader), int.class);
    }

    static class ReloadErrorInfo {
        private final String title;
        private final String description;

        public ReloadErrorInfo(String title, String description) {
            this.title = title;
            this.description = description;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }
    }
}
