package com.simtop.beerdomain.domain.models

/**
 * A beer style (the API calls it a typology): "Ale", "IPA (Indian Pale Ale)", ... The API's `name`
 * is language-neutral; only per-language *descriptions* exist and the browse list doesn't render
 * them, so they're deliberately not modeled.
 */
data class BeerStyle(val id: String, val name: String)
