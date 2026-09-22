package com.simtop.billionbeers.iosshared

import com.simtop.beerdomain.domain.repositories.BeersRepository
import com.simtop.navigation.contract.PortableRoute
import platform.Foundation.NSURL

internal suspend fun resolveIosDeepLink(
  urlString: String,
  repository: BeersRepository,
): PortableRoute? {
  val url = NSURL(string = urlString) ?: return null
  val pathSegments =
    url.pathComponents
      ?.mapNotNull { component -> component as? String }
      ?.filter { it != "/" }
      .orEmpty()
  return resolveIosDeepLink(url.scheme, url.host, pathSegments, repository)
}
