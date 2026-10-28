package com.simtop.billionbeers.shared.presentation

/** Welcome and hint copy for hosts without Android resources. */
data class OnboardingStrings(
  val headline: String,
  val body: String,
  val explore: String,
  val reassurance: String,
  val collectionHeadline: String,
  val collectionBody: String,
  val next: String,
  val back: String,
  val skip: String,
  val pagePosition: String,
  val favoriteHint: String,
  val gotIt: String,
) {
  fun pageDescription(index: Int, count: Int): String =
    pagePosition.replace("%1\$d", (index + 1).toString()).replace("%2\$d", count.toString())

  companion object {
    fun forLanguage(languageCode: String): OnboardingStrings =
      when (languageCode.substringBefore("-").substringBefore("_").lowercase()) {
        "es" ->
          OnboardingStrings(
            headline = "Encuentra tu próxima favorita.",
            body = "Descubre cervezas y explora sus estilos. Encuentra una que te encante.",
            explore = "Explorar cervezas",
            reassurance = "Sin crear una cuenta. Tus favoritas se guardan en este dispositivo.",
            collectionHeadline = "Crea tu propia colección.",
            collectionBody =
              "Toca el corazón para guardar una cerveza. Vuelve a tus favoritas cuando quieras.",
            next = "Siguiente",
            back = "Atrás",
            skip = "Omitir",
            pagePosition = "Página %1\$d de %2\$d",
            favoriteHint = "¿Te gusta esta cerveza? Toca el corazón para guardarla en Favoritos.",
            gotIt = "Entendido",
          )
        "fr" ->
          OnboardingStrings(
            headline = "Trouvez votre prochain coup de cœur.",
            body = "Découvrez les bières et leurs styles. Trouvez celle qui vous plaira.",
            explore = "Explorer les bières",
            reassurance = "Sans créer de compte. Vos favoris restent sur cet appareil.",
            collectionHeadline = "Créez votre collection.",
            collectionBody =
              "Touchez le cœur pour enregistrer une bière. Retrouvez vos favoris quand vous le souhaitez.",
            next = "Suivant",
            back = "Retour",
            skip = "Passer",
            pagePosition = "Page %1\$d sur %2\$d",
            favoriteHint =
              "Cette bière vous plaît ? Touchez le cœur pour la garder dans vos favoris.",
            gotIt = "Compris",
          )
        else ->
          OnboardingStrings(
            headline = "Find your next favorite.",
            body = "Discover beers and explore their styles. Find something you’ll love.",
            explore = "Explore beers",
            reassurance = "No account needed. Favorites stay on this device.",
            collectionHeadline = "Build your own collection.",
            collectionBody =
              "Tap the heart to save a beer. Come back to your favorites whenever you like.",
            next = "Next",
            back = "Back",
            skip = "Skip",
            pagePosition = "Page %1\$d of %2\$d",
            favoriteHint = "Like this one? Tap the heart to keep it in Favorites.",
            gotIt = "Got it",
          )
      }
  }
}
