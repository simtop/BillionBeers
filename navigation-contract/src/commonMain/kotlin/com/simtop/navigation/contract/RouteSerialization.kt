package com.simtop.navigation.contract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val routeJson = Json {
  ignoreUnknownKeys = true
}

fun encodeRoute(route: PortableRoute): String = routeJson.encodeToString(route)

fun decodeRoute(payload: String): PortableRoute {
  val value = routeJson.parseToJsonElement(payload)
  val route = value.jsonObject
  val legacyType = route["type"]?.jsonPrimitive?.content
  val compatibleType =
    when (legacyType) {
      "beers_list" -> "com.simtop.navigation.BeersList"
      "favorites" -> "com.simtop.navigation.Favorites"
      "beers_search" -> "com.simtop.navigation.BeersSearch"
      "saved_filter_presets" -> "com.simtop.navigation.SavedFilterPresets"
      "beer_browse" -> "com.simtop.navigation.BeerBrowse"
      "beer_browse_selection" -> "beer_browse_selection"
      "beer_detail" -> "com.simtop.navigation.BeerDetail"
      else -> legacyType
    }
  val compatibleValue =
    if (compatibleType == legacyType) value
    else {
      buildJsonObject {
        route.forEach { (key, element) ->
          put(key, if (key == "type") JsonPrimitive(compatibleType!!) else element)
        }
      }
    }
  return routeJson.decodeFromJsonElement(PortableRoute.serializer(), compatibleValue)
}
