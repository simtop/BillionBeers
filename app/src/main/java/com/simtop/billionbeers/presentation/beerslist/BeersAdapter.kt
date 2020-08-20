package com.simtop.billionbeers.presentation.beerslist

import android.view.ViewGroup
import com.simtop.billionbeers.core.BaseBindView
import com.simtop.billionbeers.core.BaseRecyclerViewAdapter
import com.simtop.billionbeers.core.ViewWrapper
import com.simtop.billionbeers.domain.models.Beer

class BeersAdapter(
    override var items: MutableList<Beer>,
    val listener: ((Beer) -> Unit)? = null
) : BaseRecyclerViewAdapter<Beer, BaseBindView<Beer>>(items) {
    override fun onCreateItemView(parent: ViewGroup, viewType: Int): BaseBindView<Beer> {
        return BeersItemView(parent.context)
    }

    override fun areItemsTheSame(oldItem: Beer, newItem: Beer): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Beer, newItem: Beer): Boolean {
        return oldItem == newItem
    }

    override fun onBindViewHolder(holder: ViewWrapper<BaseBindView<Beer>>, position: Int) {
        holder.view.bind(items[position])

        holder.view.setOnClickListener {
            listener?.invoke(items[position])
        }
    }
}
