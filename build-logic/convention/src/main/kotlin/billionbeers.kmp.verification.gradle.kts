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

gradle.projectsEvaluated {
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
}
