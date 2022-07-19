package eu.kanade.domain.category.interactor

import eu.kanade.domain.category.model.Category
import eu.kanade.domain.category.model.CategoryUpdate
import eu.kanade.domain.category.repository.CategoryRepository
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import logcat.LogPriority

class ReorderCategory(
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await(categoryId: Long, newPosition: Int) = withContext(NonCancellable) await@{
        val categories = categoryRepository.getAll()

        val currentIndex = categories.indexOfFirst { it.id == categoryId }
        if (currentIndex == newPosition) {
            return@await Result.Unchanged
        }

        val reorderedCategories = categories.toMutableList()
        val reorderedCategory = reorderedCategories.removeAt(currentIndex)
        reorderedCategories.add(newPosition, reorderedCategory)

        val updates = reorderedCategories.mapIndexed { index, category ->
            CategoryUpdate(
                id = category.id,
                order = index.toLong(),
            )
        }

        try {
            categoryRepository.updatePartial(updates)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    suspend fun await(category: Category, newPosition: Long): Result =
        await(category.id, newPosition.toInt())

    sealed class Result {
        object Success : Result()
        object Unchanged : Result()
        data class InternalError(val error: Throwable) : Result()
    }
}
