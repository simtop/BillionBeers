import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
}

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
    namespace = "com.simtop.billionbeers.kmp.fixture"
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
