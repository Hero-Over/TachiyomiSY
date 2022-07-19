package eu.kanade.domain.manga.repository

import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.MangaUpdate
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import kotlinx.coroutines.flow.Flow

interface MangaRepository {

    suspend fun getMangaById(id: Long): Manga

    suspend fun getMangaByIdAsFlow(id: Long): Flow<Manga>

    suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga?

    suspend fun getFavorites(): List<Manga>

    suspend fun getLibraryManga(): List<LibraryManga>

    fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>>

    fun getFavoritesBySourceId(sourceId: Long): Flow<List<Manga>>

    suspend fun getDuplicateLibraryManga(title: String, sourceId: Long): Manga?

    suspend fun resetViewerFlags(): Boolean

    suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>)

    suspend fun insert(manga: Manga): Long?

    suspend fun update(update: MangaUpdate): Boolean

    suspend fun updateAll(values: List<MangaUpdate>): Boolean

    suspend fun getMangaBySourceId(sourceId: Long): List<Manga>

    suspend fun getAll(): List<Manga>

    suspend fun deleteManga(mangaId: Long)
}
