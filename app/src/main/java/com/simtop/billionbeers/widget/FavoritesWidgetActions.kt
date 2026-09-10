package com.simtop.billionbeers.widget

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.simtop.billionbeers.presentation.MainActivity

private fun navigationIntent(context: Context, uri: String): Intent =
  Intent(Intent.ACTION_VIEW, uri.toUri())
    .setClass(context, MainActivity::class.java)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

class OpenFavoritesAction : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    context.startActivity(navigationIntent(context, "billionbeers://favorites"))
  }
}

class OpenFavoriteBeerAction : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    val beerId = parameters[BEER_ID] ?: return
    context.startActivity(navigationIntent(context, "billionbeers://beers/$beerId"))
  }

  companion object {
    val BEER_ID = ActionParameters.Key<String>("beer_id")
  }
}
