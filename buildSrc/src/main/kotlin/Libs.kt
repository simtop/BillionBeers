import kotlin.String

/**
 * Generated by https://github.com/jmfayard/buildSrcVersions
 *
 * Update this file with
 *   `$ ./gradlew buildSrcVersions`
 */
object Libs {
    /**
     * https://github.com/Kotlin/kotlinx.coroutines
     */
    const val kotlinx_coroutines_android: String =
            "org.jetbrains.kotlinx:kotlinx-coroutines-android:" +
            Versions.org_jetbrains_kotlinx_kotlinx_coroutines

    /**
     * https://github.com/Kotlin/kotlinx.coroutines
     */
    const val kotlinx_coroutines_test: String = "org.jetbrains.kotlinx:kotlinx-coroutines-test:" +
            Versions.org_jetbrains_kotlinx_kotlinx_coroutines

    /**
     * https://github.com/square/retrofit
     */
    const val converter_moshi: String = "com.squareup.retrofit2:converter-moshi:" +
            Versions.com_squareup_retrofit2

    /**
     * https://github.com/square/retrofit
     */
    const val retrofit: String = "com.squareup.retrofit2:retrofit:" +
            Versions.com_squareup_retrofit2

    /**
     * https://square.github.io/okhttp/
     */
    const val logging_interceptor: String = "com.squareup.okhttp3:logging-interceptor:" +
            Versions.com_squareup_okhttp3

    /**
     * https://square.github.io/okhttp/
     */
    const val mockwebserver: String = "com.squareup.okhttp3:mockwebserver:" +
            Versions.com_squareup_okhttp3

    /**
     * https://kotlinlang.org/
     */
    const val kotlin_android_extensions: String =
            "org.jetbrains.kotlin:kotlin-android-extensions:" + Versions.org_jetbrains_kotlin

    /**
     * https://kotlinlang.org/
     */
    const val kotlin_android_extensions_runtime: String =
            "org.jetbrains.kotlin:kotlin-android-extensions-runtime:" +
            Versions.org_jetbrains_kotlin

    /**
     * https://kotlinlang.org/
     */
    const val kotlin_annotation_processing_gradle: String =
            "org.jetbrains.kotlin:kotlin-annotation-processing-gradle:" +
            Versions.org_jetbrains_kotlin

    /**
     * https://kotlinlang.org/
     */
    const val kotlin_gradle_plugin: String = "org.jetbrains.kotlin:kotlin-gradle-plugin:" +
            Versions.org_jetbrains_kotlin

    /**
     * https://kotlinlang.org/
     */
    const val kotlin_stdlib_jdk7: String = "org.jetbrains.kotlin:kotlin-stdlib-jdk7:" +
            Versions.org_jetbrains_kotlin

    /**
     * https://developer.android.com/topic/libraries/architecture/index.html
     */
    const val navigation_fragment: String = "androidx.navigation:navigation-fragment:" +
            Versions.androidx_navigation

    /**
     * https://developer.android.com/topic/libraries/architecture/index.html
     */
    const val navigation_fragment_ktx: String = "androidx.navigation:navigation-fragment-ktx:" +
            Versions.androidx_navigation

    /**
     * https://developer.android.com/topic/libraries/architecture/index.html
     */
    const val navigation_safe_args_gradle_plugin: String =
            "androidx.navigation:navigation-safe-args-gradle-plugin:" + Versions.androidx_navigation

    /**
     * https://developer.android.com/topic/libraries/architecture/index.html
     */
    const val navigation_ui: String = "androidx.navigation:navigation-ui:" +
            Versions.androidx_navigation

    /**
     * https://github.com/square/moshi
     */
    const val moshi: String = "com.squareup.moshi:moshi:" + Versions.com_squareup_moshi

    /**
     * https://github.com/square/moshi
     */
    const val moshi_kotlin_codegen: String = "com.squareup.moshi:moshi-kotlin-codegen:" +
            Versions.com_squareup_moshi

    /**
     * https://github.com/google/dagger
     */
    const val dagger: String = "com.google.dagger:dagger:" + Versions.com_google_dagger

    /**
     * https://github.com/google/dagger
     */
    const val dagger_compiler: String = "com.google.dagger:dagger-compiler:" +
            Versions.com_google_dagger

    /**
     * https://junit.org/junit5/
     */
    const val junit_jupiter_api: String = "org.junit.jupiter:junit-jupiter-api:" +
            Versions.org_junit_jupiter

    /**
     * https://junit.org/junit5/
     */
    const val junit_jupiter_params: String = "org.junit.jupiter:junit-jupiter-params:" +
            Versions.org_junit_jupiter

    /**
     * http://mockk.io
     */
    const val mockk: String = "io.mockk:mockk:" + Versions.io_mockk

    /**
     * http://mockk.io
     */
    const val mockk_android: String = "io.mockk:mockk-android:" + Versions.io_mockk

    /**
     * https://developer.android.com/studio
     */
    const val com_android_tools_build_gradle: String = "com.android.tools.build:gradle:" +
            Versions.com_android_tools_build_gradle

    const val de_fayard_buildsrcversions_gradle_plugin: String =
            "de.fayard.buildSrcVersions:de.fayard.buildSrcVersions.gradle.plugin:" +
            Versions.de_fayard_buildsrcversions_gradle_plugin

    /**
     * https://developer.android.com/topic/libraries/architecture/index.html
     */
    const val lifecycle_extensions: String = "androidx.lifecycle:lifecycle-extensions:" +
            Versions.lifecycle_extensions

    /**
     * http://tools.android.com
     */
    const val constraintlayout: String = "androidx.constraintlayout:constraintlayout:" +
            Versions.constraintlayout

    /**
     * https://developer.android.com/topic/libraries/architecture/index.html
     */
    const val core_testing: String = "androidx.arch.core:core-testing:" + Versions.core_testing

    /**
     * https://developer.android.com/jetpack/androidx
     */
    const val fragment_ktx: String = "androidx.fragment:fragment-ktx:" + Versions.fragment_ktx

    /**
     * https://developer.android.com/studio
     */
    const val lint_gradle: String = "com.android.tools.lint:lint-gradle:" + Versions.lint_gradle

    const val viewbinding: String = "androidx.databinding:viewbinding:" + Versions.viewbinding

    /**
     * https://developer.android.com/jetpack/androidx
     */
    const val appcompat: String = "androidx.appcompat:appcompat:" + Versions.appcompat

    /**
     * https://developer.android.com/jetpack/androidx
     */
    const val core_ktx: String = "androidx.core:core-ktx:" + Versions.core_ktx

    /**
     * http://developer.android.com/tools/extras/support-library.html
     */
    const val material: String = "com.google.android.material:material:" + Versions.material

    const val multidex: String = "androidx.multidex:multidex:" + Versions.multidex

    const val kluent: String = "org.amshove.kluent:kluent:" + Versions.kluent

    /**
     * https://developer.android.com/studio
     */
    const val aapt2: String = "com.android.tools.build:aapt2:" + Versions.aapt2

    /**
     * https://github.com/bumptech/glide
     */
    const val glide: String = "com.github.bumptech.glide:glide:" + Versions.glide

    /**
     * http://junit.org
     */
    const val junit: String = "junit:junit:" + Versions.junit

    const val gson: String = "com.squareup.retrofit2:converter-gson:" + Versions.com_squareup_retrofit2

    const val room_runtime = "androidx.room:room-runtime:" + Versions.room
    const val room_ktx = "androidx.room:room-ktx:" + Versions.room
    const val room_compiler = "androidx.room:room-compiler:" + Versions.room

    const val paging = "androidx.paging:paging-runtime:" + Versions.paging

}
