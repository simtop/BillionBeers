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
    add("implementation", libs.billionBeersLibrary("roomRuntime"))
    add("implementation", libs.billionBeersLibrary("roomKtx"))
    add("ksp", libs.billionBeersLibrary("roomCompiler"))
}
