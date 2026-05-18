package com.simtop.beer_network.models
 
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
 
@Serializable
data class ImageResponse(
  @SerialName("id") val id: String,
  @SerialName("url") val url: String,
  @SerialName("filename") val filename: String,
  @SerialName("extension") val extension: String,
  @SerialName("width") val width: Int? = null,
  @SerialName("height") val height: Int? = null,
  @SerialName("mime") val mime: String? = null,
  @SerialName("size") val size: Int,
)
