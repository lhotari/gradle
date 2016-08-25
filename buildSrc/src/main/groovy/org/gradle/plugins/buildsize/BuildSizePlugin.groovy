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
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

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

    Collection<String> sourceSetNames = ['main', 'test'] as Set
    Collection<String> configurationNames = ['compile', 'runtime', 'agent', 'testAgent', 'classpath', 'jacocoAgent', 'compileClasspath', 'testCompileClasspath', 'testRuntimeClasspath', 'protobuf', 'testProtobuf'] as Set
    Collection<String> countedExtensions = ['java', 'groovy', 'scala', 'kt', 'properties', 'xml', 'xsd', 'xsl', 'html', 'js', 'css', 'scss', 'fxml', 'json'] as Set
    Counter counter = JavaCounter.INSTANCE
    Map<String, Counter> overrideCounters = [:]

    BuildSizeTask() {
        initDefaults()
    }

    @CompileDynamic
    initDefaults() {
        // workaround Groovy STC bug
        overrideCounters = ['xml': XmlCounter.INSTANCE, 'html': XmlCounter.INSTANCE, 'fxml': XmlCounter.INSTANCE, 'xsd': XmlCounter.INSTANCE, 'xsl': XmlCounter.INSTANCE]
    }

    @TaskAction
    void createReport() {
        ReportingSession session = new ReportingSession(this, createJsonGenerator(destination))
        session.run()
    }

    JavaPluginConvention getJavaPluginConvention(Project p) {
        p.convention.getPlugin(JavaPluginConvention)
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
    private int nextProjectId = 1
    private int nextTestSourceSetId = 1
    private int nextOtherSourceSetId = 1
    private int nextConfigurationId = 1
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
            String name = maskConfigurationName(configuration)
            def configurationInfo = [:]
            configurationInfo.name = name
            configurationInfo.extendsFrom = configuration.getExtendsFrom().collect {
                maskConfigurationName(it)
            }
            configurationInfo.excludeRulesCount = configuration.getExcludeRules().size()
            configuration.resolutionStrategy.with { resolutionStrategy ->
                def resolutionStrategyInfo = [:]
                configurationInfo.resolutionStrategy = resolutionStrategyInfo
                resolutionStrategyInfo.forcedModulesCount = resolutionStrategy.forcedModules.size()
                try {
                    def rules = (Collection) resolutionStrategy.dependencySubstitution.getMetaClass().getAttribute(resolutionStrategy.dependencySubstitution, "substitutionRules")
                    resolutionStrategyInfo.dependencySubstitutionsCount = rules.size()
                } catch (e) {
                }
            }
            configurationInfo.fileCount = configuration.getFiles().size()
            configurationInfo.filesTotalSize = configuration.getFiles().sum { File file -> file.length() } ?: 0
            configurationInfo.lengthAsClasspath = configuration.getAsPath().length()
            jsonGenerator.writeObject(configurationInfo)
        }
    }

    private int pathDepth(File file) {
        FILE_SEPARATOR.split(file.absolutePath).size()
    }

    String maskProjectName(Project project) {
        String masked = projectNames.get(project.path)
        if (!masked) {
            masked = "project${nextProjectId++}".toString()
            projectNames.put(project.path, masked)
        }
        masked
    }

    String maskConfigurationName(Configuration configuration) {
        String name = configuration.name
        String masked = configurationNames.get(name)
        if (!masked) {
            if (name in task.configurationNames) {
                masked = name
            } else {
                masked = "configuration${nextConfigurationId++}".toString()
            }
            configurationNames.put(configuration.name, masked)
        }
        masked
    }

    String maskSourceSetName(String name) {
        String masked = sourceSetNames.get(name)
        if (!masked) {
            if (name in task.sourceSetNames) {
                masked = name
            } else if (name.toLowerCase().contains('test')) {
                masked = "otherTests${nextTestSourceSetId++}".toString()
            } else {
                masked = "otherSource${nextOtherSourceSetId++}".toString()
            }
            sourceSetNames.put(name, masked)
        }
        masked
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
        task.getJavaPluginConvention(subproject).sourceSets.each { sourceSet ->
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
                        if (extension in task.countedExtensions) {
                            sourceFileCount++
                            sourceCodeSizeInBytes += fileSize

                            Counter counterToUse = task.overrideCounters.get(extension) ?: task.counter
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

            jsonGenerator.with {
                writeStartObject()
                writeStringField("name", maskSourceSetName(sourceSet.name))
                writeNumberField("fileCount", fileCount)
                writeNumberField("totalSize", totalSizeInBytes)
                writeNumberField("sourceCodeSize", sourceCodeSizeInBytes)
                writeNumberField("totalLoc", totalLoc)
                writeObjectField("loc", locCounts)
                writeObjectField("sourceFileCounts", sourceFileCounts)
                writeObjectField("packagesPerExtension", packagesPerExtension.collectEntries { k, v -> [k, v.size()] })
                writeObjectField("totalPackages", allPackages.size())
                writeEndObject()
            }
        }
    }

    Collection<String> mergeAllPackages(Map<String, Set<String>> packagesPerExtension) {
        packagesPerExtension.values().collectMany([] as Set) { Set<String> packageNames ->
            packageNames
        }
    }
}

@CompileStatic
interface Counter {
    Pattern EMPTY = ~/^\s*$/
    Pattern SLASH_SLASH = ~/^\s*\/\/.*/
    Pattern SLASH_STAR_STAR_SLASH = ~/^(.*)\/\*(.*)\*\/(.*)$/

    int count(File file,
              @ClosureParams(value = SimpleType, options = ['String']) Closure<?> packageCallback)
}

@CompileStatic
class JavaCounter implements Counter {
    static final JavaCounter INSTANCE = new JavaCounter()

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
class XmlCounter implements Counter {
    static final XmlCounter INSTANCE = new XmlCounter()
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

