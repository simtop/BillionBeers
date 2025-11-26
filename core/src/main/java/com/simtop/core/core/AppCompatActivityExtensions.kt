package com.simtop.core.core

import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

fun AppCompatActivity.enableBillionBeersEdgeToEdge() {
    val navBarColor = getColor(com.google.android.material.R.color.design_default_color_primary)

    val navigationBarStyle = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.P -> {
            SystemBarStyle.dark(navBarColor)
        }
        else -> {
            SystemBarStyle.auto(navBarColor, navBarColor)
        }
    }

    enableEdgeToEdge(navigationBarStyle = navigationBarStyle)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }
}