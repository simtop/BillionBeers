package com.simtop.feature.beerdetail.presentation

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.presentation_utils.R

@Composable
fun ComposeBeerDetail(
    beer: Beer,
    onBackClick: () -> Unit,
    onToggleAvailability: () -> Unit
) {
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onToggleAvailability,
                containerColor = if (beer.availability) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("toggle_availability")
            ) {
                Text(
                    text = if (beer.availability) "Mark as Empty" else "Refill Barrels",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = paddingValues.calculateBottomPadding() + 16.dp)
                .testTag("detail_scroll_view")
        ) {
            // Header Image with Back Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                BeerDetailImage(imageUrl = beer.imageUrl)
                
                // Gradient Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f)
                                )
                            )
                        )
                )

                // Back Button
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .padding(top = 48.dp, start = 16.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                        .testTag("back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                // Title on Image
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = beer.name,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black,
                                blurRadius = 8f
                            )
                        )
                    )
                    Text(
                        text = beer.tagline,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontStyle = FontStyle.Italic,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    )
                }
            }

            // Content Body
            Column(modifier = Modifier.padding(24.dp)) {
                
                // Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatCard(label = "ABV", value = "${beer.abv}%", color = Color(0xFFE0F7FA), textColor = Color(0xFF006064))
                    StatCard(label = "IBU", value = "${beer.ibu}", color = Color(0xFFFBE9E7), textColor = Color(0xFFBF360C))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Description
                Text(
                    text = "Description",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = beer.description,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Food Pairing
                if (beer.foodPairing.isNotEmpty()) {
                    Text(
                        text = "Food Pairing",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    beer.foodPairing.forEach { pairing ->
                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = pairing,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, color: Color, textColor: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.width(100.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = textColor.copy(alpha = 0.7f)
                )
            )
        }
    }
}

@Composable
fun BeerDetailImage(imageUrl: String) {
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
        modifier = Modifier.fillMaxSize()
    )
}

@Preview
@Composable
fun ComposeBeerDetailPreview() {
    ComposeBeerDetail(
        beer = Beer.empty.copy(
            name = "Buzz",
            tagline = "A Real Bitter Experience.",
            description = "A light, crisp and bitter IPA brewed with English and American hops. A small batch brewed only once.",
            abv = 4.5,
            ibu = 60.0,
            foodPairing = listOf("Spicy chicken tikka masala", "Grilled chicken quesadilla", "Pastrami on rye")
        ),
        onBackClick = {},
        onToggleAvailability = {}
    )
}
