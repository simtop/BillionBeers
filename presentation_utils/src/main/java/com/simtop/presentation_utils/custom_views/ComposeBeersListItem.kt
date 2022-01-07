package com.simtop.presentation_utils.custom_views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.presentation_utils.core.noRippleClickable

@Composable
fun ComposeBeersListItem(
    beer: Beer,
    onClick: ((Beer) -> Unit)? = null
) {
    val backgroundColor = remember {
        if (beer.availability) {
            Color.Yellow
        } else {
            Color.Magenta
        }
    }

    Card(
        modifier = Modifier
            .padding(8.dp)
            .noRippleClickable { onClick?.invoke(beer) }, elevation = 3.dp,
        backgroundColor = backgroundColor,
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(5.dp, Color.Blue)
    ) {
        ComposeTitle(name = beer.name)
    }
}

@Preview
@Composable
fun ComposeBeersListItemPreview() {
    ComposeBeersListItem(Beer.empty)
}