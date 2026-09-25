package com.simtop.billionbeers.iosshared

import androidx.compose.runtime.Composable
import com.simtop.billionbeers.shared.app.SharedAppStrings
import com.simtop.billionbeers.shared.beerbrowse.BrowseStrings
import com.simtop.billionbeers.shared.beerdetail.BeerDetailStrings
import com.simtop.billionbeers.shared.beerdetail.formatServingTemperatureRange

internal object IosLocalizedStrings {
  fun forLanguage(languageCode: String): SharedAppStrings =
    when (iosLanguage(languageCode)) {
      "fr" -> french()
      else -> english()
    }

  private fun english() =
    SharedAppStrings(
      appTitle = "Billion Beers",
      back = "Back",
      list = "Catalog",
      favorites = "Favorites",
      search = "Search",
      browse = "Browse",
      retry = "Retry",
      error = "Unable to load beers",
      listLoadMoreFailed = "More beers could not be loaded",
      listEndOfList = { count -> "End of list · $count beers" },
      searchHint = "Search beers",
      searchPrompt = "Type at least two characters to search",
      searchNoResults = { term -> "No beers found for \"$term\"" },
      searchResultCount = { count -> "$count results" },
      searchEndOfList = { count -> "End of results · $count beers" },
      favoritesEmpty = "No favorite beers yet",
      browseStrings = BrowseStrings(
        back = "Back",
        title = "Browse",
        stylesTab = "Styles",
        breweriesTab = "Breweries",
        emptyState = "Nothing to browse",
        noBeers = "No beers found",
        retry = "Retry",
        loadMoreFailed = "More beers could not be loaded",
        breweryFounded = { country, year -> "$country · founded $year" },
        beersCount = { count -> "$count beers" },
        endOfList = { count -> "End of list · $count beers" },
      ),
      detailStrings = detailStrings(
        back = "Back",
        imageDescription = { name -> "$name image" },
        addToFavorites = "Add to favorites",
        removeFromFavorites = "Remove from favorites",
        available = "Available",
        outOfStock = "Out of stock",
        markAsEmpty = "Mark as empty",
        refillBarrels = "Refill barrels",
        styleAndBrewery = { style, brewery -> "$style · $brewery" },
        description = "Description",
        foodPairing = "Food pairing",
        abv = "ABV",
        ibu = "IBU",
        details = "Details",
        srm = "SRM",
        released = "Released",
        servingTemperature = "Serving temperature",
        servingTemperatureValue = { minTemperature, maxTemperature ->
          formatServingTemperatureRange(minTemperature, maxTemperature)
        },
        fermentation = "Fermentation",
        ingredients = "Ingredients",
        recommendedGlasses = "Recommended glasses",
      ),
    )

  private fun french() =
    SharedAppStrings(
      appTitle = "Billion Beers",
      back = "Retour",
      list = "Catalogue",
      favorites = "Favoris",
      search = "Rechercher",
      browse = "Explorer",
      retry = "Réessayer",
      error = "Impossible de charger les bières",
      listLoadMoreFailed = "Impossible de charger plus de bières",
      listEndOfList = { count -> "Fin de la liste · $count bières" },
      searchHint = "Rechercher des bières",
      searchPrompt = "Saisissez au moins deux caractères",
      searchNoResults = { term -> "Aucune bière pour « $term »" },
      searchResultCount = { count -> "$count résultats" },
      searchEndOfList = { count -> "Fin des résultats · $count bières" },
      favoritesEmpty = "Aucune bière favorite",
      browseStrings = BrowseStrings(
        back = "Retour",
        title = "Explorer",
        stylesTab = "Styles",
        breweriesTab = "Brasseries",
        emptyState = "Rien à explorer",
        noBeers = "Aucune bière trouvée",
        retry = "Réessayer",
        loadMoreFailed = "Impossible de charger plus de bières",
        breweryFounded = { country, year -> "$country · fondée en $year" },
        beersCount = { count -> "$count bières" },
        endOfList = { count -> "Fin de la liste · $count bières" },
      ),
      detailStrings = detailStrings(
        back = "Retour",
        imageDescription = { name -> "Image de $name" },
        addToFavorites = "Ajouter aux favoris",
        removeFromFavorites = "Retirer des favoris",
        available = "Disponible",
        outOfStock = "Rupture de stock",
        markAsEmpty = "Marquer comme vide",
        refillBarrels = "Remplir les fûts",
        styleAndBrewery = { style, brewery -> "$style · $brewery" },
        description = "Description",
        foodPairing = "Accords mets",
        abv = "Alcool",
        ibu = "IBU",
        details = "Détails",
        srm = "SRM",
        released = "Sortie",
        servingTemperature = "Température de service",
        servingTemperatureValue = { minTemperature, maxTemperature ->
          formatServingTemperatureRange(minTemperature, maxTemperature)
        },
        fermentation = "Fermentation",
        ingredients = "Ingrédients",
        recommendedGlasses = "Verres recommandés",
      ),
    )

  private fun detailStrings(
    back: String,
    imageDescription: @Composable (String) -> String,
    addToFavorites: String,
    removeFromFavorites: String,
    available: String,
    outOfStock: String,
    markAsEmpty: String,
    refillBarrels: String,
    styleAndBrewery: @Composable (String, String) -> String,
    description: String,
    foodPairing: String,
    abv: String,
    ibu: String,
    details: String,
    srm: String,
    released: String,
    servingTemperature: String,
    servingTemperatureValue: @Composable (Int, Int) -> String,
    fermentation: String,
    ingredients: String,
    recommendedGlasses: String,
    ) =
    BeerDetailStrings(
      back = back,
      imageDescription = imageDescription,
      addToFavorites = addToFavorites,
      removeFromFavorites = removeFromFavorites,
      available = available,
      outOfStock = outOfStock,
      markAsEmpty = markAsEmpty,
      refillBarrels = refillBarrels,
      styleAndBrewery = styleAndBrewery,
      description = description,
      foodPairing = foodPairing,
      abv = abv,
      ibu = ibu,
      details = details,
      srm = srm,
      released = released,
      servingTemperature = servingTemperature,
      servingTemperatureValue = servingTemperatureValue,
      fermentation = fermentation,
      ingredients = ingredients,
      recommendedGlasses = recommendedGlasses,
    )
}
