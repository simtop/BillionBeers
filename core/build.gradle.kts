plugins {
    id("android-library-convention") // Apply the library convention plugin
    // org.jetbrains.kotlin.plugin.compose is now part of the convention plugin
}

android {
    namespace = "com.example.billionbeers.core" // Module-specific
    // buildFeatures { compose = true } is now handled by the convention plugin.
    // compileSdk, minSdk, etc., are now handled by the convention plugin.
}

// The androidModule.call(false) lines are removed.

dependencies {
    // Specific dependencies for :core would go here if any.
    // Common dependencies (Hilt, Kotlin stdlib, core-ktx, testing, Compose)
    // are provided by the android-library-convention plugin.
}
