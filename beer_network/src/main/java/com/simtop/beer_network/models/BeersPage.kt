package com.simtop.beer_network.models

/**
 * Remote paging envelope: the fetched items plus the server's total-count header.
 *
 * [totalCount] is nullable on purpose — the pager must keep working if `X-Total-Count` ever
 * disappears or arrives malformed, falling back to the empty-page probe for end detection.
 */
data class BeersPage(val items: List<BeersApiResponseItem>, val totalCount: Int?)
