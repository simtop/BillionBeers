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
    add("implementation", libs.roomRuntime)
    add("implementation", libs.roomKtx)
    add("ksp", libs.roomCompiler)
}
