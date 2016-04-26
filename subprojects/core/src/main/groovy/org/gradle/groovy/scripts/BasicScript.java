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

package org.gradle.groovy.scripts;

import groovy.lang.MetaClass;
import groovy.lang.MissingPropertyException;
import org.gradle.api.internal.*;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.internal.logging.StandardOutputCapture;
import org.gradle.internal.service.ServiceRegistry;

import java.util.Map;

public abstract class BasicScript extends org.gradle.groovy.scripts.Script implements org.gradle.api.Script, FileOperations, ProcessOperations, DynamicObjectAware {
    private StandardOutputCapture standardOutputCapture;
    private Object target;
    private DynamicObject dynamicTarget;
    private DynamicObject scriptDynamicObject;
    private boolean useDefaultOut = false;

    public void init(Object target, ServiceRegistry services) {
        standardOutputCapture = services.get(StandardOutputCapture.class);
        setScriptTarget(target);
    }

    public Object getScriptTarget() {
        return target;
    }

    private void setScriptTarget(Object target) {
        this.target = target;
        this.dynamicTarget = DynamicObjectUtil.asDynamicObject(target);
        this.scriptDynamicObject = new CompositeDynamicObject() {
            {
                setObjects(new BeanDynamicObject(BasicScript.this) {
                    {
                        directMetaPropertyLookup = false;
                    }
                }, dynamicTarget);
            }

            @Override
            protected String getDisplayName() {
                return getScriptSource().getDisplayName();
            }
        };
    }

    @Override
    public DynamicObject getAsDynamicObject() {
        return scriptDynamicObject;
    }

    public StandardOutputCapture getStandardOutputCapture() {
        return standardOutputCapture;
    }

    public void setProperty(String property, Object newValue) {
        if ("metaClass".equals(property)) {
            setMetaClass((MetaClass) newValue);
        } else if ("scriptTarget".equals(property)) {
            setScriptTarget(newValue);
        } else {
            dynamicTarget.setProperty(property, newValue);
        }
    }

    @Override
    public Object getProperty(String property) {
        if ("metaClass".equals(property)) {
            return getMetaClass();
        } else if ("scriptTarget".equals(property)) {
            return getScriptTarget();
        } else if ("standardOutputCapture".equals(property)) {
            return getStandardOutputCapture();
        } else if (useDefaultOut && "out".equals(property)) {
            return System.out;
        } else {
            try {
                return scriptDynamicObject.getProperty(property);
            } catch (MissingPropertyException e) {
                if ("out".equals(property)) {
                    useDefaultOut = true;
                    return System.out;
                }
                throw e;
            }
        }
    }

    @Override
    public Object invokeMethod(String name, Object args) {
        return scriptDynamicObject.invokeMethod(name, (Object[]) args);
    }

    public Map<String, ?> getProperties() {
        return dynamicTarget.getProperties();
    }

    public boolean hasProperty(String property) {
        return dynamicTarget.hasProperty(property);
    }
}


