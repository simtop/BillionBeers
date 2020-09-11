package com.simtop.billionbeers.di

import com.simtop.billionbeers.BillionBeersApplication

class TestBaseApplication : BillionBeersApplication(){

    override fun buildApiComponent() {
        appComponent = DaggerTestApplicationComponent.factory()
            .create(this)
    }
}