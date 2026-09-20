import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

@OptIn(ExperimentalWasmDsl::class)
plugins {
  id("billionbeers.kmp.library")
  id("org.jetbrains.compose")
  id("org.jetbrains.kotlin.plugin.compose")
}

apply(plugin = "billionbeers.spotless")

val libs = billionBeersCatalog()

kotlin {
  jvm {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_23)
  }

  wasmJs {
    browser()
  }

  sourceSets {
    commonMain.dependencies {
      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.material3)
      implementation(compose.components.resources)
    }
  }
}
