import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("org.jetbrains.kotlin.android")
    id("dev.zacsweers.metro")
}

val libs = the<LibrariesForLibs>()

dependencies {
    add("implementation", libs.metro.runtime)
    add("implementation", libs.metrox.viewmodel)
}

metro {
    enableTopLevelFunctionInjection.set(true)
    generateAssistedFactories.set(true)
}
