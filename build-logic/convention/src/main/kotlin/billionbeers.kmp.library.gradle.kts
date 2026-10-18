import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
}

apply(plugin = "billionbeers.spotless")
apply(plugin = "billionbeers.detekt")

afterEvaluate {
  val extraProperties = extensions.extraProperties
  val excludedTasks =
    if (extraProperties.has("kmpVerificationExcludedTasks")) {
      extraProperties.get("kmpVerificationExcludedTasks") as Set<*>
    } else {
      emptySet<String>()
    }
  tasks.named("check") {
    dependsOn(
      tasks.matching {
        it.name in setOf("jvmTest", "testAndroidHostTest", "wasmJsBrowserTest") &&
          it.name !in excludedTasks
      }
    )
  }
}

apply(plugin = "billionbeers.jacoco")

val libs = billionBeersCatalog()
val namespaceSuffix =
  (project.path.split(':').filter(String::isNotEmpty).ifEmpty { listOf(project.name) })
    .joinToString(".") { segment ->
      segment
        .lowercase()
        .replace(Regex("[^a-z0-9_]"), "_")
        .let { normalized ->
          if (normalized.firstOrNull()?.isDigit() == true) "_$normalized" else normalized
        }
    }

kotlin {
  sourceSets.commonMain.dependencies {
    implementation(libs.billionBeersLibrary("metro-runtime"))
  }

  jvm {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_23)
  }

  iosArm64()
  iosSimulatorArm64()
  applyDefaultHierarchyTemplate()

  android {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_23)
    namespace = "com.simtop.billionbeers.kmp.$namespaceSuffix"
    compileSdk = 37
    minSdk = 28
    withHostTest {}
  }

  sourceSets {
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}
