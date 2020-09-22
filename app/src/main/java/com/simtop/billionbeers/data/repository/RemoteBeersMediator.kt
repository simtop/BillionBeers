package com.simtop.billionbeers.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.simtop.billionbeers.core.intersects
import com.simtop.billionbeers.data.database.BeersDatabase
import com.simtop.billionbeers.data.mappers.BeersMapper
import com.simtop.billionbeers.data.models.BeerDbModel
import com.simtop.billionbeers.data.models.RemoteKeys
import com.simtop.billionbeers.data.remotesources.BeersRemoteSource
import retrofit2.HttpException
import java.io.IOException
import java.io.InvalidObjectException

private const val PAGING_STARTING_INDEX = 1

@ExperimentalPagingApi
class RemoteBeersMediator(
    private val beersRemoteSource: BeersRemoteSource,
    private val repoDatabase: BeersDatabase
) : RemoteMediator<Int, BeerDbModel>() {

    override suspend fun load(loadType: LoadType, state: PagingState<Int, BeerDbModel>): MediatorResult {

        val page = when (loadType) {
            LoadType.REFRESH -> {
                val remoteKeys = getRemoteKeyClosestToCurrentPosition(state)
                remoteKeys?.nextKey?.minus(1) ?: PAGING_STARTING_INDEX
            }
            LoadType.PREPEND -> {
                val remoteKeys = getRemoteKeyForFirstItem(state)
                if (remoteKeys == null) {
                    // The LoadType is PREPEND so some data was loaded before,
                    // so we should have been able to get remote keys
                    // If the remoteKeys are null, then we're an invalid state and we have a bug
                    throw InvalidObjectException("Remote key and the prevKey should not be null")
                }
                // If the previous key is null, then we can't request more data
                val prevKey = remoteKeys.prevKey
                if (prevKey == null) {
                    return MediatorResult.Success(endOfPaginationReached = true)
                }
                remoteKeys.prevKey
            }
            LoadType.APPEND -> {
                val remoteKeys = getRemoteKeyForLastItem(state)
                if (remoteKeys == null || remoteKeys.nextKey == null) {
                    throw InvalidObjectException("Remote key should not be null for $loadType")
                }
                remoteKeys.nextKey
            }

        }

        try {
            val apiResponse = beersRemoteSource.getListOfBeers(page)

            val repos = apiResponse.map { BeersMapper.fromBeersApiResponseItemToBeer(it) }

            val dbBeers = repoDatabase.beersDao().getAllBeers().map { BeersMapper.fromBeerDbModelToBeer(it) }

            val endOfPaginationReached = repos.isEmpty()

            if(dbBeers.intersects(repos)){
                return MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
            }

            repoDatabase.withTransaction {
                // clear all tables in the database
                if (loadType == LoadType.REFRESH) {
                    repoDatabase.remoteKeysDao().clearRemoteKeys()
                    repoDatabase.beersDao().deleteAll()
                }
                val prevKey = if (page == PAGING_STARTING_INDEX) null else page - 1
                val nextKey = if (endOfPaginationReached) null else page + 1
                val keys = repos.map {
                    RemoteKeys(repoId = it.id, prevKey = prevKey, nextKey = nextKey)
                }
                repoDatabase.remoteKeysDao().insertAll(keys)
                repoDatabase.beersDao().insertAll(repos.map { BeersMapper.fromBeerToBeerDbModel(it) })
            }
            return MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        } catch (exception: IOException) {
            return MediatorResult.Error(exception)
        } catch (exception: HttpException) {
            return MediatorResult.Error(exception)
        }
    }

    private suspend fun getRemoteKeyForLastItem(state: PagingState<Int, BeerDbModel>): RemoteKeys? {
        // Get the last page that was retrieved, that contained items.
        // From that last page, get the last item
        return state.pages.lastOrNull() { it.data.isNotEmpty() }?.data?.lastOrNull()
            ?.let { repo ->
                // Get the remote keys of the last item retrieved
                repoDatabase.remoteKeysDao().remoteKeysRepoId(repo.id)
            }
    }

    private suspend fun getRemoteKeyForFirstItem(state: PagingState<Int, BeerDbModel>): RemoteKeys? {
        // Get the first page that was retrieved, that contained items.
        // From that first page, get the first item
        return state.pages.firstOrNull { it.data.isNotEmpty() }?.data?.firstOrNull()
            ?.let { repo ->
                // Get the remote keys of the first items retrieved
                repoDatabase.remoteKeysDao().remoteKeysRepoId(repo.id)
            }
    }

    private suspend fun getRemoteKeyClosestToCurrentPosition(
        state: PagingState<Int, BeerDbModel>
    ): RemoteKeys? {
        // The paging library is trying to load data after the anchor position
        // Get the item closest to the anchor position
        return state.anchorPosition?.let { position ->
            state.closestItemToPosition(position)?.id?.let { repoId ->
                repoDatabase.remoteKeysDao().remoteKeysRepoId(repoId)
            }
        }
    }
}
