package com.simtop.navigation

import android.net.Uri
import com.simtop.navigation.contract.DeepLinkDestination
import com.simtop.navigation.contract.DeepLinkParser

fun Uri.toDeepLinkDestination(): DeepLinkDestination? =
  DeepLinkParser.parse(scheme, host, pathSegments)
