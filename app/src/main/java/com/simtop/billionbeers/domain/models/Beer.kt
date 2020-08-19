package com.simtop.billionbeers.domain.models

data class Beer(
    val name: String,
    val tagline: String,
    val description: String,
    val imageUrl: String,
    val abv: Double,
    val ibu: Double,
    val foodPairing : List<String>,
    val availability : Boolean = true
)