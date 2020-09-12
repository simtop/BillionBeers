package com.simtop.billionbeers.core

import android.view.View

fun View.isVisible() = this.visibility == View.VISIBLE

fun View.visible() {
    this.visibility = View.VISIBLE
}

fun View.invisible() {
    this.visibility = View.INVISIBLE
}

fun View.gone() {
    this.visibility = View.GONE
}

fun View.selectVisibility(shouldBeVisible: Boolean) {
    this.visibility = if (shouldBeVisible) View.VISIBLE else View.GONE
}