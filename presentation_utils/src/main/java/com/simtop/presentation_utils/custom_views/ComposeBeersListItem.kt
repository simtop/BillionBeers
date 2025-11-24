package com.simtop.presentation_utils.custom_views

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.presentation_utils.R
import com.simtop.presentation_utils.core.noRippleClickable

@Composable
fun ComposeBeersListItem(
    beer: Beer,
    onClick: ((Beer) -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("beer_list_item")
            .noRippleClickable { onClick?.invoke(beer) },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Beer Image
            BeerImage(imageUrl = beer.imageUrl)

            Spacer(modifier = Modifier.width(16.dp))

            // Beer Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = beer.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = beer.tagline,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BeerChip(text = "ABV: ${beer.abv}%", color = Color(0xFFE0F7FA), textColor = Color(0xFF006064))
                    Spacer(modifier = Modifier.width(8.dp))
                    BeerChip(text = "IBU: ${beer.ibu}", color = Color(0xFFFBE9E7), textColor = Color(0xFFBF360C))
                }
            }
        }
    }
}

@Composable
fun BeerImage(imageUrl: String) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.LightGray.copy(alpha = 0.3f))
    ) {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
            },
            update = { imageView ->
                Glide.with(imageView.context)
                    .load(imageUrl)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .placeholder(R.drawable.blue_image)
                    .error(R.drawable.blue_image)
                    .into(imageView)
            },
            modifier = Modifier.matchParentSize()
        )
    }
}

@Composable
fun BeerChip(text: String, color: Color, textColor: Color) {
    Surface(
        color = color,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        )
    }
}

@Preview
@Composable
fun ComposeBeersListItemPreview() {
    ComposeBeersListItem(Beer.empty.copy(
        name = "Buzz",
        tagline = "A Real Bitter Experience.",
        abv = 4.5,
        ibu = 60.0
    ))
}