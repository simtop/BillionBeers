package com.simtop.billionbeers.presentation.beerslist.paging

import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.simtop.billionbeers.core.BaseBindView
import com.simtop.billionbeers.core.ViewWrapper
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.presentation.beerslist.BeersItemView

class PagedBeersAdapter(
    private val listener: ((Beer) -> Unit)?
) : PagingDataAdapter<Beer, ViewWrapper<BaseBindView<Beer>>>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<Beer>() {
                override fun areItemsTheSame(oldItem: Beer, newItem: Beer): Boolean {
                    return oldItem.id == newItem.id
                }

                override fun areContentsTheSame(oldItem: Beer, newItem: Beer): Boolean =
                    oldItem == newItem
            }
    }

    private fun onCreateItemView(parent: ViewGroup, viewType: Int): BaseBindView<Beer> {
        return BeersItemView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    override fun onBindViewHolder(holder: ViewWrapper<BaseBindView<Beer>>, position: Int) {
        getItem(position)?.let { holder.view.bind(it) }
        holder.view.setOnClickListener {
            getItem(position)?.let { beer -> listener?.invoke(beer) }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewWrapper<BaseBindView<Beer>> =
        ViewWrapper(onCreateItemView(parent, viewType))
}