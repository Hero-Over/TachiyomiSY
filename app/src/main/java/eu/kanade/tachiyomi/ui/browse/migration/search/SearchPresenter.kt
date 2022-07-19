package eu.kanade.tachiyomi.ui.browse.migration.search
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchCardItem
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchItem
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchPresenter

class SearchPresenter(
    initialQuery: String? = "",
    private val manga: Manga,
    sources: List<CatalogueSource>? = null,
) : GlobalSearchPresenter(initialQuery, sourcesToUse = sources) {

    override fun getEnabledSources(): List<CatalogueSource> {
        // Put the source of the selected manga at the top
        return super.getEnabledSources()
            .sortedByDescending { it.id == manga.source }
    }

    override fun createCatalogueSearchItem(source: CatalogueSource, results: List<GlobalSearchCardItem>?): GlobalSearchItem {
        // Set the catalogue search item as highlighted if the source matches that of the selected manga
        return GlobalSearchItem(source, results, source.id == manga.source)
    }
}
