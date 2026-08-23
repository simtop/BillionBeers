plugins {
    id("com.google.devtools.ksp")
}

dependencies {
    add("implementation", this.project(":catalog-annotations"))
    add("ksp", this.project(":catalog-processor"))
}
