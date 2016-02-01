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

package org.gradle.plugins.ide.internal.tooling;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.plugins.ide.internal.tooling.eclipse.*;
import org.gradle.plugins.ide.internal.tooling.java.DefaultInstalledJdk;
import org.gradle.tooling.internal.gradle.DefaultGradleProject;
import org.gradle.tooling.model.ExternalDependency;
import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.eclipse.*;
import org.gradle.tooling.model.java.InstalledJdk;
import org.gradle.tooling.provider.model.ToolingModelCloner;

import java.util.*;

public class EclipseModelCloner implements ToolingModelCloner {
    private final GradleProjectCloner gradleProjectCloner;

    public EclipseModelCloner(GradleProjectCloner gradleProjectCloner) {
        this.gradleProjectCloner = gradleProjectCloner;
    }

    @Override
    public boolean canClone(String modelName) {
        return modelName.equals("org.gradle.tooling.model.eclipse.EclipseProject")
            || modelName.equals("org.gradle.tooling.model.eclipse.HierarchicalEclipseProject");
    }

    @Override
    public Object cloneModel(String modelName, Object source) {
        EclipseProject sourceEclipseProject = (EclipseProject) source;
        EclipseProject rootSourceProject = findRoot(sourceEclipseProject);
        DefaultGradleProject<?> rootGradleProject = (DefaultGradleProject<?>) gradleProjectCloner.cloneModel("org.gradle.tooling.model.GradleProject", rootSourceProject.getGradleProject());

        Map<String, DefaultEclipseProject> pathToProjectMapping = new HashMap<String, DefaultEclipseProject>();
        Map<EclipseProject, DefaultEclipseProject> sourceToTargetMapping = new HashMap<EclipseProject, DefaultEclipseProject>();

        DefaultEclipseProject targetRoot = buildHierarchy(rootSourceProject, rootGradleProject, pathToProjectMapping, sourceToTargetMapping);

        populate(targetRoot, rootSourceProject, sourceToTargetMapping);

        return pathToProjectMapping.get(sourceEclipseProject.getGradleProject().getPath());
    }

    private EclipseProject findRoot(EclipseProject sourceEclipseProject) {
        EclipseProject current = sourceEclipseProject;
        while(true) {
            EclipseProject parent = current.getParent();
            if(parent == null || parent == current) {
                break;
            } else {
                current = parent;
            }
        }
        return current;
    }

    private DefaultEclipseProject buildHierarchy(EclipseProject source, DefaultGradleProject<?> rootGradleProject, Map<String, DefaultEclipseProject> projectMapping, Map<EclipseProject, DefaultEclipseProject> sourceToTargetMapping) {
        List<DefaultEclipseProject> children = new ArrayList<DefaultEclipseProject>();
        for (org.gradle.tooling.model.eclipse.EclipseProject child : source.getChildren()) {
            children.add(buildHierarchy(child, rootGradleProject, projectMapping, sourceToTargetMapping));
        }

        final String path = source.getGradleProject().getPath();
        DefaultEclipseProject eclipseProject =
            new DefaultEclipseProject(source.getName(), path, source.getDescription(), source.getProjectDirectory(), children)
                .setGradleProject(rootGradleProject.findByPath(path));

        for (DefaultEclipseProject child : children) {
            child.setParent(eclipseProject);
        }

        projectMapping.put(path, eclipseProject);
        sourceToTargetMapping.put(source, eclipseProject);

        return eclipseProject;
    }

    private void populate(DefaultEclipseProject eclipseProject, EclipseProject source, Map<EclipseProject, DefaultEclipseProject> sourceToTargetMapping) {
        final List<DefaultEclipseExternalDependency> externalDependencies = new LinkedList<DefaultEclipseExternalDependency>();
        for (ExternalDependency externalDependency : source.getClasspath()) {
            externalDependencies.add(new DefaultEclipseExternalDependency(externalDependency.getFile(), externalDependency.getJavadoc(),
                externalDependency.getSource(), convertModuleVersion(externalDependency.getGradleModuleVersion()), externalDependency.isExported()));
        }
        eclipseProject.setClasspath(externalDependencies);

        final List<DefaultEclipseProjectDependency> projectDependencies = new LinkedList<DefaultEclipseProjectDependency>();
        for (EclipseProjectDependency projectDependency : source.getProjectDependencies()) {
            projectDependencies.add(new DefaultEclipseProjectDependency(projectDependency.getPath(), sourceToTargetMapping.get(projectDependency.getTargetProject()), projectDependency.isExported()));
        }
        eclipseProject.setProjectDependencies(projectDependencies);

        final List<DefaultEclipseSourceDirectory> sourceDirectories = new LinkedList<DefaultEclipseSourceDirectory>();
        for (EclipseSourceDirectory sourceDirectory : source.getSourceDirectories()) {
            sourceDirectories.add(new DefaultEclipseSourceDirectory(sourceDirectory.getPath(), sourceDirectory.getDirectory()));
        }
        eclipseProject.setSourceDirectories(sourceDirectories);

        List<DefaultEclipseLinkedResource> linkedResources = new LinkedList<DefaultEclipseLinkedResource>();
        for (EclipseLinkedResource r : source.getLinkedResources()) {
            linkedResources.add(new DefaultEclipseLinkedResource(r.getName(), r.getType(), r.getLocation(), r.getLocationUri()));
        }
        eclipseProject.setLinkedResources(linkedResources);

        List<DefaultEclipseProjectNature> natures = new ArrayList<DefaultEclipseProjectNature>();
        for (EclipseProjectNature n : source.getProjectNatures()) {
            natures.add(new DefaultEclipseProjectNature(n.getId()));
        }
        eclipseProject.setProjectNatures(natures);

        List<DefaultEclipseBuildCommand> buildCommands = new ArrayList<DefaultEclipseBuildCommand>();
        for (EclipseBuildCommand b : source.getBuildCommands()) {
            buildCommands.add(new DefaultEclipseBuildCommand(b.getName(), b.getArguments()));
        }
        eclipseProject.setBuildCommands(buildCommands);

        EclipseJavaSourceSettings javaSourceSettings = source.getJavaSourceSettings();
        if (javaSourceSettings != null) {
            eclipseProject.setJavaSourceSettings(new DefaultEclipseJavaSourceSettings()
                .setSourceLanguageLevel(javaSourceSettings.getSourceLanguageLevel())
                .setTargetBytecodeVersion(javaSourceSettings.getTargetBytecodeVersion())
                .setJdk(cloneJdk(javaSourceSettings.getJdk()))
            );
        }

        for (EclipseProject childProject : source.getChildren()) {
            populate(sourceToTargetMapping.get(childProject), childProject, sourceToTargetMapping);
        }
    }

    private DefaultInstalledJdk cloneJdk(InstalledJdk jdk) {
        return new DefaultInstalledJdk(jdk.getJavaHome(), jdk.getJavaVersion());
    }


    private ModuleVersionIdentifier convertModuleVersion(GradleModuleVersion gradleModuleVersion) {
        return new DefaultModuleVersionIdentifier(gradleModuleVersion.getGroup(), gradleModuleVersion.getName(), gradleModuleVersion.getVersion());
    }
}
