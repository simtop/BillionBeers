package com.simtop.billionbeers.presentation.beerslist.paging

import androidx.paging.PagingSource
import com.simtop.billionbeers.domain.models.Beer
import com.simtop.billionbeers.domain.usecases.GetBeersFromApiUseCase


private const val GITHUB_STARTING_PAGE_INDEX = 1

class BeersPagingSource(
    private val getBeersFromApiUseCase: GetBeersFromApiUseCase
) : PagingSource<Int, Beer>()  {
    override suspend fun load(
        params: LoadParams<Int>
    ): LoadResult<Int, Beer> {
        return try {
            val nextPageNumber = params.key ?: 1
            val response = getBeersFromApiUseCase.execute(getBeersFromApiUseCase.Params(nextPageNumber))
            response.either(
                {
                    LoadResult.Error(it)
                },
                {
                    LoadResult.Page(
                        data = it,
                        prevKey = if (nextPageNumber == GITHUB_STARTING_PAGE_INDEX) null else nextPageNumber - 1,
                        nextKey = if (it.isEmpty()) null else nextPageNumber + 1
                    )
                })
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}