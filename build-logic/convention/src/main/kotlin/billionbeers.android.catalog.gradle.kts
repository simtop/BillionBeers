plugins {
    id("com.google.devtools.ksp")
}

val catalog = billionBeersCatalog()

dependencies {
    add("implementation", catalog.billionBeersLibrary("vitrine-annotations"))
    add("ksp", catalog.billionBeersLibrary("vitrine-processor"))
}
