package com.simtop.presentation_utils.core

import android.content.Context
import android.content.DialogInterface
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

fun <T : Any, L : LiveData<T>> LifecycleOwner.observe(liveData: L, body: (T?) -> Unit) {
  liveData.observe(this, Observer(body))
}

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
  Toast.makeText(this, message, duration).show()
}

fun Context.dialog(
  @StringRes titleRes: Int? = null,
  @StringRes message: Int? = null,
  builder: AlertDialog.Builder.() -> Unit
): AlertDialog.Builder =
  AlertDialog.Builder(this).apply {
    titleRes?.let { setTitle(it) }
    message?.let { setMessage(it) }
    builder()
  }

fun AlertDialog.Builder.positiveButton(
  @StringRes resTitle: Int,
  func: AlertDialog.Builder.() -> Unit
) {
  setPositiveButton(resTitle) { _: DialogInterface?, _: Int -> func() }
}

fun AlertDialog.Builder.negativeButton(
  @StringRes resTitle: Int,
  func: AlertDialog.Builder.() -> Unit
) {
  setNegativeButton(resTitle) { _: DialogInterface?, _: Int -> func() }
}

fun AlertDialog.Builder.setLayout(viewRes: View) {
  setView(viewRes)
}

fun getDialog(context: Context, title: Int? = null, message: Int? = null, layout: View) =
  context.dialog(title, message) { setLayout(layout) }.setCancelable(false).create()
