package com.simtop.billionbeers.domain.models

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Beer(
    val id: Int,
    val name: String,
    val tagline: String,
    val description: String,
    val imageUrl: String,
    val abv: Double,
    val ibu: Double,
    val foodPairing: List<String>,
    var availability: Boolean = true
) : Parcelable {
    companion object {
        val empty = Beer(
            1,
            "",
            "",
            "",
            "",
            0.0,
            0.0,
            emptyList(),
            true
        )
    }
}