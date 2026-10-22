package com.simtop.billionbeers.core.designsystem.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersColors
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersDarkColors
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersLightColors

/** Android launcher adapter. Layout and actions stay with the widget; appearance lives here. */
private fun color(role: (BillionBeersColors) -> Color) =
  ColorProvider(day = role(BillionBeersLightColors), night = role(BillionBeersDarkColors))

@Composable
fun BrandWidgetContainer(content: @Composable ColumnScope.() -> Unit) {
  Column(
    GlanceModifier.fillMaxWidth()
      .background(color { it.background })
      .cornerRadius(24.dp)
      .padding(16.dp),
    verticalAlignment = Alignment.Vertical.Top,
    content = content,
  )
}

@Composable
fun BrandWidgetText(
  text: String,
  role: BrandWidgetTextRole,
  modifier: GlanceModifier = GlanceModifier,
) {
  Text(
    text,
    modifier,
    style =
      TextStyle(
        color = color { if (role == BrandWidgetTextRole.Title) it.primary else it.onBackground },
        fontSize =
          when (role) {
            BrandWidgetTextRole.Title -> 16.sp
            BrandWidgetTextRole.Body -> 14.sp
            BrandWidgetTextRole.BeerTitle -> 15.sp
          },
        fontWeight = if (role == BrandWidgetTextRole.Body) FontWeight.Normal else FontWeight.Bold,
        fontFamily =
          if (role == BrandWidgetTextRole.Title) FontFamily.Serif else FontFamily.SansSerif,
      ),
  )
}

@Composable
fun BrandWidgetAvailabilityBadge(text: String, available: Boolean) {
  Text(
    text,
    modifier =
      GlanceModifier.padding(top = 3.dp)
        .background(color { if (available) it.availableContainer else it.material.surfaceVariant })
        .cornerRadius(8.dp)
        .padding(horizontal = 6.dp, vertical = 2.dp),
    style =
      TextStyle(
        color = color { if (available) it.onAvailableContainer else it.material.onSurfaceVariant },
        fontSize = 11.sp,
      ),
  )
}

@Composable
fun BrandWidgetImage(provider: ImageProvider, contentDescription: String) =
  Image(
    provider,
    contentDescription,
    modifier = GlanceModifier.size(44.dp).cornerRadius(10.dp),
    contentScale = ContentScale.Fit,
  )
