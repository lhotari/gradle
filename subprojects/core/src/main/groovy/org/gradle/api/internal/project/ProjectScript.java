package org.gradle.api.internal.project;

import groovy.lang.Closure;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownProjectException;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.DynamicObject;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.GetPropertyResult;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.LoggingManager;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.groovy.scripts.DefaultScript;
import org.gradle.internal.logging.StandardOutputCapture;
import org.gradle.internal.service.ServiceRegistry;

import java.io.File;
import java.util.Map;
import java.util.Set;

public abstract class ProjectScript extends DefaultScript {
    DynamicObject dynamicObject;

    @Override
    public void init(Object target, ServiceRegistry services) {
        super.init(target, services);
        if (getScriptTarget() instanceof DynamicObjectAware) {
            dynamicObject = ((DynamicObjectAware) getScriptTarget()).getAsDynamicObject();
        }
    }

    public void apply(Closure closure) {
        getProject().apply(closure);
    }

    public void apply(Map options) {
        getProject().apply(options);
    }

    public Project getProject() {
        return (Project) getScriptTarget();
    }

    public ScriptHandler getBuildscript() {
        return getProject().getBuildscript();
    }

    public void buildscript(Closure configureClosure) {
        getProject().buildscript(configureClosure);
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

    @Override
    protected PropertyValue resolvePropertyValue(String property) {
        if (property.equals("project")) {
            return new PropertyValue(getProject());
        }
        if (property.equals("rootProject")) {
            return new PropertyValue(getRootProject());
        }
        if (property.equals("gradle")) {
            return new PropertyValue(getGradle());
        }
        Object extension = getExtensions().findByName(property);
        if (extension != null) {
            return new PropertyValue(extension);
        }
        if (getExtensions().getExtraProperties().has(property)) {
            return new PropertyValue(getExtensions().getExtraProperties().get(property));
        }
        if (dynamicObject != null) {
            GetPropertyResult result = new GetPropertyResult();
            dynamicObject.getProperty(property, result);
            if (result.isFound()) {
                return new PropertyValue(result.getValue());
            }
        }
        return null;
    }
}
