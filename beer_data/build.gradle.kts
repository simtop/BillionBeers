plugins {
    id("android-library-convention")
}

android {
    namespace = "com.example.billionbeers.beer_data"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":beerdomain"))
    implementation(project(":beer_database"))
    implementation(project(":beer_network"))
}
