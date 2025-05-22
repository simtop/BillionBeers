plugins {
    id("android-library-convention")
}

android {
    namespace = "com.example.billionbeers.beer_network"
}

dependencies {
    implementation(project(":core"))
}
