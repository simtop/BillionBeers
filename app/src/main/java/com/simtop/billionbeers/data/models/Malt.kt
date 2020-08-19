package com.simtop.billionbeers.data.models


import com.google.gson.annotations.SerializedName
import com.simtop.billionbeers.data.models.AmountX

data class Malt(
    @SerializedName("amount")
    val amount: AmountX?,
    @SerializedName("name")
    val name: String?
)