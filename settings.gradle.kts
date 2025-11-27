pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

include(":beer_data")
include(":beer_database")
include(":beer_network")
include(":presentation_utils")
include(":core")
include(":core-common")
include(":feature:beerslist")
include(":feature:beerdetail")
include(":beerdomain")
include(":app")
rootProject.name = "BillionBeers"
include(":navigation")
