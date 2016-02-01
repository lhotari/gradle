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

import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleProjectTask;
import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleTask;
import org.gradle.tooling.internal.gradle.DefaultGradleProject;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.provider.model.ToolingModelCloner;

import java.util.ArrayList;
import java.util.List;

public class GradleProjectCloner implements ToolingModelCloner {
    @Override
    public boolean canClone(String modelName) {
        return modelName.equals("org.gradle.tooling.model.GradleProject");
    }

    @Override
    public Object cloneModel(String modelName, Object source) {
        return buildHierarchy((GradleProject) source);
    }

    private DefaultGradleProject<LaunchableGradleTask> buildHierarchy(GradleProject source) {
        List<DefaultGradleProject<LaunchableGradleTask>> children = new ArrayList<DefaultGradleProject<LaunchableGradleTask>>();
        for (GradleProject child : source.getChildren()) {
            children.add(buildHierarchy(child));
        }

        DefaultGradleProject<LaunchableGradleTask> gradleProject = new DefaultGradleProject<LaunchableGradleTask>()
            .setPath(source.getPath())
            .setName(source.getName())
            .setDescription(source.getDescription())
            .setBuildDirectory(source.getBuildDirectory())
            .setProjectDirectory(source.getProjectDirectory())
            .setChildren(children);

        gradleProject.getBuildScript().setSourceFile(source.getBuildScript().getSourceFile());
        gradleProject.setTasks(cloneTasks(gradleProject, source.getTasks()));

        for (DefaultGradleProject child : children) {
            child.setParent(gradleProject);
        }

        return gradleProject;
    }

    private static List<LaunchableGradleTask> cloneTasks(DefaultGradleProject owner, DomainObjectSet<? extends GradleTask> sourceTasks) {
        List<LaunchableGradleTask> tasks = new ArrayList<LaunchableGradleTask>();
        for (GradleTask sourceTask : sourceTasks) {
            LaunchableGradleProjectTask target = new LaunchableGradleProjectTask();
            target.setProject(owner);
            target.setPath(sourceTask.getPath())
                .setName(sourceTask.getName())
                .setGroup(sourceTask.getGroup())
                .setDisplayName(sourceTask.getDisplayName())
                .setDescription(sourceTask.getDescription())
                .setPublic(sourceTask.isPublic());
        }
        return tasks;
    }

}
