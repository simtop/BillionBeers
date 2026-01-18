package com.simtop.billionbeers.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme
import com.google.android.play.core.splitinstall.SplitInstallManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  @Inject lateinit var splitInstallManager: SplitInstallManager

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { BillionBeersTheme { AppNavigation(splitInstallManager = splitInstallManager) } }
  }
}
