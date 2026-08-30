
val libs = billionBeersCatalog()

pluginManager.withPlugin("com.android.base") {
    pluginManager.apply("dev.zacsweers.metro")
    
    dependencies {
        add("implementation", libs.billionBeersLibrary("metro-runtime"))
        add("implementation", libs.billionBeersLibrary("metrox-viewmodel"))
    }

    extensions.getByType<dev.zacsweers.metro.gradle.MetroPluginExtension>().apply {
        enableTopLevelFunctionInjection.set(true)
        generateAssistedFactories.set(true)
    }
}
