package eu.kanade.tachiyomi.ui.browse.migration.manga

import android.os.Bundle
import eu.kanade.domain.manga.interactor.GetFavorites
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrationMangaPresenter(
    private val sourceId: Long,
    private val getFavorites: GetFavorites = Injekt.get(),
) : BasePresenter<MigrationMangaController>() {
    private val _state: MutableStateFlow<MigrateMangaState> = MutableStateFlow(MigrateMangaState.Loading)
    val state: StateFlow<MigrateMangaState> = _state.asStateFlow()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        presenterScope.launchIO {
            getFavorites
                .subscribe(sourceId)
                .catch { exception ->
                    _state.value = MigrateMangaState.Error(exception)
                }
                .map { list ->
                    list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                }
                .collectLatest { sortedList ->
                    _state.value = MigrateMangaState.Success(sortedList)
                }
        }
    }
}

sealed class MigrateMangaState {
    object Loading : MigrateMangaState()
    data class Error(val error: Throwable) : MigrateMangaState()
    data class Success(val list: List<Manga>) : MigrateMangaState()
}
