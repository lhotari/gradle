/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl.dependencies

import groovy.transform.CompileStatic
import org.codehaus.groovy.runtime.InvokerHelper
import org.gradle.api.artifacts.ClientModule
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.util.ConfigureUtil

@CompileStatic
class ModuleFactoryDelegate {
    ClientModule clientModule
    DependencyFactory dependencyFactory

    def ModuleFactoryDelegate(ClientModule clientModule, DependencyFactory dependencyFactory) {
        this.clientModule = clientModule
        this.dependencyFactory = dependencyFactory
    }

    Closure prepareDelegation(Closure configureClosure) {
        if (!configureClosure) {
            return null
        }
        Closure clonedClosure = (Closure) configureClosure.clone()
        clonedClosure.delegate = new ClientModuleConfigureDelegate(clientModule: clientModule, moduleFactoryDelegate: this)
        clonedClosure.resolveStrategy = Closure.DELEGATE_FIRST
        clonedClosure
    }

    void dependency(Object dependencyNotation) {
        dependency(dependencyNotation, null)
    }

    void dependency(Object dependencyNotation, Closure configureClosure) {
        def dependency = dependencyFactory.createDependency(dependencyNotation)
        clientModule.addDependency((ModuleDependency) dependency)
        ConfigureUtil.configure(configureClosure, dependency)
    }

    void dependencies(Object[] dependencyNotations) {
        for (Object notation : dependencyNotations) {
            clientModule.addDependency((ModuleDependency) dependencyFactory.createDependency(notation))
        }
    }

    void module(Object dependencyNotation, Closure configureClosure) {
        clientModule.addDependency(dependencyFactory.createModule(dependencyNotation, configureClosure))
    }

    @CompileStatic
    static class ClientModuleConfigureDelegate {
        ModuleFactoryDelegate moduleFactoryDelegate
        ClientModule clientModule

        @Override
        Object invokeMethod(String name, Object args) {
            if (name == 'dependency' || name == 'dependencies' || name == 'module') {
                return InvokerHelper.invokeMethod(moduleFactoryDelegate, name, args)
            } else {
                return InvokerHelper.invokeMethod(clientModule, name, args)
            }
        }

        @Override
        Object getProperty(String property) {
            return InvokerHelper.getProperty(clientModule, property)
        }

        @Override
        void setProperty(String property, Object newValue) {
            InvokerHelper.setProperty(clientModule, property, newValue)
        }
    }
}

