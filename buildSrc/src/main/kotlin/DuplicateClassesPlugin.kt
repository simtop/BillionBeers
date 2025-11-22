package com.simtop.billionbeers.plugin

//import com.android.build.api.artifact.ScopedArtifact
//import com.android.build.api.variant.AndroidComponentsExtension
//import com.android.build.api.variant.ScopedArtifacts
//import org.gradle.api.DefaultTask
//import org.gradle.api.Plugin
//import org.gradle.api.Project
//import org.gradle.api.file.Directory
//import org.gradle.api.file.RegularFile
//import org.gradle.api.provider.ListProperty
//import org.gradle.api.tasks.InputFiles
//import org.gradle.api.tasks.TaskAction
//import org.gradle.api.tasks.TaskProvider
//import java.io.File
//import java.util.zip.ZipEntry
//import java.util.zip.ZipFile
//
//
//abstract class PrintClassesJars : DefaultTask() {
//
//    @InputFiles
//    abstract fun getAllClassFiles(): ListProperty<RegularFile>
//
//    @InputFiles
//    abstract fun getAllDirectories(): ListProperty<Directory>
//
//    @TaskAction
//    fun findDuplicateClasses() {
//        val classToJarMap = mutableMapOf<String, MutableList<File>>().withDefault { mutableListOf() }
//
//        val jarToModuleMap = buildJarToModuleMap()
//
//        getAllJars().get().forEach { file ->
//            ZipFile(file.asFile).use { zipFile ->
//                val entries = zipFile.entries()
//                while (entries.hasMoreElements()) {
//                    val entry = entries.nextElement()
//                    if (entry.name.endsWith(".class")) {
//                        classToJarMap[entry.name] = classToJarMap.getValue(entry.name).apply { add(file.asFile) }
//                    }
//                }
//            }
//        }
//
//        val duplicates = classToJarMap.filterValues { it.size > 1 }
//
//        if (duplicates.isNotEmpty()) {
//            println("⚠️ Found duplicate classes:")
//            duplicates.forEach { (className, jars) ->
//                println(" - $className in:")
//                jars.forEach { jar ->
//                    val source = jarToModuleMap[jar] ?: identifyJar(jar)
//                    println("     → $jar   [source: $source]")
//                }
//            }
//        } else {
//            println("✅ No duplicate classes found")
//        }
//    }
//
//    /**
//     * Attempts to map JAR file paths to local Gradle module names.
//     */
//    protected fun buildJarToModuleMap(): Map<File, String> {
//        val map = mutableMapOf<File, String>()
//        project.rootProject.subprojects.forEach { sub ->
//            val pathPrefix = File(sub.buildDir, "intermediates/runtime_library_classes_jar")
//            if (pathPrefix.exists()) {
//                pathPrefix.walkTopDown().forEach { f ->
//                    if (f.name == "classes.jar") {
//                        map[f] = sub.path
//                    }
//                }
//            }
//        }
//        return map
//    }
//
//    /**
//     * Fallback classifier for external jars (usually from Gradle caches)
//     */
//    protected fun identifyJar(jar: File): String {
//        return if (jar.absolutePath.contains(".gradle/caches/")) {
//            val fileName = jar.name
//            val parent = jar.parentFile?.name
//            "$parent/$fileName".replace("jetified-", "")
//        } else {
//            "unknown"
//        }
//    }
//}
//
//class DuplicateClassesPlugin : Plugin<Project> {
//    override fun apply(project: Project) {
//        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
//        androidComponents.onVariants(androidComponents.selector().all()) { variant ->
//            val taskProvider: TaskProvider<PrintClassesJars> = project.tasks.register(
//                "${variant.name}PrintDuplicateClasses", PrintClassesJars::class.java
//            )
//            variant.artifacts.forScope(ScopedArtifacts.Scope.ALL)
//                .use(taskProvider)
//                .toGet(
//                    ScopedArtifact.ALL_JARS,
//                    PrintClassesJars::getAllJars
//                )
//            variant.artifacts.forScope(ScopedArtifacts.Scope.ALL)
//                .use(taskProvider)
//                .toGet(
//                    ScopedArtifact.ALL_CLASSES_DIRS,
//                    PrintClassesJars::getAllDirectories
//                )
//        }
//    }
//}

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.Variant
import com.simtop.billionbeers.plugin.com.simtop.billionbeers.buildlogic.PrintClassesJars
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.*
import java.util.*


class PrintDuplicateClassesPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.withPlugin("com.android.application") {
            val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class)

            androidComponents.onVariants(androidComponents.selector().all()) { variant: Variant ->
                val taskProvider = project.tasks.register<PrintClassesJars>("${variant.name}PrintDuplicateClasses")
                variant.artifacts.forScope(ScopedArtifacts.Scope.ALL).use(taskProvider)
                    .toGet(
                        ScopedArtifact.CLASSES,
                        PrintClassesJars::allJars,
                        PrintClassesJars::allDirectories
                    )
            }
        }
    }
}