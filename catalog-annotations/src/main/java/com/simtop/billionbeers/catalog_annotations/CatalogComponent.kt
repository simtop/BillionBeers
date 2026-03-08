package com.simtop.billionbeers.catalog_annotations

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class CatalogComponent(
    val tab: String,
    val name: String = "",
    val demoContainer: String = ""
)
