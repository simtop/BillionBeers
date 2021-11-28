package com.simtop.billionbeers.testing_utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer


class TestObserver<T> : Observer<T> {

    val observedValues = mutableListOf<T?>()

    override fun onChanged(value: T?) {
        observedValues.add(value)
    }

    fun clear() {
        observedValues.clear()
    }

}

fun <T> LiveData<T>.testObserver() = TestObserver<T>().also {
    it.clear()
    observeForever(it)
}