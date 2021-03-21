package com.simtop.feature.beerslist

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import com.simtop.beerdomain.core.BaseBindView
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.feature.beerslist.databinding.RowBeerListBinding

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
        if (isInEditMode) {
            val beer = Beer.empty
            bind(beer)
        }
    }

    override fun bind(value: Beer) {
        if(value.availability) {
            rowBeerListBinding.cardView.setCardBackgroundColor(Color.WHITE)
        } else {
            rowBeerListBinding.cardView.setCardBackgroundColor(Color.GRAY)
        }
        rowBeerListBinding.beerName.text = value.name
        rowBeerListBinding.beerTagline.text = value.tagline
    }
}