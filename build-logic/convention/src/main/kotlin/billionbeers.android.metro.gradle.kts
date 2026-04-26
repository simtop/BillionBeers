import org.gradle.accessors.dm.LibrariesForLibs

val libs = the<LibrariesForLibs>()

pluginManager.withPlugin("com.android.base") {
    pluginManager.apply("dev.zacsweers.metro")
    
    dependencies {
        add("implementation", libs.metro.runtime)
        add("implementation", libs.metrox.viewmodel)
    }

    extensions.getByType<dev.zacsweers.metro.gradle.MetroPluginExtension>().apply {
        enableTopLevelFunctionInjection.set(true)
        generateAssistedFactories.set(true)
    }
}
