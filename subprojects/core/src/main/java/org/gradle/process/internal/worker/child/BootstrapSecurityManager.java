/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.process.internal.worker.child;

import org.gradle.process.internal.streams.EncodedStream;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.Permission;
import java.util.ArrayList;
import java.util.List;

/**
 * Used to bootstrap the system classpath.
 */
public class BootstrapSecurityManager extends SecurityManager {
    private boolean initialised;
    private final URLClassLoader target;

    public BootstrapSecurityManager() {
        this(null);
    }

    BootstrapSecurityManager(URLClassLoader target) {
        this.target = target;
    }

    @Override
    public void checkPermission(Permission permission) {
        synchronized (this) {
            if (initialised) {
                return;
            }
            if (System.in == null) {
                // Still starting up
                return;
            }

            initialised = true;
        }

        resetSecurityManager();

        BootstrapSettings bootstrapSettings = new BootstrapSettings(System.in);
        URLClassLoader systemClassLoader = resolveSystemClassLoader();

        injectSystemClassPath(bootstrapSettings.classpathEntries, systemClassLoader);

        applySecurityManager(bootstrapSettings.securityManagerType, systemClassLoader);
    }

    private void resetSecurityManager() {
        System.clearProperty("java.security.manager");
        System.setSecurityManager(null);
    }

    private void applySecurityManager(String securityManagerType, URLClassLoader systemClassLoader) {
        if (securityManagerType.length() > 0) {
            System.setProperty("java.security.manager", securityManagerType);
            SecurityManager securityManager;
            try {
                Class<?> aClass = systemClassLoader.loadClass(securityManagerType);
                securityManager = (SecurityManager) aClass.newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Could not create an instance of '" + securityManagerType + "' specified for system SecurityManager.", e);
            }
            System.setSecurityManager(securityManager);
        }
    }

    private URLClassLoader resolveSystemClassLoader() {
        return target != null ? target : (URLClassLoader) getClass().getClassLoader();
    }

    private void injectSystemClassPath(List<String> classpathEntries, URLClassLoader systemClassLoader) {
        try {
            Method addUrlMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addUrlMethod.setAccessible(true);
            StringBuilder classpathStr = new StringBuilder();
            for (String entry : classpathEntries) {
                File file = new File(entry);
                addUrlMethod.invoke(systemClassLoader, file.toURI().toURL());
                if (classpathStr.length() > 0) {
                    classpathStr.append(File.pathSeparator);
                }
                classpathStr.append(file.toString());
            }
            System.setProperty("java.class.path", classpathStr.toString());
        } catch (Exception e) {
            throw new RuntimeException("Could not initialise system classpath.", e);
        }
    }

    public static class BootstrapSettings {
        List<String> classpathEntries;
        String securityManagerType;

        BootstrapSettings(InputStream input) {
            DataInputStream inputStream = new DataInputStream(new EncodedStream.EncodedInput(input));
            try {
                classpathEntries = readClassPathEntries(inputStream);
            } catch (IOException e) {
                throw new RuntimeException("Could not read classpath entries from input.", e);
            }
            try {
                securityManagerType = inputStream.readUTF();
            } catch (IOException e) {
                throw new RuntimeException("Could not read security manager type from input.", e);
            }
        }

        private List<String> readClassPathEntries(DataInputStream inputStream) throws IOException {
            int count = inputStream.readInt();
            List<String> entries = new ArrayList<String>(count);
            for (int i = 0; i < count; i++) {
                String entry = inputStream.readUTF();
                entries.add(entry);
            }
            return entries;
        }
    }
}
