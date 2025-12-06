package com.simtop.beer_network.models

import com.google.gson.annotations.SerializedName

data class ImageResponse(
  @SerializedName("id") val id: String,
  @SerializedName("url") val url: String,
  @SerializedName("filename") val filename: String,
  @SerializedName("extension") val extension: String,
  @SerializedName("width") val width: Int?,
  @SerializedName("height") val height: Int?,
  @SerializedName("mime") val mime: String?,
  @SerializedName("size") val size: Int
)
