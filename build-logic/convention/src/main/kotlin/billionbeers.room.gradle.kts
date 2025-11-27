import androidx.room.gradle.RoomExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

plugins {
    id("androidx.room")
    id("com.google.devtools.ksp")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

configure<RoomExtension> {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    "implementation"(libs.findLibrary("roomRuntime").get())
    "implementation"(libs.findLibrary("roomKtx").get())
    "ksp"(libs.findLibrary("roomCompiler").get())
}
