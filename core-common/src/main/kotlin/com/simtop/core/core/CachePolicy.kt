package com.simtop.core.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * How long a cached paged surface counts as fresh. Until Paging 2.0 Phase 4 this was an emergent
 * behavior (cache-first forever); naming it makes "show cached data immediately, refresh in the
 * background once it's older than [staleAfter]" an explicit, testable policy.
 */
data class CachePolicy(val staleAfter: Duration = 24.hours)
