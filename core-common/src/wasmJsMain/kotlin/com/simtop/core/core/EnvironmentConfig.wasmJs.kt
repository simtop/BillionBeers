package com.simtop.core.core

internal actual fun parseUrl(value: String): ParsedUrl {
  require(value.none { it.isWhitespace() }) { "Invalid URL: $value" }

  val schemeSeparator = value.indexOf("://")
  require(schemeSeparator > 0) { "Invalid URL: $value" }

  val scheme = value.substring(0, schemeSeparator)
  require(scheme.first().isLetter() && scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }) {
    "Invalid URL: $value"
  }

  val authorityStart = schemeSeparator + 3
  require(authorityStart < value.length) { "Invalid URL: $value" }
  val pathStart = value.indexOfAny(charArrayOf('/', '?', '#'), authorityStart)
  val authorityEnd = if (pathStart >= 0) pathStart else value.length
  val authority = value.substring(authorityStart, authorityEnd)
  require(authority.isNotEmpty()) { "Invalid URL: $value" }

  val userInfoSeparator = authority.lastIndexOf('@')
  val userInfo = if (userInfoSeparator >= 0) authority.substring(0, userInfoSeparator) else null
  val hostPort = if (userInfoSeparator >= 0) authority.substring(userInfoSeparator + 1) else authority
  require(hostPort.isNotEmpty()) { "Invalid URL: $value" }

  val host = when {
    hostPort.startsWith('[') -> {
      val closingBracket = hostPort.indexOf(']')
      require(closingBracket > 1) { "Invalid URL: $value" }
      hostPort.substring(1, closingBracket)
    }
    else -> {
      val colonCount = hostPort.count { it == ':' }
      when {
        colonCount == 0 -> hostPort
        colonCount == 1 -> hostPort.substringBefore(':').takeIf {
          it.isNotEmpty() && hostPort.substringAfter(':').all(Char::isDigit)
        }
        else -> null
      }
    }
  }

  val remainder = if (pathStart >= 0) value.substring(pathStart) else ""
  val queryStart = remainder.indexOf('?')
  val fragmentStart = remainder.indexOf('#')
  val pathEnd = listOf(queryStart, fragmentStart).filter { it >= 0 }.minOrNull() ?: remainder.length
  val path = remainder.substring(0, pathEnd)
  val query = if (queryStart >= 0) {
    remainder.substring(queryStart + 1, if (fragmentStart > queryStart) fragmentStart else remainder.length)
  } else {
    null
  }
  val fragment = if (fragmentStart >= 0) remainder.substring(fragmentStart + 1) else null

  return ParsedUrl(
    scheme = scheme,
    host = host,
    userInfo = userInfo,
    query = query,
    fragment = fragment,
    path = path,
  )
}
