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
    add("kspJvm", libs.billionBeersLibrary("roomCompiler"))
    add("kspAndroid", libs.billionBeersLibrary("roomCompiler"))
    add("kspIosArm64", libs.billionBeersLibrary("roomCompiler"))
    add("kspIosSimulatorArm64", libs.billionBeersLibrary("roomCompiler"))
}
