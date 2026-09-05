package com.simtop.core.core

/**
 * The only analytics events the application is allowed to emit.
 *
 * There is intentionally no free-form event name or parameter map here. In particular, search text,
 * URLs and exception messages have no representation in this contract.
 */
sealed interface AnalyticsEvent {
  val name: Name

  enum class Name {
    APP_OPENED,
    SEARCH_OPENED,
    SEARCH_SUBMITTED,
    BEER_DETAIL_OPENED,
    AVAILABILITY_CHANGED,
  }

  data object AppOpened : AnalyticsEvent {
    override val name = Name.APP_OPENED
  }

  data object SearchOpened : AnalyticsEvent {
    override val name = Name.SEARCH_OPENED
  }

  /** Search text is deliberately absent; only the bounded result count may be reported. */
  data class SearchSubmitted(val resultCount: Int) : AnalyticsEvent {
    init {
      require(resultCount >= 0) { "resultCount must not be negative" }
    }

    override val name = Name.SEARCH_SUBMITTED
  }

  data class BeerDetailOpened(val beerId: CatalogId) : AnalyticsEvent {
    override val name = Name.BEER_DETAIL_OPENED
  }

  data class AvailabilityChanged(val beerId: CatalogId, val available: Boolean) : AnalyticsEvent {
    override val name = Name.AVAILABILITY_CHANGED
  }
}

/** A bounded identifier for a public catalog item, never an arbitrary user or URL string. */
@JvmInline
value class CatalogId private constructor(val value: String) {
  companion object {
    private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,64}")

    /** Returns null for values that could contain a URL, query, fragment or free-form text. */
    fun from(raw: String): CatalogId? = raw.takeIf(SAFE_ID::matches)?.let(::CatalogId)
  }
}

enum class DiagnosticArea {
  DATA_MAPPING,
  NETWORK,
  DATABASE,
  NAVIGATION,
  DYNAMIC_FEATURE,
}

enum class DiagnosticCode {
  MISSING_BEER_ID,
  MISSING_BEER_NAME,
  MISSING_BEER_TRANSLATION,
  MISSING_STYLE_ID_OR_NAME,
  MISSING_BREWERY_ID_OR_NAME,
  NETWORK_UNAVAILABLE,
  HTTP_NOT_FOUND,
  HTTP_FORBIDDEN,
  RATE_LIMITED,
  SERVER_FAILURE,
  LOCAL_STORAGE_FAILURE,
  UNKNOWN,
}

enum class DiagnosticLevel {
  INFO,
  WARNING,
  ERROR,
}

/**
 * A deliberately closed diagnostic vocabulary. It carries no message, throwable, URL or user input,
 * so adapters cannot accidentally forward those values to a logging/crash provider.
 */
data class Diagnostic(
  val area: DiagnosticArea,
  val code: DiagnosticCode,
  val level: DiagnosticLevel,
)

enum class ObservabilityScreen {
  BEERS_LIST,
  BEER_SEARCH,
  BEER_DETAIL,
  BEER_BROWSE,
}

enum class ObservabilityFeature {
  BEER_DETAIL,
  BEER_BROWSE,
}

enum class ObservabilityOperation {
  LOAD_BEERS,
  SEARCH_BEERS,
  LOAD_BEER_DETAIL,
  UPDATE_AVAILABILITY,
  INSTALL_DYNAMIC_FEATURE,
}

/** Only bounded, non-user context may be attached to a crash report. */
data class CrashContext(
  val screen: ObservabilityScreen? = null,
  val feature: ObservabilityFeature? = null,
  val operation: ObservabilityOperation? = null,
)
