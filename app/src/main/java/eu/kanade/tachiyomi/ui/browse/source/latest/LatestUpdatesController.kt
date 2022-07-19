package eu.kanade.tachiyomi.ui.browse.source.latest

import android.os.Bundle
import android.view.Menu
import androidx.core.os.bundleOf
import eu.kanade.domain.source.model.Source
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter

/**
 * Controller that shows the latest manga from the catalogue. Inherit [BrowseSourceController].
 */
class LatestUpdatesController(bundle: Bundle) : BrowseSourceController(bundle) {

    constructor(source: Source) : this(
        bundleOf(SOURCE_ID_KEY to source.id),
    )

    // SY -->
    constructor(source: CatalogueSource) : this(
        bundleOf(SOURCE_ID_KEY to source.id),
    )
    // SY <--

    override fun createPresenter(): BrowseSourcePresenter {
        return LatestUpdatesPresenter(args.getLong(SOURCE_ID_KEY))
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.action_search).isVisible = false
    }

    override fun initFilterSheet() {
        // No-op: we don't allow filtering in latest
    }
}
