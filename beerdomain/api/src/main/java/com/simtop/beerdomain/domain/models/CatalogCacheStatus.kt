package com.simtop.beerdomain.domain.models

/**
 * How the catalog's cached rows relate to the current language and TTL policy - what the list
 * screen consults on start to decide between doing nothing, a cold load, and a background refresh.
 */
sealed interface CatalogCacheStatus {
  /** No cached rows at all: cold start, full-screen loading. */
  data object Empty : CatalogCacheStatus

  /** Rows + a bookmark for the current language surface, within the TTL: show as-is. */
  data object Fresh : CatalogCacheStatus

  /**
   * Rows exist but are past the TTL - or predate the `paging_state` table entirely (legacy cache
   * with no bookmarks), which makes their age unknowable. Either way: background refresh.
   */
  data object Stale : CatalogCacheStatus

  /**
   * Rows exist but every bookmark belongs to another language's surface: the device language
   * changed since they were fetched. Background refresh re-translates them page by page through the
   * availability-preserving upsert; the resume key restarts from page 1 so load-more re-walks the
   * stale pages instead of skipping them.
   */
  data object LanguageMismatch : CatalogCacheStatus
}
