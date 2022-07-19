package eu.kanade.domain.category.repository

import eu.kanade.domain.category.model.Category
import eu.kanade.domain.category.model.CategoryUpdate
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {

    suspend fun getAll(): List<Category>

    fun getAllAsFlow(): Flow<List<Category>>

    suspend fun getCategoriesByMangaId(mangaId: Long): List<Category>

    fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>>

    // SY -->
    suspend fun insert(category: Category): Long
    // SY <--

    suspend fun updatePartial(update: CategoryUpdate)

    suspend fun updatePartial(updates: List<CategoryUpdate>)

    suspend fun delete(categoryId: Long)
}
