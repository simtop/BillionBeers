import org.gradle.api.JavaVersion

// The stock runner is the right default for every module: a custom runner exists to swap the Metro
// application graph, and only :app has one. :app opts *up* to its own MockTestRunner in its build
// script. Naming an :app-owned class here made every other module's test APK reference a class it
// does not contain, failing on device with ClassNotFoundException.
val PROJECT_TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner"
val PROJECT_JAVA_VERSION = JavaVersion.VERSION_23
val DETEKT_JAVA_VERSION = "22"
val PROJECT_JACOCO_VERSION = "0.8.13"
val PROJECT_VERSION_CODE = 67
val PROJECT_VERSION_NAME = "67"