package com.simtop.presentation_utils.core

import android.view.View
import androidx.recyclerview.widget.RecyclerView

class ViewWrapper<out V : View>(val view: V) : RecyclerView.ViewHolder(view)
