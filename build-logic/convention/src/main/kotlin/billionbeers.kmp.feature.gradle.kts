@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

apply(plugin = "billionbeers.kmp.compose")

val libs = billionBeersCatalog()
val namespaceSuffix =
  project.path
    .split(':')
    .filter(String::isNotEmpty)
    .joinToString(".") { segment ->
      segment
        .lowercase()
        .replace(Regex("[^a-z0-9_]"), "_")
        .let { normalized ->
          if (normalized.firstOrNull()?.isDigit() == true) "_$normalized" else normalized
        }
    }
check(namespaceSuffix.isNotEmpty()) {
  "billionbeers.kmp.feature must be applied to a named subproject"
}

extensions.configure<KotlinMultiplatformExtension> {
  targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach {
    namespace = "com.simtop.billionbeers.$namespaceSuffix"
  }

  wasmJs { binaries.executable() }

  sourceSets.getByName("commonMain").dependencies {
    implementation(project(":beerdomain:api"))
    implementation(project(":core-common"))
    implementation(libs.billionBeersLibrary("lifecycle-viewmodel"))
    implementation(project(":shared:designsystem"))
  }
  sourceSets.getByName("jvmTest").dependencies {
    implementation(kotlin("test"))
    implementation(project(":beerdomain:fakes"))
  }
}

dependencies {
  add("jvmTestImplementation", libs.billionBeersLibrary("coroutinesTest"))
  add("jvmTestImplementation", libs.billionBeersLibrary("turbine"))
  add("jvmTestImplementation", libs.billionBeersBundle("unitTestJunit5"))
  add("jvmTestRuntimeOnly", libs.billionBeersBundle("unitTestJunit5Runtime"))
  add("jvmTestRuntimeOnly", libs.billionBeersLibrary("junit-platform-launcher"))
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
