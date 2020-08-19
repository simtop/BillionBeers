package com.simtop.billionbeers.data.models


import com.google.gson.annotations.SerializedName

data class AmountX(
    @SerializedName("unit")
    val unit: String?,
    @SerializedName("value")
    val value: Double?
)