package com.simtop.billionbeers.presentation.beerslist.paging

import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import com.simtop.billionbeers.core.BaseBindView
import com.simtop.billionbeers.core.ViewWrapper

class BeersLoadStateAdapter(
    private val retry: () -> Unit
) : LoadStateAdapter<ViewWrapper<BaseBindView<LoadState>>>() {
    override fun onBindViewHolder(
        holder: ViewWrapper<BaseBindView<LoadState>>,
        loadState: LoadState
    ) {
        holder.view.bind(loadState, retry)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        loadState: LoadState
    ): ViewWrapper<BaseBindView<LoadState>> {
        return ViewWrapper(onCreateItemView(parent))
    }

    private fun onCreateItemView(parent: ViewGroup): BaseBindView<LoadState> {
        return LoadStateItemView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }
}
