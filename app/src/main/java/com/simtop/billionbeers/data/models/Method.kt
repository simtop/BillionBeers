package com.simtop.billionbeers.data.models


import com.google.gson.annotations.SerializedName
import com.simtop.billionbeers.data.models.Fermentation
import com.simtop.billionbeers.data.models.MashTemp

data class Method(
    @SerializedName("fermentation")
    val fermentation: Fermentation?,
    @SerializedName("mash_temp")
    val mashTemp: List<MashTemp>?,
    @SerializedName("twist")
    val twist: String?
)