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

package org.gradle.api.internal.project;

import groovy.lang.Closure;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownProjectException;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.LoggingManager;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.groovy.scripts.DefaultScript;
import org.gradle.internal.logging.StandardOutputCapture;

import java.io.File;
import java.util.Map;
import java.util.Set;

public abstract class ProjectScript extends DefaultScript {

    public void apply(Closure closure) {
        getProject().apply(closure);
    }

    public void apply(Map options) {
        getProject().apply(options);
    }

    public Project getProject() {
        return (Project) getScriptTarget();
    }

    public StandardOutputCapture getStandardOutputCapture() {
        return ((ProjectInternal) getProject()).getStandardOutputCapture();
    }

    public LoggingManager getLogging() {
        return getProject().getLogging();
    }

    public Logger getLogger() {
        return getProject().getLogger();
    }

    public String toString() {
        return getProject().toString();
    }

    public Project getRootProject() {
        return getProject().getRootProject();
    }

    public File getRootDir() {
        return getProject().getRootDir();
    }

    public File getBuildDir() {
        return getProject().getBuildDir();
    }

    public Set<Project> getAllprojects() {
        return getProject().getAllprojects();
    }

    public Set<Project> getSubprojects() {
        return getProject().getSubprojects();
    }

    public Task task(String name) throws InvalidUserDataException {
        return getProject().task(name);
    }

    public Task task(Map<String, ?> args, String name) throws InvalidUserDataException {
        return getProject().task(args, name);
    }

    public Task task(Map<String, ?> args, String name, Closure configureClosure) {
        return getProject().task(args, name, configureClosure);
    }

    public Task task(String name, Closure configureClosure) {
        return getProject().task(name, configureClosure);
    }

    public Project project(String path) throws UnknownProjectException {
        return getProject().project(path);
    }

    public Project project(String path, Closure configureClosure) {
        return getProject().project(path, configureClosure);
    }

    public Project findProject(String path) {
        return getProject().findProject(path);
    }

    public File getProjectDir() {
        return getProject().getProjectDir();
    }

    public ConfigurationContainer getConfigurations() {
        return getProject().getConfigurations();
    }

    public void configurations(Closure configureClosure) {
        getProject().configurations(configureClosure);
    }

    public RepositoryHandler getRepositories() {
        return getProject().getRepositories();
    }

    public void repositories(Closure configureClosure) {
        getProject().repositories(configureClosure);
    }

    public DependencyHandler getDependencies() {
        return getProject().getDependencies();
    }

    public void dependencies(Closure configureClosure) {
        getProject().dependencies(configureClosure);
    }

    public ExtensionContainer getExtensions() {
        return getProject().getExtensions();
    }

    public Gradle getGradle() {
        return getProject().getGradle();
    }

    public File getBuildFile() {
        return getProject().getBuildFile();
    }
}
