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
import java.io.File
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
                        if (!entry.isDirectory && entry.name.endsWith(".class") && !entry.name.startsWith("META-INF")) {
                            classToJarMap.computeIfAbsent(entry.name) { mutableListOf() }.add(file.asFile.absolutePath)
                        }
                    }
                }
            } catch (e: Exception) {
                println("Could not read jar: ${file.asFile.absolutePath}")
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
            println("⚠️ Found duplicate classes:")
            duplicates.forEach { (className, jars) ->
                println(" - $className in:")
                jars.forEach { jar ->
                    println("     → $jar")
                }
            }
        } else {
            println("✅ No duplicate classes found")
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