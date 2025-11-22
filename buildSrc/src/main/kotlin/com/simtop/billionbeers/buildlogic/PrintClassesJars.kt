package com.simtop.billionbeers.plugin.com.simtop.billionbeers.buildlogic

import org.gradle.api.*
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.*
import java.io.File
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

abstract class PrintClassesJars : DefaultTask() {

    @get:InputFiles
    abstract val allJars: ListProperty<RegularFile>

    @get:InputFiles
    abstract val allDirectories: ListProperty<Directory>

    @TaskAction
    fun findDuplicateClasses() {
        val classToJarMap = mutableMapOf<String, MutableList<File>>().withDefault { mutableListOf() }

        val jarToModuleMap = buildJarToModuleMap()

        allJars.get().forEach { file ->
            ZipFile(file.asFile).use { zip ->
                val entries: Enumeration<out ZipEntry> = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.endsWith(".class")) {
                        classToJarMap.getOrPut(entry.name) { mutableListOf() }.add(file.asFile)
                    }
                }
            }
        }

        val duplicates = classToJarMap.filter { it.value.size > 1 }

        if (duplicates.isNotEmpty()) {
            logger.warn("⚠️ Found duplicate classes:")
            duplicates.forEach { (className, jars) ->
                logger.warn(" - $className in:")
                jars.forEach { jar ->
                    val source = jarToModuleMap[jar] ?: identifyJar(jar)
                    logger.warn("     → $jar   [source: $source]")
                }
            }
        } else {
            logger.lifecycle("✅ No duplicate classes found")
        }
    }

    /**
     * Attempts to map JAR file paths to local Gradle module names.
     */
    private fun buildJarToModuleMap(): Map<File, String> {
        val map = mutableMapOf<File, String>()
        project.rootProject.subprojects.forEach { sub ->
            val pathPrefix = File(sub.buildDir, "intermediates/runtime_library_classes_jar")
            if (pathPrefix.exists()) {
                pathPrefix.walkTopDown().forEach { f ->
                    if (f.name == "classes.jar") {
                        map[f] = sub.path
                    }
                }
            }
        }
        return map
    }

    /**
     * Fallback classifier for external jars (usually from Gradle caches)
     */
    private fun identifyJar(jar: File): String =
        if (jar.absolutePath.contains(".gradle/caches/")) {
            val parent = jar.parentFile?.name ?: "unknown"
            "${parent}/${jar.name}".replace("jetified-", "")
        } else {
            "unknown"
        }
}