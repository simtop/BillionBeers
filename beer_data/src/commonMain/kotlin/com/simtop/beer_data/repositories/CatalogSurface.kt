package com.simtop.beer_data.repositories

/**
 * The one place the catalog's paged-surface key is built - the factory (bookmark read/write) and
 * the repository (cache-status classification) must agree on it byte for byte. The key is
 * language-scoped so a device language switch reads as a bookmark miss (`LanguageMismatch`) instead
 * of silently serving rows fetched under another language.
 */
internal fun catalogSurface(languageCode: String): String = CATALOG_SURFACE_PREFIX + languageCode

private const val CATALOG_SURFACE_PREFIX = "catalog:"
