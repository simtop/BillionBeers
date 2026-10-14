check(project == rootProject) { "billionbeers.kmp.verification must be applied to the root project" }

val kmpVerificationTasks =
  mapOf(
    "kmpJvmTest" to "jvmTest",
    "kmpMetadata" to "allMetadataJar",
    "kmpAndroidHostTest" to "testAndroidHostTest",
    "kmpBrowserTest" to "wasmJsBrowserTest",
  )

kmpVerificationTasks.keys.forEach { taskName ->
  tasks.register(taskName) {
    group = "verification"
    description = "Runs the $taskName verification tasks across KMP modules."
  }
}

tasks.register("testModule") {
  group = "verification"
  description = "Runs the test tier configured for the project selected by -PtestModule."
}

tasks.register("kmpIosSimulatorTest") {
  group = "verification"
  description = "Runs every configured Kotlin Multiplatform iOS simulator test task."
}

gradle.projectsEvaluated {
  tasks.named("kmpIosSimulatorTest") {
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("iosSimulatorArm64Test") })
  }

  kmpVerificationTasks.forEach { (aggregateName, projectTaskName) ->
    val aggregate = tasks.named(aggregateName)
    subprojects.forEach { subproject ->
      val extraProperties = subproject.extensions.extraProperties
      val excludedTasks =
        if (extraProperties.has("kmpVerificationExcludedTasks")) {
          extraProperties.get("kmpVerificationExcludedTasks") as Set<*>
        } else {
          emptySet<String>()
        }
      if (projectTaskName in excludedTasks) return@forEach

      aggregate.configure {
        dependsOn(subproject.tasks.matching { it.name == projectTaskName })
      }
    }
  }

  tasks.register("kmpTest") {
    group = "verification"
    description = "Runs every configured KMP JVM, metadata, Android-host and browser test tier."
    dependsOn(kmpVerificationTasks.keys.map(tasks::named))
  }

  providers.gradleProperty("testModule").orNull?.let { projectPath ->
    val selectedProject =
      findProject(projectPath)
        ?: error("No Gradle project exists at -PtestModule=$projectPath")
    val selectedTaskNames =
      listOf("jvmTest", "allMetadataJar", "testAndroidHostTest", "wasmJsBrowserTest")
        .firstOrNull { taskName -> selectedProject.tasks.names.contains(taskName) }
        ?.let(::listOf)
        ?: listOf("testDebugUnitTest", "test").firstOrNull(selectedProject.tasks.names::contains)
        ?.let(::listOf)
        ?: error("Project $projectPath does not expose a supported test task")

    tasks.named("testModule") {
      dependsOn(selectedTaskNames.map(selectedProject.tasks::named))
    }
  }
}
