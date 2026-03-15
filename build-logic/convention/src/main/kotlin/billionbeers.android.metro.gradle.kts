import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("org.jetbrains.kotlin.android")
    id("dev.zacsweers.metro")
}

val libs = the<LibrariesForLibs>()

dependencies {
    add("implementation", libs.metro.runtime)
    add("implementation", libs.metro.interop.dagger)
    add("implementation", libs.javax.inject)
}

metro {
    // Enable Dagger interop if we want to support javax.inject.Provider / Lazy
    interop {
        includeDagger()
    }
}
