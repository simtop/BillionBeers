package com.simtop.navigation.contract

import kotlinx.serialization.json.Json

private val routeJson = Json {
  ignoreUnknownKeys = true
}

fun encodeRoute(route: PortableRoute): String = routeJson.encodeToString(route)

fun decodeRoute(payload: String): PortableRoute = routeJson.decodeFromString(payload)
