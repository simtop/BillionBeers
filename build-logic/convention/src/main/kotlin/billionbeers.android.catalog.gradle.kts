plugins {
    id("com.google.devtools.ksp")
}

dependencies {
    add("implementation", project(":catalog-annotations"))
    add("ksp", project(":catalog-processor"))
}
