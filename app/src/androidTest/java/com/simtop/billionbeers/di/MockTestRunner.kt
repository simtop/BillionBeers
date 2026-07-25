package com.simtop.billionbeers.di

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.simtop.billionbeers.BillionBeersApplication

// Declared as the instrumentation runner in build-logic; instantiates the real Application class.
class MockTestRunner : AndroidJUnitRunner() {

  override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
    return super.newApplication(cl, BillionBeersApplication::class.java.name, context)
  }
}
