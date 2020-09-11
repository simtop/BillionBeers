package com.simtop.billionbeers.di

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class MockTestRunner: AndroidJUnitRunner(){

    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ) : Application {
        return super.newApplication(cl, TestBaseApplication::class.java.name, context)
    }
}