package com.simtop.billionbeers.iosshared

import androidx.compose.runtime.Composable
import com.simtop.billionbeers.shared.app.SharedAppChromeStrings
import com.simtop.billionbeers.shared.app.SharedAppFavoritesStrings
import com.simtop.billionbeers.shared.app.SharedAppListStrings
import com.simtop.billionbeers.shared.app.SharedAppSavedFilterActionStrings
import com.simtop.billionbeers.shared.app.SharedAppSavedFiltersStrings
import com.simtop.billionbeers.shared.app.SharedAppSearchStrings
import com.simtop.billionbeers.shared.app.SharedAppStrings
import com.simtop.billionbeers.shared.app.formatCount
import com.simtop.billionbeers.shared.beerbrowse.BrowseStrings
import com.simtop.billionbeers.shared.beerdetail.BeerDetailStrings
import com.simtop.billionbeers.shared.beerdetail.formatServingTemperatureRange

internal object IosLocalizedStrings {
  fun forLanguage(languageCode: String): SharedAppStrings =
    when (iosLanguage(languageCode)) {
      "fr" -> french()
      "es" -> spanish()
      else -> english()
    }

  private fun spanish() =
    SharedAppStrings(
      chrome =
        SharedAppChromeStrings(
          appTitle = "Billion Beers",
          back = "Atrás",
          list = "Catálogo",
          favorites = "Favoritos",
          search = "Buscar",
          browse = "Explorar",
          savedFilters = "Filtros guardados",
          error = "No se pudieron cargar las cervezas",
        ),
      list =
        SharedAppListStrings(
          loadMoreFailed = "No se pudieron cargar más cervezas",
          retry = "Reintentar",
          endOfList = { count ->
            "Fin de la lista · ${formatCount(count, "cerveza", "cervezas")}"
          },
        ),
      favorites =
        SharedAppFavoritesStrings(
          empty = "Aún no tienes cervezas favoritas",
          error = "No se pudieron cargar las cervezas",
        ),
      search =
        SharedAppSearchStrings(
          hint = "Buscar cervezas",
          styleFilter = "Estilos",
          allStyles = "Todos los estilos",
          clearStyle = "Quitar estilo",
          clearFilters = "Borrar filtros",
          prompt = "Escribe al menos dos caracteres para buscar",
          noResults = { term -> "No se encontraron cervezas para «$term»" },
          resultCount = { count -> formatCount(count, "resultado", "resultados") },
          loadMoreFailed = "No se pudieron cargar más cervezas",
          retry = "Reintentar",
          endOfList = { count ->
            "Fin de los resultados · ${formatCount(count, "cerveza", "cervezas")}"
          },
        ),
      savedFilters =
        SharedAppSavedFiltersStrings(
          empty = "Aún no hay filtros guardados",
          loadFailed = "No se pudieron cargar los filtros guardados. Reintentando.",
          name = "Nombre del filtro",
          rename = "Cambiar nombre",
          delete = "Eliminar",
        ),
      savedFilterActions =
        SharedAppSavedFilterActionStrings(
          saveFilter = "Guardar",
          filterPresetLimitReached = "Puedes guardar hasta 10 filtros. Elimina uno antes de guardar otro.",
          saveFilterFailed = "No se pudo guardar el filtro.",
          mutateFilterFailed = "No se pudo modificar este filtro guardado. Inténtalo de nuevo.",
        ),
      browseStrings = BrowseStrings(
        back = "Atrás",
        title = "Explorar",
        stylesTab = "Estilos",
        breweriesTab = "Cervecerías",
        emptyState = "No hay nada que explorar",
        noBeers = "No se encontraron cervezas",
        retry = "Reintentar",
        loadMoreFailed = "No se pudieron cargar más cervezas",
        breweryFounded = { country, year -> "$country · fundada en $year" },
        beersCount = { count -> formatCount(count, "cerveza", "cervezas") },
        endOfList = { count ->
          "Fin de la lista · ${formatCount(count, "cerveza", "cervezas")}"
        },
      ),
      detailStrings = detailStrings(
        back = "Atrás",
        imageDescription = { name -> "Imagen de $name" },
        addToFavorites = "Añadir a favoritos",
        removeFromFavorites = "Quitar de favoritos",
        available = "Disponible",
        outOfStock = "Agotada",
        markAsEmpty = "Marcar como vacío",
        refillBarrels = "Rellenar barriles",
        styleAndBrewery = { style, brewery -> "$style · $brewery" },
        description = "Descripción",
        foodPairing = "Maridaje",
        abv = "Alcohol",
        ibu = "IBU",
        details = "Detalles",
        srm = "SRM",
        released = "Lanzamiento",
        servingTemperature = "Temperatura de servicio",
        servingTemperatureValue = { minTemperature, maxTemperature ->
          formatServingTemperatureRange(minTemperature, maxTemperature)
        },
        fermentation = "Fermentación",
        ingredients = "Ingredientes",
        recommendedGlasses = "Vasos recomendados",
      ),
    )

  private fun english() =
    SharedAppStrings(
      chrome =
        SharedAppChromeStrings(
          appTitle = "Billion Beers",
          back = "Back",
          list = "Catalog",
          favorites = "Favorites",
          search = "Search",
          browse = "Browse",
          savedFilters = "Saved filters",
          error = "Unable to load beers",
        ),
      list =
        SharedAppListStrings(
          loadMoreFailed = "More beers could not be loaded",
          retry = "Retry",
          endOfList = { count -> "End of list · ${formatCount(count, "beer", "beers")}" },
        ),
      favorites = SharedAppFavoritesStrings(empty = "No favorite beers yet", error = "Unable to load beers"),
      search =
        SharedAppSearchStrings(
          hint = "Search beers",
          styleFilter = "Styles",
          allStyles = "All styles",
          clearStyle = "Clear style",
          clearFilters = "Clear filters",
          prompt = "Type at least two characters to search",
          noResults = { term -> "No beers found for \"$term\"" },
          resultCount = { count -> formatCount(count, "result", "results") },
          loadMoreFailed = "More beers could not be loaded",
          retry = "Retry",
          endOfList = { count ->
            "End of results · ${formatCount(count, "beer", "beers")}"
          },
        ),
      savedFilters =
        SharedAppSavedFiltersStrings(
          empty = "No saved filters yet",
          loadFailed = "Could not load saved filters. Retrying.",
          name = "Filter name",
          rename = "Rename",
          delete = "Delete",
        ),
      savedFilterActions =
        SharedAppSavedFilterActionStrings(
          saveFilter = "Save",
          filterPresetLimitReached = "You can save up to 10 filters. Delete one before saving another.",
          saveFilterFailed = "The filter could not be saved.",
          mutateFilterFailed = "This saved filter could not be changed. Try again.",
        ),
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
        beersCount = { count -> formatCount(count, "beer", "beers") },
        endOfList = { count -> "End of list · ${formatCount(count, "beer", "beers")}" },
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
      chrome =
        SharedAppChromeStrings(
          appTitle = "Billion Beers",
          back = "Retour",
          list = "Catalogue",
          favorites = "Favoris",
          search = "Rechercher",
          browse = "Explorer",
          savedFilters = "Filtres enregistrés",
          error = "Impossible de charger les bières",
        ),
      list =
        SharedAppListStrings(
          loadMoreFailed = "Impossible de charger plus de bières",
          retry = "Réessayer",
          endOfList = { count -> "Fin de la liste · ${formatCount(count, "bière", "bières")}" },
        ),
      favorites =
        SharedAppFavoritesStrings(
          empty = "Aucune bière favorite",
          error = "Impossible de charger les bières",
        ),
      search =
        SharedAppSearchStrings(
          hint = "Rechercher des bières",
          styleFilter = "Styles",
          allStyles = "Tous les styles",
          clearStyle = "Effacer le style",
          clearFilters = "Effacer les filtres",
          prompt = "Saisissez au moins deux caractères",
          noResults = { term -> "Aucune bière pour « $term »" },
          resultCount = { count -> formatCount(count, "résultat", "résultats") },
          loadMoreFailed = "Impossible de charger plus de bières",
          retry = "Réessayer",
          endOfList = { count ->
            "Fin des résultats · ${formatCount(count, "bière", "bières")}"
          },
        ),
      savedFilters =
        SharedAppSavedFiltersStrings(
          empty = "Aucun filtre enregistré",
          loadFailed = "Impossible de charger les filtres enregistrés. Nouvelle tentative.",
          name = "Nom du filtre",
          rename = "Renommer",
          delete = "Supprimer",
        ),
      savedFilterActions =
        SharedAppSavedFilterActionStrings(
          saveFilter = "Enregistrer",
          filterPresetLimitReached = "Vous pouvez enregistrer jusqu'à 10 filtres. Supprimez-en un avant d'en ajouter un autre.",
          saveFilterFailed = "Impossible d'enregistrer le filtre.",
          mutateFilterFailed = "Impossible de modifier ce filtre enregistré. Réessayez.",
        ),
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
        beersCount = { count -> formatCount(count, "bière", "bières") },
        endOfList = { count -> "Fin de la liste · ${formatCount(count, "bière", "bières")}" },
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
