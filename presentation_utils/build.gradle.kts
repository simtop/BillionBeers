plugins {
    id("android-library-convention")
}

android {
    namespace = "com.example.billionbeers.presentation_utils"
}

dependencies {
    implementation(project(":beerdomain"))
}
