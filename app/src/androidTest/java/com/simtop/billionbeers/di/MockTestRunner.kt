package com.simtop.billionbeers.di

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class MockTestRunner : AndroidJUnitRunner() {

  override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
    // Keep the target application's class name as a string: resolving a class literal here makes
    // the
    // minified test APK try to load the target class from its own class loader before the target
    // APK
    // is attached. The framework resolves this name against the target package correctly.
    return super.newApplication(cl, "com.simtop.billionbeers.BillionBeersApplication", context)
  }
}
