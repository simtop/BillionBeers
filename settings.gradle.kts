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
include(":core:designsystem")
include(":feature:beerslist")
include(":feature:beerdetail")
include(":beerdomain:api")
include(":beerdomain:impl")
include(":beerdomain:fakes")
include(":app")
rootProject.name = "BillionBeers"
include(":navigation")

include(":benchmark:microbenchmark")
include(":benchmark:macrobenchmark")
include(":benchmark:baselineprofile")
include(":catalog")
include(":snapshot-testing")
include(":snapshot-processor")
include(":catalog-annotations")
include(":catalog-processor")
