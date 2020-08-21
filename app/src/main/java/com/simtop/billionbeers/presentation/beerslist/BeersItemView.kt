package com.simtop.billionbeers.presentation.beerslist

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import com.simtop.billionbeers.core.BaseBindView
import com.simtop.billionbeers.databinding.RowBeerListBinding
import com.simtop.billionbeers.domain.models.Beer

class BeersItemView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : BaseBindView<Beer>(context, attrs, defStyleAttr) {

    private val rowBeerListBinding: RowBeerListBinding =
        RowBeerListBinding.inflate(
            LayoutInflater.from(context),
            this,
            true
        )

    init {
        val beer: Beer
        if (isInEditMode) {
            beer = Beer.empty
            bind(beer)
        }
    }

    override fun bind(beer: Beer) {
        var test = ""
        if(beer.availability) test = "  av"
        rowBeerListBinding.textId.text = beer.id.toString() + test
    }
}