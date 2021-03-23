package com.simtop.presentation_utils.custom_views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.Glide
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.presentation_utils.R
import com.simtop.presentation_utils.databinding.SingleBeerItemBinding

class SingleBeerItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val singleBeerItemBinding: SingleBeerItemBinding =
        SingleBeerItemBinding.inflate(
            LayoutInflater.from(context),
            this,
            true
        )

    fun bind(
        beer: Beer,
        onClick: (() -> Unit)? = null,
        onBack: (() -> Unit)? = null
    ) {
        singleBeerItemBinding.apply {
            singleBeerName.text = beer.name
            title.text = beer.name
            beerDescription.text = beer.description
            beerAbv.text = context.getString(R.string.abv, beer.abv.toString())
            beerIbu.text = context.getString(R.string.ibu, beer.ibu.toString())
            foodPairing.text = beer.foodPairing.toString()
            toggleAvailability.setOnClickListener { onClick?.invoke() }
            if (beer.availability) {
                toggleAvailability.text = context.getString(R.string.empty_barrels)
                emergencyText.visibility = View.GONE
            } else {
                toggleAvailability.text = context.getString(R.string.refill_barrels)
                emergencyText.visibility = View.VISIBLE
            }
            home.setOnClickListener { onBack?.invoke() }
        }

        if (beer.imageUrl.isNotEmpty()) {
            Glide.with(context)
                .load(beer.imageUrl)
                .error(R.drawable.blue_image)
                .into(singleBeerItemBinding.beerImage)
        }
    }
}