package com.simtop.billionbeers.data.models


import com.google.gson.annotations.SerializedName
import com.simtop.billionbeers.data.models.Amount

data class Hop(
    @SerializedName("add")
    val add: String?,
    @SerializedName("amount")
    val amount: Amount?,
    @SerializedName("attribute")
    val attribute: String?,
    @SerializedName("name")
    val name: String?
)