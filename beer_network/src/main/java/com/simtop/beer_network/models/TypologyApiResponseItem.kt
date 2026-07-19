package com.simtop.beer_network.models

import kotlinx.serialization.Serializable

/**
 * One `/typologies` row. `name` is language-neutral ("Ale", "IPA (Indian Pale Ale)"); the
 * per-language `translations` carry only descriptions, which nothing renders, so they're not
 * modeled.
 */
@Serializable data class TypologyApiResponseItem(val id: String? = null, val name: String? = null)
