package com.simtop.billionbeers.presentation.beerslist.paging

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.paging.LoadState
import com.simtop.billionbeers.core.BaseBindView
import com.simtop.billionbeers.databinding.LoadStateFooterPagedItemBinding

class LoadStateItemView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : BaseBindView<LoadState>(context, attrs, defStyleAttr) {

    private val loadStateFooterPagedItemBinding: LoadStateFooterPagedItemBinding =
        LoadStateFooterPagedItemBinding.inflate(
            LayoutInflater.from(context),
            this,
            true
        )

    override fun bind(loadState: LoadState, retry: (() -> Unit?)?) {
        loadStateFooterPagedItemBinding.retryButton.setOnClickListener { retry?.invoke() }

        if (loadState is LoadState.Error) {
            loadStateFooterPagedItemBinding.errorMsg.text = loadState.error.localizedMessage
        }
        loadStateFooterPagedItemBinding.progressBar.isVisible = loadState is LoadState.Loading
        loadStateFooterPagedItemBinding.retryButton.isVisible = loadState !is LoadState.Loading
        loadStateFooterPagedItemBinding.errorMsg.isVisible = loadState !is LoadState.Loading
    }
}