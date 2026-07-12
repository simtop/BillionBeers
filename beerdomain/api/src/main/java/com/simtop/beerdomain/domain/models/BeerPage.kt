package com.simtop.beerdomain.domain.models

/**
 * A page of beers fetched from the API plus the server-reported total ([totalCount], from
 * `X-Total-Count`). [totalCount] is nullable: the pager keeps working without it (empty-page
 * probe), and the UI can render "N of [totalCount]" when present.
 */
data class BeerPage(val items: List<Beer>, val totalCount: Int?)
