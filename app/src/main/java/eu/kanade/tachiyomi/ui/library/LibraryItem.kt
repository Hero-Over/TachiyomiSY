package eu.kanade.tachiyomi.ui.library

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.fredporciuncula.flow.preferences.Preference
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFilterable
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.databinding.SourceComfortableGridItemBinding
import eu.kanade.tachiyomi.databinding.SourceCompactGridItemBinding
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LibraryItem(
    val manga: LibraryManga,
    private val shouldSetFromCategory: Preference<Boolean>,
    private val defaultLibraryDisplayMode: Preference<DisplayModeSetting>,
) :
    AbstractFlexibleItem<LibraryHolder<*>>(), IFilterable<String> {

    private val sourceManager: SourceManager = Injekt.get()

    var displayMode: Long = -1
    var downloadCount = -1
    var unreadCount = -1
    var isLocal = false
    var sourceLanguage = ""

    // SY -->
    var startReadingButton = false
    // SY <--

    private fun getDisplayMode(): DisplayModeSetting {
        return if (shouldSetFromCategory.get() && manga.category != 0) {
            DisplayModeSetting.fromFlag(displayMode)
        } else {
            defaultLibraryDisplayMode.get()
        }
    }

    override fun getLayoutRes(): Int {
        return when (getDisplayMode()) {
            DisplayModeSetting.COMPACT_GRID, DisplayModeSetting.COVER_ONLY_GRID -> R.layout.source_compact_grid_item
            DisplayModeSetting.COMFORTABLE_GRID -> R.layout.source_comfortable_grid_item
            DisplayModeSetting.LIST -> R.layout.source_list_item
        }
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): LibraryHolder<*> {
        return when (getDisplayMode()) {
            DisplayModeSetting.COMPACT_GRID -> {
                LibraryCompactGridHolder(SourceCompactGridItemBinding.bind(view), adapter, coverOnly = false)
            }
            DisplayModeSetting.COVER_ONLY_GRID -> {
                LibraryCompactGridHolder(SourceCompactGridItemBinding.bind(view), adapter, coverOnly = true)
            }
            DisplayModeSetting.COMFORTABLE_GRID -> {
                LibraryComfortableGridHolder(SourceComfortableGridItemBinding.bind(view), adapter)
            }
            DisplayModeSetting.LIST -> {
                LibraryListHolder(view, adapter)
            }
        }
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: LibraryHolder<*>,
        position: Int,
        payloads: List<Any?>?,
    ) {
        holder.onSetValues(this)
    }

    // SY -->
    /**
     * Returns true if this item is draggable.
     */
    override fun isDraggable(): Boolean {
        return true
    }

    /**
     * Filters a manga depending on a query.
     *
     * @param constraint the query to apply.
     * @return true if the manga should be included, false otherwise.
     */
    override fun filter(constraint: String): Boolean {
        val sourceName by lazy { sourceManager.getOrStub(manga.source).name }
        val genres by lazy { manga.getGenres() }
        return manga.title.contains(constraint, true) ||
            (manga.author?.contains(constraint, true) ?: false) ||
            (manga.artist?.contains(constraint, true) ?: false) ||
            (manga.description?.contains(constraint, true) ?: false) ||
            if (constraint.contains(",")) {
                constraint.split(",").all { containsSourceOrGenre(it.trim(), sourceName, genres) }
            } else {
                containsSourceOrGenre(constraint, sourceName, genres)
            }
    }

    /**
     * Filters a manga by checking whether the query is the manga's source OR part of
     * the genres of the manga
     * Checking for genre is done only if the query isn't part of the source name.
     *
     * @param query the query to check
     * @param sourceName name of the manga's source
     * @param genres list containing manga's genres
     */
    private fun containsSourceOrGenre(query: String, sourceName: String, genres: List<String>?): Boolean {
        val minus = query.startsWith("-")
        val tag = if (minus) { query.substringAfter("-") } else query
        return when (sourceName.contains(tag, true)) {
            false -> containsGenre(query, genres)
            else -> !minus
        }
    }

    private fun containsGenre(tag: String, genres: List<String>?): Boolean {
        return if (tag.startsWith("-")) {
            genres?.find {
                it.trim().equals(tag.substringAfter("-"), ignoreCase = true)
            } == null
        } else {
            genres?.find {
                it.trim().equals(tag, ignoreCase = true)
            } != null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other is LibraryItem) {
            return manga.id == other.manga.id
        }
        return false
    }

    override fun hashCode(): Int {
        return manga.id!!.hashCode()
    }
}
