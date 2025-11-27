plugins {
    `kotlin-dsl`
}

group = "com.simtop.billionbeers.buildlogic"

dependencies {
    implementation(libs.androidToolsBuildGradle)
    implementation(libs.kotlinGradlePlugin)
    implementation(libs.composeCompilerGradlePlugin)
    implementation(libs.roomGradlePlugin)
    implementation(libs.hiltAndroidGradlePlugin)
    implementation(libs.kspGradlePlugin)
    implementation(libs.navigationSafeArgsPlugin)
    implementation(files(files((libs as Any).javaClass.superclass.protectionDomain.codeSource.location)))
}
