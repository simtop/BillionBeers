package com.simtop.navigation.contract

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Encodes detail identity only and reads the former whole-beer payload during route migration. */
internal object BeerDetailRouteSerializer : KSerializer<PortableRoute.BeerDetail> {
  override val descriptor: SerialDescriptor =
    buildClassSerialDescriptor("beer_detail") {
      element<String>("beerId")
    }

  override fun serialize(encoder: Encoder, value: PortableRoute.BeerDetail) {
    encoder.encodeStructure(descriptor) { encodeStringElement(descriptor, 0, value.beerId) }
  }

  override fun deserialize(decoder: Decoder): PortableRoute.BeerDetail {
    if (decoder is JsonDecoder) {
      val content = decoder.decodeJsonElement().jsonObject
      val beerId = content["beerId"]?.jsonPrimitive?.content ?: content.legacyBeerId()
      return PortableRoute.BeerDetail(beerId)
    }

    var beerId: String? = null
    decoder.decodeStructure(descriptor) {
      while (true) {
        when (val index = decodeElementIndex(descriptor)) {
          kotlinx.serialization.encoding.CompositeDecoder.DECODE_DONE -> break
          0 -> beerId = decodeStringElement(descriptor, 0)
          else -> error("Unexpected beer detail route field: $index")
        }
      }
    }
    return PortableRoute.BeerDetail(checkNotNull(beerId))
  }

  private fun kotlinx.serialization.json.JsonObject.legacyBeerId(): String =
    getValue("beer").jsonObject.getValue("id").jsonPrimitive.content
}
