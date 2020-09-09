package com.servicetitan.mviexample.processors

import android.util.Log
import com.servicetitan.mviexample.events.MovieEvent
import com.servicetitan.mviexample.services.api.OmdbApi
import com.servicetitan.mviexample.services.api.OmdbRetrofitApi
import com.servicetitan.mviexample.services.db.OmdbDatabase
import com.servicetitan.mviexample.state.MovieState
import io.reactivex.rxkotlin.addTo
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovieEventProcessor @Inject constructor(
    private var omdbRetrofitApi: OmdbRetrofitApi,
    private var omdbDatabase: OmdbDatabase
): BaseProcessor<MovieEvent, MovieState>() {

    init {
        eventDispatcher
            .doOnError { Log.d("Movie Event Processor", "Movie Event Process Error $it") }
            .subscribe { processEvent(it) }.addTo(disposable)
    }

    private fun processEvent(event: MovieEvent) {
        logEvent(event)
        when(event) {
            is MovieEvent.Request -> {
                GlobalScope.launch {
                    stateDispatcher.onNext(MovieState.Loading)
                    val movies = omdbDatabase.provideDatabase().movieDao().findByQuery(event.payload.searchQuery.value)
                    if(movies.isEmpty()) {
                        eventDispatcher.onNext(MovieEvent.RequestAPI(event.payload))
                    } else {
                        stateDispatcher.onNext(MovieState.Received(movies))
                    }
                }
            }
            is MovieEvent.RequestAPI -> {
                GlobalScope.launch {
                    val movies = omdbRetrofitApi.search(event.payload.searchQuery.value)
                    eventDispatcher.onNext(MovieEvent.SaveDB(movies))
                }
            }
            is MovieEvent.SaveDB -> {
                GlobalScope.launch {
                    omdbDatabase.provideDatabase().movieDao().insert(event.movies)
                    stateDispatcher.onNext(MovieState.Received(event.movies))
                }
            }
        }
    }
}