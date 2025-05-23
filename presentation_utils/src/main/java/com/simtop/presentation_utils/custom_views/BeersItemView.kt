package com.simtop.presentation_utils.custom_views

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.compose.material3.MaterialTheme
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.presentation_utils.core.BaseBindView
import com.simtop.presentation_utils.databinding.RowBeerListBinding

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
        rowBeerListBinding
            .beerName.setContent {
            MaterialTheme {
                ComposeTitle(value.name)
            }
        }
        rowBeerListBinding.beerTagline.text = value.tagline
    }
}