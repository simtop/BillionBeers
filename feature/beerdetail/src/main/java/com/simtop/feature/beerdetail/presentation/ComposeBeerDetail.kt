package com.simtop.feature.beerdetail.presentation


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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.placeholder
import coil3.request.error
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import com.simtop.beerdomain.domain.models.Beer
import com.simtop.presentation_utils.R

import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeBeerDetail(
    beer: Beer,
    onBackClick: () -> Unit,
    onToggleAvailability: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Box(modifier = Modifier.wrapContentSize()) {

                BeerDetailImage(
                    imageUrl = beer.imageUrl,
                    modifier = Modifier.matchParentSize()
                )

                // Gradient Overlay for text readability
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                )

                LargeTopAppBar(
                    title = {
                        Text(
                            text = beer.name,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    },
                    expandedHeight = 350.dp,
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    scrollBehavior = scrollBehavior
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onToggleAvailability,
                containerColor = if (beer.availability) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("toggle_availability")
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = beer.availability,
                    label = "availability_animation"
                ) { isAvailable ->
                    if (isAvailable) {
                        Text(
                            text = "Mark as Empty",
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "Refill Barrels",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp)
                .testTag("detail_scroll_view")
        ) {
            // Tagline
            Text(
                text = beer.tagline,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

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
fun BeerDetailImage(imageUrl: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageUrl)
            .crossfade(true)
            .placeholder(R.drawable.blue_image)
            .error(R.drawable.blue_image)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize()
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
