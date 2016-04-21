package org.gradle.api.internal.project;

import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.LoggingManager;
import org.gradle.groovy.scripts.DefaultScript;
import org.gradle.internal.logging.StandardOutputCapture;

import java.util.Map;

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

}
