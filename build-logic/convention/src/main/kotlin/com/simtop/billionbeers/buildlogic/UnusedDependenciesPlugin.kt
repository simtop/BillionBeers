package com.simtop.billionbeers.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.jar.JarFile

/**
 * Helper to fetch package information from Maven Central API
 */
class MavenCentralPackageFetcher(private val cacheFile: File) {
    
    private val cache = mutableMapOf<String, List<String>>()
    
    init {
        loadCache()
    }
    
    private fun loadCache() {
        if (cacheFile.exists()) {
            cacheFile.readLines().forEach { line ->
                if (line.isNotBlank() && line.contains("=")) {
                    val (dep, packages) = line.split("=", limit = 2)
                    cache[dep] = packages.split(",").filter { it.isNotBlank() }
                }
            }
        }
    }
    
    fun saveCache() {
        cacheFile.parentFile.mkdirs()
        cacheFile.bufferedWriter().use { writer ->
            cache.toSortedMap().forEach { (dep, packages) ->
                writer.write("$dep=${packages.sorted().joinToString(",")}\n")
            }
        }
    }
    
    fun getPackages(groupId: String, artifactId: String, version: String): List<String> {
        val key = "$groupId:$artifactId"
        
        // Check cache first
        cache[key]?.let { return it }
        
        // Try to fetch from Maven Central
        val packages = fetchFromMavenCentral(groupId, artifactId, version)
        
        if (packages.isNotEmpty()) {
            cache[key] = packages
        }
        
        return packages
    }
    
    private fun fetchFromMavenCentral(groupId: String, artifactId: String, version: String): List<String> {
        try {
            // Download the JAR from Maven Central
            val groupPath = groupId.replace('.', '/')
            val jarUrl = "https://repo1.maven.org/maven2/$groupPath/$artifactId/$version/$artifactId-$version.jar"
            
            val tempFile = File.createTempFile("maven-", ".jar")
            tempFile.deleteOnExit()
            
            val connection = URL(jarUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 10000
            connection.instanceFollowRedirects = true
            
            if (connection.responseCode == 200) {
                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Extract packages from the downloaded JAR
                return extractPackagesFromJar(tempFile)
            }
        } catch (e: Exception) {
            // Silently fail - we'll use fallback heuristics
        }
        
        return emptyList()
    }
    
    private fun extractPackagesFromJar(jarFile: File): List<String> {
        val packages = mutableSetOf<String>()
        
        try {
            JarFile(jarFile).use { jar ->
                jar.entries().asSequence()
                    .filter { entry -> 
                        !entry.isDirectory && 
                        entry.name.endsWith(".class") &&
                        !entry.name.startsWith("META-INF") &&
                        !entry.name.contains("$") &&
                        entry.name != "module-info.class" &&
                        !entry.name.startsWith("android/") &&
                        !entry.name.startsWith("java/") &&
                        !entry.name.startsWith("javax/") &&
                        !entry.name.startsWith("kotlin/")
                    }
                    .forEach { entry ->
                        val packagePath = entry.name
                            .substringBeforeLast("/")
                            .replace("/", ".")
                        
                        if (packagePath.isNotEmpty()) {
                            packages.add(packagePath)
                        }
                    }
            }
        } catch (e: Exception) {
            // Ignore
        }
        
        return packages.toList()
    }
}

/**
 * Task that detects unused dependencies by analyzing source code imports
 * Uses Maven Central API to fetch package information dynamically
 */
abstract class DetectUnusedDependenciesTask : DefaultTask() {

    @get:Input
    abstract val moduleName: SetProperty<String>

    @get:InputFiles
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Input
    abstract val declaredDependencies: SetProperty<String>

    @get:Internal
    abstract val cacheFile: RegularFileProperty

    @TaskAction
    fun detectUnusedDependencies() {
        val modName = moduleName.get().firstOrNull() ?: "unknown"
        logger.lifecycle("\n📊 Dependency Analysis for $modName")
        logger.lifecycle("━".repeat(60))

        // Initialize Maven Central fetcher with cache
        val cache = cacheFile.get().asFile
        val fetcher = MavenCentralPackageFetcher(cache)

        // Collect all imports from source files
        val imports = collectImports()
        logger.lifecycle("📝 Found ${imports.size} unique imports in source code")

        // Map dependencies to their packages
        val dependencyPackageMap = mapDependenciesToPackages(fetcher)
        
        // Save updated cache
        fetcher.saveCache()
        logger.lifecycle("💾 Updated package cache: ${cache.absolutePath}")

        // Analyze usage
        val usedDeps = mutableSetOf<String>()
        val unusedDeps = mutableSetOf<String>()

        dependencyPackageMap.forEach { (dep, packages) ->
            val isUsed = packages.any { pkg ->
                imports.any { import -> import.startsWith(pkg) }
            }

            if (isUsed) {
                usedDeps.add(dep)
            } else {
                unusedDeps.add(dep)
            }
        }

        // Report results
        reportResults(usedDeps, unusedDeps, dependencyPackageMap)
    }

    private fun collectImports(): Set<String> {
        val imports = mutableSetOf<String>()
        val importPattern = Regex("""import\s+([\w.]+)(\.\*)?""")

        sourceFiles.files.forEach { file ->
            if (file.extension in listOf("kt", "java")) {
                file.readLines().forEach { line ->
                    importPattern.find(line)?.let { match ->
                        val importPath = match.groupValues[1]
                        imports.add(importPath)
                    }
                }
            }
        }

        return imports
    }

    private fun mapDependenciesToPackages(
        fetcher: MavenCentralPackageFetcher
    ): Map<String, List<String>> {
        val map = mutableMapOf<String, List<String>>()
        var fetchedFromMaven = 0
        var usedFallback = 0

        declaredDependencies.get().forEach { dep ->
            val parts = dep.split(":")
            if (parts.size >= 3) {
                val groupId = parts[0]
                val artifactId = parts[1]
                val version = parts[2]
                
                logger.debug("Resolving packages for $groupId:$artifactId:$version")
                
                // Try Maven Central API
                val packages = fetcher.getPackages(groupId, artifactId, version)
                
                if (packages.isNotEmpty()) {
                    map[dep] = packages
                    fetchedFromMaven++
                } else {
                    // Fallback to heuristic
                    val fallbackPackages = getPackagesForDependencyFallback(dep)
                    if (fallbackPackages.isNotEmpty()) {
                        map[dep] = fallbackPackages
                        usedFallback++
                    }
                }
            }
        }

        logger.lifecycle("📦 Resolved packages: $fetchedFromMaven from cache/Maven, $usedFallback from heuristics")

        return map
    }

    private fun getPackagesForDependencyFallback(dependency: String): List<String> {
        val parts = dependency.split(":")
        if (parts.size >= 2) {
            val groupId = parts[0]
            
            // Project-specific heuristic: Map "BillionBeers" group to "com.simtop" package
            if (groupId.startsWith("BillionBeers")) {
                return listOf(groupId.replace("BillionBeers", "com.simtop"))
            }
            
            // Default heuristic: use group ID as base package
            return listOf(groupId)
        }

        return emptyList()
    }

    private fun reportResults(
        usedDeps: Set<String>,
        unusedDeps: Set<String>,
        packageMap: Map<String, List<String>>
    ) {
        if (usedDeps.isNotEmpty()) {
            logger.lifecycle("\n✅ Used Dependencies (${usedDeps.size}):")
            usedDeps.sorted().forEach { dep ->
                logger.lifecycle("  - $dep")
            }
        }

        if (unusedDeps.isNotEmpty()) {
            logger.lifecycle("\n⚠️  Potentially Unused Dependencies (${unusedDeps.size}):")
            unusedDeps.sorted().forEach { dep ->
                val packages = packageMap[dep] ?: emptyList()
                logger.lifecycle("  - $dep")
                logger.lifecycle("    Package(s): ${packages.joinToString(", ")}")
                logger.lifecycle("    Not found in any imports")
                logger.lifecycle("    💡 Consider removing from build.gradle.kts\n")
            }
        } else {
            logger.lifecycle("\n✅ All declared dependencies are being used!")
        }

        logger.lifecycle("━".repeat(60))
        logger.lifecycle("Summary: ${unusedDeps.size} potentially unused dependencies found\n")
    }
}

/**
 * Plugin that registers the dependency detection task with Maven Central integration
 */
class UnusedDependenciesPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Only apply to Android modules
        project.plugins.withId("com.android.application") {
            registerTask(project)
        }
        project.plugins.withId("com.android.library") {
            registerTask(project)
        }
        project.plugins.withId("com.android.dynamic-feature") {
            registerTask(project)
        }
    }

    private fun registerTask(project: Project) {
        project.afterEvaluate {
            val cacheFileLocation = project.rootProject.layout.buildDirectory.file("dependency-package-cache.txt")

            project.tasks.register(
                "detectUnusedDependencies",
                DetectUnusedDependenciesTask::class.java
            ) {
                group = "verification"
                description = "Detects dependencies declared but not used in source code (uses Maven Central API)"
                
                moduleName.set(setOf(project.path))
                cacheFile.set(cacheFileLocation)

                // Collect source files
                val sources = project.files()
                listOf(
                    "src/main/java",
                    "src/main/kotlin",
                    "src/test/java",
                    "src/test/kotlin",
                    "src/androidTest/java",
                    "src/androidTest/kotlin"
                ).forEach { path ->
                    val dir = project.file(path)
                    if (dir.exists()) {
                        sources.from(project.fileTree(dir))
                    }
                }
                sourceFiles.from(sources)

                // Collect declared dependencies with versions from resolvable configurations
                val dependencies = mutableSetOf<String>()
                listOf(
                    "compileClasspath",
                    "testCompileClasspath",
                    "androidTestCompileClasspath",
                    "debugCompileClasspath",
                    "releaseCompileClasspath",
                    "debugRuntimeClasspath",
                    "releaseRuntimeClasspath"
                ).forEach { configName ->
                    try {
                        val config = project.configurations.findByName(configName)
                        if (config != null && config.isCanBeResolved) {
                            config.resolvedConfiguration.firstLevelModuleDependencies.forEach { dep ->
                                dependencies.add("${dep.moduleGroup}:${dep.moduleName}:${dep.moduleVersion}")
                            }
                        }
                    } catch (e: Exception) {
                        // Configuration might not be resolvable, skip
                        project.logger.debug("Could not resolve $configName: ${e.message}")
                    }
                }

                declaredDependencies.set(dependencies)
            }
        }
    }
}
