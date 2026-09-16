import androidx.room.gradle.RoomExtension

plugins {
    id("androidx.room")
    id("com.google.devtools.ksp")
}

val libs = billionBeersCatalog()

configure<RoomExtension> {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("commonMainImplementation", libs.billionBeersLibrary("roomRuntime"))
    add("kspCommonMainMetadata", libs.billionBeersLibrary("roomCompiler"))
    add("kspJvm", libs.billionBeersLibrary("roomCompiler"))
    add("kspAndroid", libs.billionBeersLibrary("roomCompiler"))
}
