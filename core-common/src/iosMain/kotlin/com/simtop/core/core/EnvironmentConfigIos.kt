package com.simtop.core.core

import platform.Foundation.NSURLComponents

internal actual fun parseUrl(value: String): ParsedUrl {
  val components = requireNotNull(NSURLComponents(string = value)) {
    "Invalid URL: $value"
  }
  return ParsedUrl(
    scheme = components.scheme,
    host = components.host,
    userInfo = components.user,
    query = components.query,
    fragment = components.fragment,
    path = components.path ?: "",
  )
}
