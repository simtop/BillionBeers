import androidx.room.gradle.RoomExtension
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("androidx.room")
    id("com.google.devtools.ksp")
}

val libs = the<LibrariesForLibs>()

configure<RoomExtension> {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    "implementation"(libs.roomRuntime)
    "implementation"(libs.roomKtx)
    "ksp"(libs.roomCompiler)
}
