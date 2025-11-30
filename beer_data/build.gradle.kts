plugins {
    id("billionbeers.android.library")
    id("billionbeers.android.hilt")
}

android {
    namespace = "com.simtop.beer_data"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":beerdomain"))
    implementation(project(":beer_database"))
    implementation(project(":beer_network"))
    
    //implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)
}
