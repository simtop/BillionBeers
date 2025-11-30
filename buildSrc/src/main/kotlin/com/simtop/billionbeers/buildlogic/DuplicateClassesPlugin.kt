package com.simtop.billionbeers.buildlogic

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

abstract class PrintClassesJars : DefaultTask() {

    @get:InputFiles
    abstract val allJars: ListProperty<RegularFile>

    @get:InputFiles
    abstract val allDirectories: ListProperty<Directory>

    @TaskAction
    fun findDuplicateClasses() {
        val classToJarMap = mutableMapOf<String, MutableList<String>>()

        // Process Jars
        allJars.get().forEach { file ->
            try {
                ZipFile(file.asFile).use { zipFile ->
                    val entries = zipFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.isDirectory && 
                            entry.name.endsWith(".class") && 
                            !entry.name.startsWith("META-INF") &&
                            entry.name != "module-info.class"
                        ) {
                            classToJarMap.computeIfAbsent(entry.name) { mutableListOf() }.add(file.asFile.absolutePath)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn("Could not read jar: ${file.asFile.absolutePath}", e)
            }
        }

        // Process Directories (local classes)
        allDirectories.get().forEach { directory ->
            directory.asFile.walkTopDown().forEach { file ->
                if (file.isFile && file.name.endsWith(".class")) {
                    val relativePath = file.toRelativeString(directory.asFile)
                    classToJarMap.computeIfAbsent(relativePath) { mutableListOf() }.add("PROJECT_CLASSES")
                }
            }
        }

        val duplicates = classToJarMap.filterValues { it.size > 1 }

        if (duplicates.isNotEmpty()) {
            logger.error("⚠️ Found duplicate classes:")
            duplicates.forEach { (className, jars) ->
                logger.error(" - $className in:")
                jars.forEach { jar ->
                    logger.error("     → ${prettifyPath(jar)}")
                }
            }
        } else {
            logger.lifecycle("✅ No duplicate classes found")
        }
    }

    private fun prettifyPath(path: String): String {
        return if (path.contains("transforms")) {
            // Try to extract a more readable name from transforms path
            path.substringAfterLast("transformed/").substringAfter("/")
        } else {
            path
        }
    }
}

class DuplicateClassesPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
        androidComponents.onVariants(androidComponents.selector().all()) { variant ->
            val taskProvider = project.tasks.register(
                "${variant.name}PrintDuplicateClasses", PrintClassesJars::class.java
            )
            variant.artifacts.forScope(ScopedArtifacts.Scope.ALL)
                .use(taskProvider)
                .toGet(
                    ScopedArtifact.CLASSES,
                    PrintClassesJars::allJars,
                    PrintClassesJars::allDirectories
                )
        }
    }
}