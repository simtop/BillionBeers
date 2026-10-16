package com.simtop.navigation

import android.net.Uri
import com.simtop.navigation.contract.DeepLinkParser
import com.simtop.navigation.contract.PortableRoute

fun Uri.toPortableRoute(): PortableRoute? = DeepLinkParser.parse(scheme, host, pathSegments)
