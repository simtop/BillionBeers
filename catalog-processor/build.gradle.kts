plugins {
    kotlin("jvm")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    api(libs.ksp.api)
    api(libs.kotlinpoet)
    api(libs.kotlinpoet.ksp)
    implementation(kotlin("stdlib"))
}
