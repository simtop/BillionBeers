plugins {
    id("android-library-convention")
}

android {
    namespace = "com.example.billionbeers.beer_database"
}

dependencies {
    implementation(project(":core"))
}
