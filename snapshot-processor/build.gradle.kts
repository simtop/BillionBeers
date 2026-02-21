plugins {
    kotlin("jvm")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17)) // Standardize on 17 for processors
    }
}

dependencies {
    api(libs.ksp.api)
    api(libs.kotlinpoet)
    api(libs.kotlinpoet.ksp)
    implementation(kotlin("stdlib"))
}
