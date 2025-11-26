plugins {
    id("billionbeers.jvm.library")
}

dependencies {
    implementation(libs.coroutinesTest) // For Dispatchers? No, coroutines-core
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("androidx.annotation:annotation:1.7.0")
}
