package com.simtop.core.core

import java.net.URI

internal actual fun parseUrl(value: String): ParsedUrl {
  val uri = URI(value)
  return ParsedUrl(
    scheme = uri.scheme,
    host = uri.host,
    userInfo = uri.userInfo,
    query = uri.query,
    fragment = uri.fragment,
    path = uri.path,
  )
}
