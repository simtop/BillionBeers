plugins {
    id("android-library-convention")
}

android {
    namespace = "com.example.billionbeers.beerdomain"
}

dependencies {
    implementation(project(":core"))
}
