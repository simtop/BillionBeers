package com.simtop.billionbeers

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

/**
 * Get the current value from a LiveData without needing to register an observer.
 */
fun <T> LiveData<T>.getValueForTest(): T? {
    var value: T? = null
    var observer = Observer<T> {
        value = it
    }
    observeForever(observer)
    removeObserver(observer)
    return value
}