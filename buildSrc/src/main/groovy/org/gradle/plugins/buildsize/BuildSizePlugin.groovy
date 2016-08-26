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

package org.gradle.plugins.buildsize

import com.fasterxml.jackson.core.JsonEncoding
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy
import org.gradle.api.internal.tasks.options.Option
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import org.gradle.util.Path

import java.util.regex.Matcher
import java.util.regex.Pattern

@CompileStatic
class BuildSizePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.tasks.create('buildSize', BuildSizeTask)
    }
}

@CompileStatic
class BuildSizeTask extends DefaultTask {
    @OutputFile
    File destination = new File(project.buildDir, "buildsizeinfo.json")
    @Input
    Collection<String> unmaskedSourceSetNames = ['main', 'test'] as Set
    @Input
    Collection<String> unmaskedConfigurationNames = ['compile', 'testCompile', 'compileOnly', 'testCompileOnly', 'runtime', 'testRuntime',
                                                     'default', 'archives',
                                                     'agent', 'testAgent', 'jacocoAgent',
                                                     'classpath', 'compileClasspath', 'testCompileClasspath', 'testRuntimeClasspath',
                                                     'protobuf', 'testProtobuf',
                                                     'checkstyle', 'codenarc'] as Set
    @Input
    Collection<String> locCountExtensions = ['java', 'groovy', 'scala', 'kt', 'properties', 'xml', 'xsd', 'xsl', 'html', 'js', 'css', 'scss', 'fxml', 'json'] as Set
    @Input
    boolean maskResults = true
    @Input
    int maskingSalt = project.rootDir.absolutePath.toString().hashCode()

    LocCounter defaultLocCounter = DefaultLocCounter.INSTANCE
    Map<String, LocCounter> overriddenLocCounters = Map.cast(['xml': XmlLocCounter.INSTANCE, 'html': XmlLocCounter.INSTANCE, 'fxml': XmlLocCounter.INSTANCE, 'xsd': XmlLocCounter.INSTANCE, 'xsl': XmlLocCounter.INSTANCE])

    @TaskAction
    void createReport() {
        ReportingSession session = new ReportingSession(this, createJsonGenerator(destination))
        session.run()
    }

    @Option(option = "no-mask-results", description = "Don't mask results")
    public void setNoMaskResults(boolean flagPassed) {
        if (flagPassed) {
            this.maskResults = false
        }
    }

    @Option(option = "masking-salt", description = "Apply salt (int value) to masking so that hashes aren't detectable")
    public void setMaskingSaltOption(String maskingSaltString) {
        if (maskingSaltString != null) {
            this.maskingSalt = Integer.parseInt(maskingSaltString)
        }
    }

    private static JsonGenerator createJsonGenerator(File file) {
        JsonFactory jsonFactory = new JsonFactory()
        ObjectMapper objectMapper = new ObjectMapper(jsonFactory)
        JsonGenerator jsonGenerator = jsonFactory.createGenerator(file, JsonEncoding.UTF8)
        jsonGenerator.useDefaultPrettyPrinter()
        jsonGenerator.codec = objectMapper
        jsonGenerator
    }
}

@CompileStatic
class ReportingSession {
    private static final Pattern SOURCE_CODE_TOKEN_SEPARATORS = ~/[\s;]+/
    private static
    final Pattern FILE_SEPARATOR = Pattern.compile(Pattern.quote(File.separator))
    private final BuildSizeTask task
    private final JsonGenerator jsonGenerator
    private final Map<String, String> projectNames = [:]
    private final Map<String, String> sourceSetNames = [:]
    private final Map<String, String> configurationNames = [:]
    private final Map<String, String> configurationPaths = [:]
    long grandTotalSizeInBytes = 0
    long grandTotalSourceCodeSizeInBytes = 0
    int grandTotalLoc = 0
    int grandTotalFileCount = 0
    int grandTotalSourceFileCount = 0
    int sumOfFilePathLengths = 0
    int sumOfFilePathDepths = 0
    Set<String> grandPackageNames = [] as Set

    ReportingSession(BuildSizeTask task, JsonGenerator jsonGenerator) {
        this.task = task
        this.jsonGenerator = jsonGenerator
    }

    public void run() {
        jsonGenerator.writeStartObject()
        try {
            jsonGenerator.writeFieldName('projects')
            jsonGenerator.writeStartArray()
            task.project.allprojects { Project subproject ->
                task.logger.lifecycle("Handling ${subproject.path}, masked as ${maskProjectName(subproject)}")
                jsonGenerator.writeStartObject()
                jsonGenerator.writeStringField('name', maskProjectName(subproject))

                jsonGenerator.writeArrayFieldStart('sourceSets')
                writeProjectSourceSets(subproject)
                jsonGenerator.writeEndArray()

                jsonGenerator.writeArrayFieldStart('configurations')
                writeProjectConfigurations(subproject)
                jsonGenerator.writeEndArray()

                jsonGenerator.writeEndObject()
            }
            jsonGenerator.writeEndArray()

            jsonGenerator.writeNumberField("grandTotalSizeInBytes", grandTotalSizeInBytes)
            jsonGenerator.writeNumberField("grandTotalSourceCodeSizeInBytes", grandTotalSourceCodeSizeInBytes)
            jsonGenerator.writeNumberField("grandTotalLoc", grandTotalLoc)
            jsonGenerator.writeNumberField("grandTotalFileCount", grandTotalFileCount)
            jsonGenerator.writeNumberField("grandTotalSourceFileCount", grandTotalSourceFileCount)
            jsonGenerator.writeNumberField("averageFilePathLength", (sumOfFilePathLengths / grandTotalFileCount) as Integer)
            jsonGenerator.writeNumberField("averageFilePathDepth", (sumOfFilePathDepths / grandTotalFileCount) as Integer)
            jsonGenerator.writeNumberField("rootDirFilePathLength", task.project.rootDir.absolutePath.length())
            jsonGenerator.writeNumberField("rootDirFilePathDepth", pathDepth(task.project.rootDir))

            jsonGenerator.writeEndObject()
        } finally {
            jsonGenerator.close()
        }
    }

    void writeProjectConfigurations(Project project) {
        for (Configuration configuration : project.configurations) {
            //ResolutionResult result = configuration.getIncoming().getResolutionResult()
            //configuration.getResolvedConfiguration()

            def configurationInfo = [:]
            configurationInfo.name = maskConfigurationName(configuration)
            configurationInfo.project = resolvedMaskedProjectForConfiguration(configuration)
            configurationInfo.extendsFrom = configuration.getExtendsFrom().collect {
                [configuration: maskConfigurationName(it), project: resolvedMaskedProjectForConfiguration(it)]
            }
            configurationInfo.visible = configuration.visible
            configurationInfo.transitive = configuration.transitive
            configurationInfo.excludeRulesCount = configuration.getExcludeRules().size()
            configurationInfo.artifactsCount = configuration.artifacts.size()
            configuration.resolutionStrategy.with { resolutionStrategy ->
                def resolutionStrategyInfo = [:]
                configurationInfo.resolutionStrategy = resolutionStrategyInfo
                resolutionStrategyInfo.type = resolutionStrategy instanceof DefaultResolutionStrategy ? 'default' : 'custom'
                resolutionStrategyInfo.forcedModulesCount = resolutionStrategy.forcedModules.size()
                try {
                    def rules = (Collection) resolutionStrategy.componentSelection.getMetaClass().getProperty(resolutionStrategy.componentSelection, "rules")
                    resolutionStrategyInfo.componentSelectionRulesCount = rules.size()
                } catch (e) {
                }
                try {
                    def rules = (Collection) resolutionStrategy.dependencySubstitution.getMetaClass().getAttribute(resolutionStrategy.dependencySubstitution, "substitutionRules")
                    resolutionStrategyInfo.dependencySubstitutionsCount = rules.size()
                } catch (e) {
                }
            }
            configurationInfo.fileCount = configuration.getFiles().size()
            configurationInfo.directoryCount = configuration.getFiles().count { File file -> file.directory } ?: 0
            configurationInfo.filesTotalSize = configuration.getFiles().sum { File file -> file.file ? file.length() : 0 } ?: 0
            configurationInfo.lengthAsClasspath = configuration.getAsPath().length()

            def dependenciesInfo = []
            configurationInfo.dependencies = dependenciesInfo
            for (Dependency dependency : configuration.dependencies) {
                def dependencyInfo = [:]
                dependenciesInfo << dependencyInfo
                fillInDependencyInfo(dependency, dependencyInfo)
            }

            jsonGenerator.writeObject(configurationInfo)
        }
    }

    void fillInDependencyInfo(Dependency dependency, Map<String, Object> dependencyInfo) {
        if (dependency instanceof SelfResolvingDependency) {
            if (dependency instanceof ProjectDependency) {
                dependencyInfo.type = "project"
                def projectDependency = ProjectDependency.cast(dependency)
                dependencyInfo.project = maskProjectName(projectDependency.dependencyProject)
                if (projectDependency.projectConfiguration && projectDependency.projectConfiguration != 'default') {
                    dependencyInfo.configuration = maskConfigurationName(projectDependency.projectConfiguration)
                }
            } else if (dependency instanceof FileCollectionDependency) {
                Set<File> files = FileCollectionDependency.cast(dependency).resolve()
                dependencyInfo.type = "fileCollection"
                dependencyInfo.fileCount = files.count {
                    it.file
                }
                dependencyInfo.directoryCount = files.count {
                    it.directory
                }
            } else {
                dependencyInfo.type = "selfResolving"
            }
        } else {
            if (dependency instanceof ModuleDependency) {
                dependencyInfo.group = maskGroupName(dependency.group)
                dependencyInfo.name = maskDependencyName(dependency.name)
                dependencyInfo.version = maskDependencyVersion(dependency.version)
                dependencyInfo.type = 'module'
                dependencyInfo.transitive = dependency.transitive
                dependencyInfo.excludesRulesCount = dependency.excludeRules.size()
                if (dependency.configuration && dependency.configuration != 'default') {
                    dependencyInfo.configuration = maskConfigurationName(dependency.configuration)
                }
            }
        }
    }

    String maskGeneric(String prefix, String name) {
        if (task.maskResults) {
            name ? "${prefix}_${hashId(name)}".toString() : ''
        } else {
            name
        }
    }

    String maskGroupName(String group) {
        maskGeneric("group", group)
    }

    String maskDependencyName(String name) {
        maskGeneric("name", name)
    }

    String maskDependencyVersion(String version) {
        maskGeneric("version", version)
    }

    private int pathDepth(File file) {
        FILE_SEPARATOR.split(file.absolutePath).size()
    }

    String maskProjectName(Project project) {
        maskProjectNameByPath(project.path)
    }

    String maskProjectNameByPath(String path) {
        if (!task.maskResults) {
            return path
        }
        String masked = projectNames.get(path)
        if (!masked) {
            masked = path == ':' ? 'project_root' : "project_${hashId(path)}".toString()
            projectNames.put(path, masked)
        }
        masked
    }

    String maskConfigurationName(Configuration configuration) {
        maskConfigurationName(configuration.name)
    }

    String maskConfigurationName(String name) {
        if (!name || !task.maskResults) {
            return name
        }
        String masked = configurationNames.get(name)
        if (!masked) {
            if (name in task.unmaskedConfigurationNames) {
                masked = name
            } else {
                masked = "${name.toLowerCase().contains('test') ? 'testC' : 'c'}onfiguration_${hashId(name)}".toString()
            }
            configurationNames.put(name, masked)
        }
        masked
    }

    String resolvedMaskedProjectForConfiguration(Configuration configuration) {
        ConfigurationInternal configurationInternal = (ConfigurationInternal) configuration
        // the project path can be found from ConfigurationInternal.getPath() String by removing the last segment
        Path path = configurationInternal.path ? new Path(configurationInternal.path) : null
        def projectPath = path?.parent?.toString()
        projectPath ? maskProjectNameByPath(projectPath) : null
    }

    String maskSourceSetName(String name) {
        if (!task.maskResults) {
            return name
        }
        String masked = sourceSetNames.get(name)
        if (!masked) {
            if (name in task.unmaskedSourceSetNames) {
                masked = name
            } else if (name.toLowerCase().contains('test')) {
                masked = "otherTests_${hashId(name)}".toString()
            } else {
                masked = "otherSource_${hashId(name)}".toString()
            }
            sourceSetNames.put(name, masked)
        }
        masked
    }

    String hashId(String source) {
        int hash = source.hashCode()
        int salt = task.maskingSalt
        if (salt != 0) {
            hash = 31 * hash + salt
        }
        Integer.toHexString(hash)
    }

    String fileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.')
        if (lastDot > -1) {
            filename.substring(lastDot + 1)
        } else {
            ''
        }
    }

    void writeProjectSourceSets(Project subproject) {
        getJavaPluginConvention(subproject).sourceSets.each { SourceSet sourceSet ->
            long totalSizeInBytes = 0
            long sourceCodeSizeInBytes = 0
            int totalLoc = 0
            int fileCount = 0
            int sourceFileCount = 0
            Map<String, Integer> locCounts = [:]
            Map<String, Integer> sourceFileCounts = [:]
            Map<String, Set<String>> packagesPerExtension = [:]
            def packageCallback = { String extension, String packageLine ->
                def parts = SOURCE_CODE_TOKEN_SEPARATORS.split(packageLine)
                if (parts.size() > 1) {
                    String packageName = parts[1]
                    def allPackages = packagesPerExtension.get(extension)
                    if (allPackages == null) {
                        allPackages = [] as Set
                        packagesPerExtension.put(extension, allPackages)
                    }
                    allPackages << packageName
                }
            }
            sourceSet.allSource.srcDirs.each { File dir ->
                if (!dir.exists()) {
                    return
                }
                dir.eachFileRecurse { File file ->
                    if (file.file) {
                        sumOfFilePathLengths += file.absolutePath.length()
                        sumOfFilePathDepths += pathDepth(file)
                        fileCount++
                        def fileSize = file.length()
                        totalSizeInBytes += fileSize
                        String extension = fileExtension(file.name)
                        if (extension in task.locCountExtensions) {
                            sourceFileCount++
                            sourceCodeSizeInBytes += fileSize

                            LocCounter counterToUse = task.overriddenLocCounters.get(extension) ?: task.defaultLocCounter
                            Integer currentLoc = locCounts.get(extension) ?: 0
                            def fileLoc = counterToUse.count(file, packageCallback.curry(extension))
                            locCounts.put(extension, currentLoc + fileLoc)
                            totalLoc += fileLoc

                            Integer currentSourceFileCount = sourceFileCounts.get(extension) ?: 0
                            sourceFileCounts.put(extension, currentSourceFileCount + 1)
                        }
                    }
                }
            }

            grandTotalLoc += totalLoc
            grandTotalSizeInBytes += totalSizeInBytes
            grandTotalSourceCodeSizeInBytes += sourceCodeSizeInBytes
            def allPackages = mergeAllPackages(packagesPerExtension)
            grandPackageNames.addAll(allPackages)
            grandTotalFileCount += fileCount
            grandTotalSourceFileCount += sourceFileCount

            def sourceSetInfo = [:]

            sourceSetInfo.name = maskSourceSetName(sourceSet.name)
            sourceSetInfo.fileCount = fileCount
            sourceSetInfo.totalSize = totalSizeInBytes
            sourceSetInfo.sourceCodeSize = sourceCodeSizeInBytes
            sourceSetInfo.totalLoc = totalLoc
            sourceSetInfo.loc = locCounts
            sourceSetInfo.sourceFileCounts = sourceFileCounts
            sourceSetInfo.packagesPerExtension = packagesPerExtension.collectEntries { k, v -> [k, v.size()] }
            sourceSetInfo.totalPackages = allPackages.size()

            sourceSetInfo.compileClasspathConfigurationName = maskConfigurationName(sourceSet.compileClasspathConfigurationName)
            sourceSetInfo.compileConfigurationName = maskConfigurationName(sourceSet.compileConfigurationName)
            sourceSetInfo.compileOnlyConfigurationName = maskConfigurationName(sourceSet.compileOnlyConfigurationName)
            sourceSetInfo.runtimeConfigurationName = maskConfigurationName(sourceSet.runtimeConfigurationName)


            jsonGenerator.writeObject(sourceSetInfo)
        }
    }

    Collection<String> mergeAllPackages(Map<String, Set<String>> packagesPerExtension) {
        packagesPerExtension.values().collectMany([] as Set) { Set<String> packageNames ->
            packageNames
        }
    }

    JavaPluginConvention getJavaPluginConvention(Project p) {
        p.convention.getPlugin(JavaPluginConvention)
    }
}

/*
 * LOC counting classes copied originally from https://github.com/aalmiray/stats-gradle-plugin
 * This is a stripped down version with just C-style and XML-style comment detection
 */

@CompileStatic
interface LocCounter {
    Pattern EMPTY = ~/^\s*$/
    Pattern SLASH_SLASH = ~/^\s*\/\/.*/
    Pattern SLASH_STAR_STAR_SLASH = ~/^(.*)\/\*(.*)\*\/(.*)$/

    int count(File file,
              @ClosureParams(value = SimpleType, options = ['String']) Closure<?> packageCallback)
}

@CompileStatic
class DefaultLocCounter implements LocCounter {
    static final DefaultLocCounter INSTANCE = new DefaultLocCounter()

    @Override
    int count(File file,
              @ClosureParams(value = SimpleType, options = ['String']) Closure<?> packageCallback) {
        def loc = 0
        def comment = 0
        file.eachLine { line ->
            String trimmed = line.trim()
            if (!trimmed.length() || line ==~ EMPTY) {
                return
            } else if (line ==~ SLASH_SLASH) {
                return
            }

            if (trimmed.startsWith('package ')) {
                packageCallback(line)
            }

            Matcher m = line =~ SLASH_STAR_STAR_SLASH
            if (m.find() && m.group(1) ==~ EMPTY && m.group(3) ==~ EMPTY) {
                return
            }
            int open = line.indexOf('/*')
            int close = line.indexOf('*/')
            if (open != -1 && (close - open) <= 1) {
                comment++
            } else {
                if (close != -1 && comment) {
                    comment--
                    if (!comment) {
                        return
                    }
                }
            }

            if (!comment) {
                loc++
            }
        }

        loc
    }
}

@CompileStatic
class XmlLocCounter implements LocCounter {
    static final XmlLocCounter INSTANCE = new XmlLocCounter()
    static final Pattern OPEN_CARET_CLOSE_CARET = ~/^(.*)<!--(.*)-->(.*)$/

    @Override
    int count(File file,
              @ClosureParams(value = SimpleType, options = ['String']) Closure<?> packageCallback) {
        def loc = 0
        def comment = 0
        file.eachLine { line ->
            if (!line.trim().length() || line ==~ EMPTY) {
                return
            }

            def m = line =~ OPEN_CARET_CLOSE_CARET
            if (m.find() && m.group(1) ==~ EMPTY && m.group(3) ==~ EMPTY) {
                return
            }
            int open = line.indexOf('<!--')
            int close = line.indexOf('-->')

            if (open != -1 && (close - open) <= 1) {
                comment++
            } else if (close != -1 && comment) {
                comment--
                if (!comment) {
                    return
                }
            }

            if (!comment) {
                loc++
            }
        }

        loc
    }
}

