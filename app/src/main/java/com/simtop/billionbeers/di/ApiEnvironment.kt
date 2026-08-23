package com.simtop.billionbeers.di

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** API environments owned by the shipping app rather than by the reusable networking module. */
enum class ApiEnvironment(val displayName: String, val apiBaseUrl: String) {
  PRODUCTION(displayName = "Production", apiBaseUrl = "https://brewbuddy.dev/"),
  STAGING(displayName = "Staging", apiBaseUrl = "https://brewbuddy.dev/"),
}

interface ApiEnvironmentController {
  val selectedEnvironment: StateFlow<ApiEnvironment>

  fun selectEnvironment(environment: ApiEnvironment)
}

/**
 * Persists the debug environment choice across graph recreation and process restarts. Release
 * builds deliberately ignore that preference and always resolve [ApiEnvironment.PRODUCTION].
 */
internal class DefaultApiEnvironmentController(
  context: Context,
  private val debugSelectionEnabled: Boolean,
) : ApiEnvironmentController {

  private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  private val _selectedEnvironment = MutableStateFlow(loadSelectedEnvironment())
  override val selectedEnvironment: StateFlow<ApiEnvironment> = _selectedEnvironment.asStateFlow()

  override fun selectEnvironment(environment: ApiEnvironment) {
    if (!debugSelectionEnabled || environment == _selectedEnvironment.value) return

    preferences.edit().putString(ENVIRONMENT_KEY, environment.name).apply()
    _selectedEnvironment.value = environment
  }

  private fun loadSelectedEnvironment(): ApiEnvironment {
    if (!debugSelectionEnabled) return ApiEnvironment.PRODUCTION

    val storedName = preferences.getString(ENVIRONMENT_KEY, null)
    return ApiEnvironment.entries.firstOrNull { it.name == storedName } ?: ApiEnvironment.PRODUCTION
  }

  private companion object {
    const val PREFERENCES_NAME = "debug_api_environment"
    const val ENVIRONMENT_KEY = "selected_environment"
  }
}
