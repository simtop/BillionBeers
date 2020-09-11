package com.simtop.billionbeers

import android.app.Activity
import android.app.Application
import androidx.fragment.app.Fragment
import com.simtop.billionbeers.di.ApplicationComponent
import com.simtop.billionbeers.di.DaggerApplicationComponent

open class BillionBeersApplication : Application() {

    lateinit var appComponent: ApplicationComponent

    override fun onCreate() {
        super.onCreate()
        buildApiComponent()
    }

    open fun buildApiComponent() {
        appComponent = DaggerApplicationComponent
            .factory()
            .create(this)
    }
}

val Activity.appComponent get() = (applicationContext as BillionBeersApplication).appComponent
val Fragment.appComponent get() = requireActivity().appComponent