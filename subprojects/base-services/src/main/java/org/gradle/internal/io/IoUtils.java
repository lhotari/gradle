/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.io;

import org.gradle.api.Transformer;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.CompositeStoppable;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;

public abstract class IoUtils {

    // TODO merge in IoActions

    public static <T, C extends Closeable> T get(C resource, Transformer<T, ? super C> transformer) {
        try {
            return transformer.transform(resource);
        } finally {
            CompositeStoppable.stoppable(resource).stop();
        }
    }

    // disable URL caching which keeps Jar files opens
    // Modifying open Jar files might cause JVM crashes on Unixes (jar/zip files are mmapped by default)
    // On Windows, open Jar files keep file locks that can prevent modifications or deletion
    public static void disableUrlCaching() {
        try {
            new URL("jar:file://any_valid_jar_url_syntax.jar!/").openConnection().setDefaultUseCaches(false);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
