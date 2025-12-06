package com.simtop.presentation_utils.adapters

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.presentation_utils.custom_views.ComposeBeersListItem

class BeersAdapter(private val listener: ((Beer) -> Unit)?) :
  ListAdapter<Beer, ComposeBeersListItemViewHolder>(DIFF_CALLBACK) {

  companion object {
    private val DIFF_CALLBACK =
      object : DiffUtil.ItemCallback<Beer>() {
        override fun areItemsTheSame(oldItem: Beer, newItem: Beer): Boolean {
          return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Beer, newItem: Beer): Boolean = oldItem == newItem
      }
  }

  override fun onBindViewHolder(holder: ComposeBeersListItemViewHolder, position: Int) {
    holder.bind(getItem(position), listener)
  }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ): ComposeBeersListItemViewHolder {
    return ComposeBeersListItemViewHolder(ComposeView(parent.context))
  }
}

class ComposeBeersListItemViewHolder(val view: ComposeView) : RecyclerView.ViewHolder(view) {

  init {
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  fun bind(beer: Beer, listener: ((Beer) -> Unit)?) {
    view.setContent { ComposeBeersListItem(beer, listener) }
  }
}
