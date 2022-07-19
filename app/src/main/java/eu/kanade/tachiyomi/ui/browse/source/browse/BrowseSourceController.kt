package eu.kanade.tachiyomi.ui.browse.source.browse

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fredporciuncula.flow.preferences.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import dev.chrisbanes.insetter.applyInsetter
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.toDbManga
import eu.kanade.domain.source.model.Source
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.SourceControllerBinding
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.LoginSource
import eu.kanade.tachiyomi.ui.base.controller.FabController
import eu.kanade.tachiyomi.ui.base.controller.SearchableNucleusController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.extension.details.SourcePreferencesController
import eu.kanade.tachiyomi.ui.browse.source.SourcesController
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.library.ChangeMangaCategoriesDialog
import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.AddDuplicateMangaDialog
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.ui.more.MoreController
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.preference.asImmediateFlow
import eu.kanade.tachiyomi.util.system.connectivityManager
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.inflate
import eu.kanade.tachiyomi.util.view.shrinkOnScroll
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.widget.AutofitRecyclerView
import eu.kanade.tachiyomi.widget.EmptyView
import eu.kanade.tachiyomi.widget.materialdialogs.QuadStateTextView
import eu.kanade.tachiyomi.widget.materialdialogs.setTextInput
import exh.log.xLogW
import exh.savedsearches.EXHSavedSearch
import exh.source.anyIs
import exh.source.getMainSource
import exh.source.isEhBasedSource
import exh.widget.preference.MangadexLoginDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.injectLazy

open class BrowseSourceController(bundle: Bundle) :
    SearchableNucleusController<SourceControllerBinding, BrowseSourcePresenter>(bundle),
    FabController,
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    FlexibleAdapter.EndlessScrollListener,
    ChangeMangaCategoriesDialog.Listener {

    constructor(
        sourceId: Long,
        query: String? = null,
        // SY -->
        smartSearchConfig: SourcesController.SmartSearchConfig? = null,
        savedSearch: Long? = null,
        filterList: String? = null,
        // SY <--
    ) : this(
        Bundle().apply {
            putLong(SOURCE_ID_KEY, sourceId)
            if (query != null) {
                putString(SEARCH_QUERY_KEY, query)
            }

            // SY -->
            if (smartSearchConfig != null) {
                putParcelable(SMART_SEARCH_CONFIG_KEY, smartSearchConfig)
            }

            if (savedSearch != null) {
                putLong(SAVED_SEARCH_CONFIG_KEY, savedSearch)
            }

            if (filterList != null) {
                putString(FILTERS_CONFIG_KEY, filterList)
            }
            // SY <--
        },
    )

    constructor(
        source: CatalogueSource,
        query: String? = null,
        // SY -->
        smartSearchConfig: SourcesController.SmartSearchConfig? = null,
        savedSearch: Long? = null,
        filterList: String? = null,
        // SY <--
    ) : this(
        source.id,
        query,
        smartSearchConfig,
        savedSearch,
        filterList,
    )

    constructor(
        source: Source,
        query: String? = null,
        // SY -->
        smartSearchConfig: SourcesController.SmartSearchConfig? = null,
        savedSearch: Long? = null,
        filterList: String? = null,
        // SY <--
    ) : this(
        source.id,
        query,
        smartSearchConfig,
        savedSearch,
        filterList,
    )

    private val preferences: PreferencesHelper by injectLazy()

    /**
     * Adapter containing the list of manga from the catalogue.
     */
    /* SY --> */
    protected /* SY <-- */ var adapter: FlexibleAdapter<IFlexible<*>>? = null

    private var actionFab: ExtendedFloatingActionButton? = null
    private var actionFabScrollListener: RecyclerView.OnScrollListener? = null

    /**
     * Snackbar containing an error message when a request fails.
     */
    private var snack: Snackbar? = null

    /**
     * Sheet containing filter items.
     */
    private var filterSheet: SourceFilterSheet? = null

    /**
     * Recycler view with the list of results.
     */
    private var recycler: RecyclerView? = null

    /**
     * Subscription for the number of manga per row.
     */
    private var numColumnsJob: Job? = null

    /**
     * Endless loading item.
     */
    private var progressItem: ProgressItem? = null

    init {
        setHasOptionsMenu(true)
    }

    override fun getTitle(): String? {
        return presenter.source.name
    }

    override fun createPresenter(): BrowseSourcePresenter {
        // SY -->
        return BrowseSourcePresenter(
            args.getLong(SOURCE_ID_KEY),
            args.getString(SEARCH_QUERY_KEY),
            filters = args.getString(FILTERS_CONFIG_KEY),
            savedSearch = args.getLong(SAVED_SEARCH_CONFIG_KEY, 0).takeUnless { it == 0L },
        )
        // SY <--
    }

    override fun createBinding(inflater: LayoutInflater) = SourceControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        // Initialize adapter, scroll listener and recycler views
        adapter = FlexibleAdapter(null, this)
        setupRecycler(view)

        binding.progress.isVisible = true

        // SY -->
        val mainSource = presenter.source.getMainSource<LoginSource>()
        if (mainSource != null && mainSource.requiresLogin && !mainSource.isLogged()) {
            val dialog = MangadexLoginDialog(mainSource)
            dialog.showDialog(router)
        }
        // SY <--
    }

    fun setSavedSearches(savedSearches: List<EXHSavedSearch>) {
        filterSheet?.setSavedSearches(savedSearches)
    }

    open fun initFilterSheet() {
        if (presenter.sourceFilters.isEmpty()) {
            // SY -->
            actionFab?.text = activity!!.getString(R.string.saved_searches)
            // SY <--
        }

        filterSheet = SourceFilterSheet(
            activity!!,
            // SY -->
            this,
            presenter.source,
            emptyList(),
            // SY <--
            onFilterClicked = {
                showProgressBar()
                adapter?.clear()
                presenter.setSourceFilter(presenter.sourceFilters)
            },
            onResetClicked = {
                presenter.appliedFilters = FilterList()
                val newFilters = presenter.source.getFilterList()
                presenter.sourceFilters = newFilters
                filterSheet?.setFilters(presenter.filterItems)
            },
            // EXH -->
            onSaveClicked = {
                viewScope.launchUI {
                    filterSheet?.context?.let {
                        val names = presenter.loadSearches().map { it.name }
                        var searchName = ""
                        MaterialAlertDialogBuilder(it)
                            .setTitle(R.string.save_search)
                            .setTextInput(hint = it.getString(R.string.save_search_hint)) { input ->
                                searchName = input
                            }
                            .setPositiveButton(R.string.action_save) { _, _ ->
                                if (searchName.isNotBlank() && searchName !in names) {
                                    presenter.saveSearch(searchName.trim(), presenter.query, presenter.sourceFilters)
                                } else {
                                    it.toast(R.string.save_search_invalid_name)
                                }
                            }
                            .setNegativeButton(R.string.action_cancel, null)
                            .show()
                    }
                }
            },
            onSavedSearchClicked = { idOfSearch ->
                viewScope.launchUI {
                    val search = presenter.loadSearch(idOfSearch)

                    if (search == null) {
                        filterSheet?.context?.let {
                            MaterialAlertDialogBuilder(it)
                                .setTitle(R.string.save_search_failed_to_load)
                                .setMessage(R.string.save_search_failed_to_load_message)
                                .show()
                        }
                        return@launchUI
                    }

                    if (search.filterList == null) {
                        activity?.toast(R.string.save_search_invalid)
                        return@launchUI
                    }

                    presenter.sourceFilters = FilterList(search.filterList)
                    filterSheet?.setFilters(presenter.filterItems)
                    val allDefault = presenter.sourceFilters == presenter.source.getFilterList()

                    showProgressBar()
                    adapter?.clear()
                    filterSheet?.dismiss()
                    presenter.restartPager(search.query, if (allDefault) FilterList() else presenter.sourceFilters)
                    activity?.invalidateOptionsMenu()
                }
            },
            onSavedSearchDeleteClicked = { idToDelete, name ->
                filterSheet?.context?.let {
                    MaterialAlertDialogBuilder(it)
                        .setTitle(R.string.save_search_delete)
                        .setMessage(it.getString(R.string.save_search_delete_message, name))
                        .setPositiveButton(R.string.action_cancel, null)
                        .setNegativeButton(android.R.string.ok) { _, _ ->
                            presenter.deleteSearch(idToDelete)
                        }
                        .show()
                }
            },
            // EXH <--
        )
        launchUI {
            filterSheet?.setSavedSearches(presenter.loadSearches())
        }
        filterSheet?.setFilters(presenter.filterItems)

        filterSheet?.setOnShowListener { actionFab?.hide() }
        filterSheet?.setOnDismissListener { actionFab?.show() }

        actionFab?.setOnClickListener { filterSheet?.show() }

        actionFab?.show()
    }

    override fun configureFab(fab: ExtendedFloatingActionButton) {
        actionFab = fab

        fab.setText(R.string.action_filter)
        fab.setIconResource(R.drawable.ic_filter_list_24dp)

        // Controlled by initFilterSheet()
        fab.hide()
        initFilterSheet()
    }

    override fun cleanupFab(fab: ExtendedFloatingActionButton) {
        fab.setOnClickListener(null)
        actionFabScrollListener?.let { recycler?.removeOnScrollListener(it) }
        actionFab = null
    }

    override fun onDestroyView(view: View) {
        numColumnsJob?.cancel()
        numColumnsJob = null
        adapter = null
        snack = null
        recycler = null
        super.onDestroyView(view)
    }

    private fun setupRecycler(view: View) {
        numColumnsJob?.cancel()

        var oldPosition = RecyclerView.NO_POSITION
        val oldRecycler = binding.catalogueView.getChildAt(1)
        if (oldRecycler is RecyclerView) {
            oldPosition = (oldRecycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            oldRecycler.adapter = null

            binding.catalogueView.removeView(oldRecycler)
        }

        val recycler = if (preferences.sourceDisplayMode().get() == DisplayModeSetting.LIST /* SY --> */ || (preferences.enhancedEHentaiView().get() && presenter.source.isEhBasedSource()) /* SY <-- */) {
            RecyclerView(view.context).apply {
                id = R.id.recycler
                layoutManager = LinearLayoutManager(context)
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        } else {
            (binding.catalogueView.inflate(R.layout.source_recycler_autofit) as AutofitRecyclerView).apply {
                numColumnsJob = getColumnsPreferenceForCurrentOrientation().asImmediateFlow { spanCount = it }
                    .drop(1)
                    // Set again the adapter to recalculate the covers height
                    .onEach { adapter = this@BrowseSourceController.adapter }
                    .launchIn(viewScope)

                (layoutManager as GridLayoutManager).spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return when (adapter?.getItemViewType(position)) {
                            R.layout.source_compact_grid_item, R.layout.source_comfortable_grid_item -> 1
                            else -> spanCount
                        }
                    }
                }
            }
        }

        if (filterSheet != null) {
            // Add bottom padding if filter FAB is visible
            recycler.updatePadding(bottom = view.resources.getDimensionPixelOffset(R.dimen.fab_list_padding))
            recycler.clipToPadding = false

            actionFab?.shrinkOnScroll(recycler)
        }

        recycler.applyInsetter {
            type(navigationBars = true) {
                padding()
            }
        }
        recycler.setHasFixedSize(true)
        recycler.adapter = adapter

        binding.catalogueView.addView(recycler, 1)

        if (oldPosition != RecyclerView.NO_POSITION) {
            recycler.layoutManager?.scrollToPosition(oldPosition)
        }
        this.recycler = recycler
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        createOptionsMenu(menu, inflater, R.menu.source_browse, R.id.action_search)
        val searchItem = menu.findItem(R.id.action_search)

        searchItem.fixExpand(
            onExpand = { invalidateMenuOnExpand() },
            onCollapse = {
                if (router.backstackSize >= 2 && router.backstack[router.backstackSize - 2].controller is GlobalSearchController) {
                    router.popController(this)
                } else {
                    nonSubmittedQuery = ""
                    searchWithQuery("")
                }

                true
            },
        )

        val displayItem = when (preferences.sourceDisplayMode().get()) {
            DisplayModeSetting.LIST -> R.id.action_list
            DisplayModeSetting.COMFORTABLE_GRID -> R.id.action_comfortable_grid
            else -> R.id.action_compact_grid
        }
        menu.findItem(displayItem).isChecked = true
        // SY -->
        if (preferences.enhancedEHentaiView().get() && presenter.source.isEhBasedSource()) {
            menu.findItem(R.id.action_display_mode).isVisible = false
        }
        // SY <--
    }

    override fun onSearchViewQueryTextSubmit(query: String?) {
        searchWithQuery(query ?: "")
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        val isHttpSource = presenter.source is HttpSource
        menu.findItem(R.id.action_open_in_web_view)?.isVisible = isHttpSource

        val isLocalSource = presenter.source is LocalSource
        menu.findItem(R.id.action_local_source_help)?.isVisible = isLocalSource

        // SY -->
        menu.findItem(R.id.action_settings)?.isVisible = presenter.source.anyIs<ConfigurableSource>()
        // SY <--
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> expandActionViewFromInteraction = true
            R.id.action_compact_grid -> setDisplayMode(DisplayModeSetting.COMPACT_GRID)
            R.id.action_comfortable_grid -> setDisplayMode(DisplayModeSetting.COMFORTABLE_GRID)
            R.id.action_list -> setDisplayMode(DisplayModeSetting.LIST)
            R.id.action_open_in_web_view -> openInWebView()
            // SY -->
            R.id.action_settings -> openSourceSettings()
            // SY <--
            R.id.action_local_source_help -> openLocalSourceHelpGuide()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openInWebView() {
        val source = presenter.source as? HttpSource ?: return

        val activity = activity ?: return
        val intent = WebViewActivity.newIntent(activity, source.baseUrl, source.id, presenter.source.name)
        startActivity(intent)
    }

    private fun openLocalSourceHelpGuide() {
        activity?.openInBrowser(LocalSource.HELP_URL)
    }

    // SY -->
    private fun openSourceSettings() {
        router.pushController(SourcePreferencesController(presenter.source.id))
    }
    // SY <--

    /**
     * Restarts the request with a new query.
     *
     * @param newQuery the new query.
     */
    fun searchWithQuery(newQuery: String) {
        // If text didn't change, do nothing
        if (presenter.query == newQuery) {
            return
        }

        showProgressBar()
        adapter?.clear()

        presenter.restartPager(newQuery, presenter.sourceFilters)
    }

    /**
     * Attempts to restart the request with a new genre-filtered query.
     * If the genre name can't be found the filters,
     * the standard searchWithQuery search method is used instead.
     *
     * @param genreName the name of the genre
     */
    fun searchWithGenre(genreName: String) {
        presenter.sourceFilters = presenter.source.getFilterList()

        var filterList: FilterList? = null

        filter@ for (sourceFilter in presenter.sourceFilters) {
            if (sourceFilter is Filter.Group<*>) {
                for (filter in sourceFilter.state) {
                    if (filter is Filter<*> && filter.name.equals(genreName, true)) {
                        when (filter) {
                            is Filter.TriState -> filter.state = 1
                            is Filter.CheckBox -> filter.state = true
                            else -> {}
                        }
                        filterList = presenter.sourceFilters
                        break@filter
                    }
                }
            } else if (sourceFilter is Filter.Select<*>) {
                val index = sourceFilter.values.filterIsInstance<String>()
                    .indexOfFirst { it.equals(genreName, true) }

                if (index != -1) {
                    sourceFilter.state = index
                    filterList = presenter.sourceFilters
                    break
                }
            }
        }

        if (filterList != null) {
            filterSheet?.setFilters(presenter.filterItems)

            showProgressBar()

            adapter?.clear()
            presenter.restartPager("", filterList)
        } else {
            searchWithQuery(genreName)
        }
    }

    /**
     * Called from the presenter when the network request is received.
     *
     * @param page the current page.
     * @param mangas the list of manga of the page.
     */
    fun onAddPage(page: Int, mangas: List<SourceItem>) {
        val adapter = adapter ?: return
        hideProgressBar()
        if (page == 1) {
            adapter.clear()
            resetProgressItem()
        }
        adapter.onLoadMoreComplete(mangas)
    }

    /**
     * Called from the presenter when the network request fails.
     *
     * @param error the error received.
     */
    /* SY --> */
    open /* SY <-- */fun onAddPageError(error: Throwable) {
        // SY -->
        xLogW("> Failed to load next catalogue page!", error)
        xLogW(
            "> (source.id: %s, source.name: %s)",
            presenter.source.id,
            presenter.source.name,
        )
        // SY <--

        val adapter = adapter ?: return
        adapter.onLoadMoreComplete(null)
        hideProgressBar()

        snack?.dismiss()

        val message = getErrorMessage(error)
        val retryAction = View.OnClickListener {
            // If not the first page, show bottom progress bar.
            if (adapter.mainItemCount > 0 && progressItem != null) {
                adapter.addScrollableFooterWithDelay(progressItem!!, 0, true)
            } else {
                showProgressBar()
            }
            presenter.requestNext()
        }

        if (adapter.isEmpty) {
            val actions = if (presenter.source is LocalSource) {
                listOf(
                    EmptyView.Action(R.string.local_source_help_guide, R.drawable.ic_help_24dp) { openLocalSourceHelpGuide() },
                )
            } else {
                listOf(
                    EmptyView.Action(R.string.action_retry, R.drawable.ic_refresh_24dp, retryAction),
                    EmptyView.Action(R.string.action_open_in_web_view, R.drawable.ic_public_24dp) { openInWebView() },
                    EmptyView.Action(R.string.label_help, R.drawable.ic_help_24dp) { activity?.openInBrowser(MoreController.URL_HELP) },
                )
            }

            binding.emptyView.show(message, actions)
        } else {
            snack = (activity as? MainActivity)?.binding?.rootCoordinator?.snack(message, Snackbar.LENGTH_INDEFINITE) {
                setAction(R.string.action_retry, retryAction)
            }
        }
    }

    private fun getErrorMessage(error: Throwable): String {
        if (error is NoResultsException) {
            return binding.catalogueView.context.getString(R.string.no_results_found)
        }

        return when {
            error.message == null -> ""
            error.message!!.startsWith("HTTP error") -> "${error.message}: ${binding.catalogueView.context.getString(R.string.http_error_hint)}"
            else -> error.message!!
        }
    }

    /**
     * Sets a new progress item and reenables the scroll listener.
     */
    private fun resetProgressItem() {
        progressItem = ProgressItem()
        adapter?.endlessTargetCount = 0
        adapter?.setEndlessScrollListener(this, progressItem!!)
    }

    /**
     * Called by the adapter when scrolled near the bottom.
     */
    override fun onLoadMore(lastPosition: Int, currentPage: Int) {
        if (presenter.hasNextPage()) {
            presenter.requestNext()
        } else {
            adapter?.onLoadMoreComplete(null)
            adapter?.endlessTargetCount = 1
        }
    }

    override fun noMoreLoad(newItemsSize: Int) {
    }

    /**
     * Called from the presenter when a manga is initialized.
     *
     * @param manga the manga initialized
     */
    fun onMangaInitialized(manga: Manga) {
        getHolder(manga)?.setImage(manga)
    }

    /**
     * Sets the current display mode.
     *
     * @param mode the mode to change to
     */
    private fun setDisplayMode(mode: DisplayModeSetting) {
        val view = view ?: return
        val adapter = adapter ?: return

        preferences.sourceDisplayMode().set(mode)
        activity?.invalidateOptionsMenu()
        setupRecycler(view)

        // Initialize mangas if not on a metered connection
        if (!view.context.connectivityManager.isActiveNetworkMetered) {
            val mangas = (0 until adapter.itemCount).mapNotNull {
                (adapter.getItem(it) as? SourceItem)?.manga
            }
            presenter.initializeMangas(mangas)
        }
    }

    /**
     * Returns a preference for the number of manga per row based on the current orientation.
     *
     * @return the preference.
     */
    private fun getColumnsPreferenceForCurrentOrientation(): Preference<Int> {
        return if (resources?.configuration?.orientation == Configuration.ORIENTATION_PORTRAIT) {
            preferences.portraitColumns()
        } else {
            preferences.landscapeColumns()
        }
    }

    /**
     * Returns the view holder for the given manga.
     *
     * @param manga the manga to find.
     * @return the holder of the manga or null if it's not bound.
     */
    private fun getHolder(manga: Manga): SourceHolder<*>? {
        val adapter = adapter ?: return null

        adapter.allBoundViewHolders.forEach { holder ->
            val item = adapter.getItem(holder.bindingAdapterPosition) as? SourceItem
            if (item != null && item.manga.id == manga.id) {
                return holder as SourceHolder<*>
            }
        }

        return null
    }

    /**
     * Shows the progress bar.
     */
    private fun showProgressBar() {
        binding.emptyView.hide()
        binding.progress.isVisible = true
        snack?.dismiss()
        snack = null
    }

    /**
     * Hides active progress bars.
     */
    private fun hideProgressBar() {
        binding.emptyView.hide()
        binding.progress.isVisible = false
    }

    /**
     * Called when a manga is clicked.
     *
     * @param position the position of the element clicked.
     * @return true if the item should be selected, false otherwise.
     */
    override fun onItemClick(view: View, position: Int): Boolean {
        val item = adapter?.getItem(position) as? SourceItem ?: return false
        router.pushController(
            MangaController(
                item.manga.id,
                true,
                args.getParcelable(MangaController.SMART_SEARCH_CONFIG_EXTRA),
            ),
        )
        return false
    }

    /**
     * Called when a manga is long clicked.
     *
     * Adds the manga to the default category if none is set it shows a list of categories for the user to put the manga
     * in, the list consists of the default category plus the user's categories. The default category is preselected on
     * new manga, and on already favorited manga the manga's categories are preselected.
     *
     * @param position the position of the element clicked.
     */
    override fun onItemLongClick(position: Int) {
        val activity = activity ?: return
        val manga = (adapter?.getItem(position) as? SourceItem?)?.manga ?: return
        launchIO {
            val duplicateManga = presenter.getDuplicateLibraryManga(manga)

            withUIContext {
                if (manga.favorite) {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(manga.title)
                        .setItems(arrayOf(activity.getString(R.string.remove_from_library))) { _, which ->
                            when (which) {
                                0 -> {
                                    presenter.changeMangaFavorite(manga.toDbManga())
                                    adapter?.notifyItemChanged(position)
                                    activity.toast(activity.getString(R.string.manga_removed_library))
                                }
                            }
                        }
                        .show()
                } else {
                    if (duplicateManga != null) {
                        AddDuplicateMangaDialog(this@BrowseSourceController, duplicateManga) {
                            addToLibrary(
                                manga,
                                position,
                            )
                        }
                            .showDialog(router)
                    } else {
                        addToLibrary(manga, position)
                    }
                }
            }
        }
    }

    private fun addToLibrary(newManga: Manga, position: Int) {
        val activity = activity ?: return
        launchIO {
            val categories = presenter.getCategories()
            val defaultCategoryId = preferences.defaultCategory()
            val defaultCategory = categories.find { it.id == defaultCategoryId.toLong() }

            withUIContext {
                when {
                    // Default category set
                    defaultCategory != null -> {
                        presenter.moveMangaToCategory(newManga.toDbManga(), defaultCategory)

                        presenter.changeMangaFavorite(newManga.toDbManga())
                        adapter?.notifyItemChanged(position)
                        activity.toast(activity.getString(R.string.manga_added_library))
                    }

                    // Automatic 'Default' or no categories
                    defaultCategoryId == 0 || categories.isEmpty() -> {
                        presenter.moveMangaToCategory(newManga.toDbManga(), null)

                        presenter.changeMangaFavorite(newManga.toDbManga())
                        adapter?.notifyItemChanged(position)
                        activity.toast(activity.getString(R.string.manga_added_library))
                    }

                    // Choose a category
                    else -> {
                        val ids = presenter.getMangaCategoryIds(newManga)
                        val preselected = categories.map {
                            if (it.id in ids) {
                                QuadStateTextView.State.CHECKED.ordinal
                            } else {
                                QuadStateTextView.State.UNCHECKED.ordinal
                            }
                        }.toTypedArray()

                        ChangeMangaCategoriesDialog(this@BrowseSourceController, listOf(newManga), categories, preselected)
                            .showDialog(router)
                    }
                }
            }
        }
    }

    /**
     * Update manga to use selected categories.
     *
     * @param mangas The list of manga to move to categories.
     * @param categories The list of categories where manga will be placed.
     */
    override fun updateCategoriesForMangas(mangas: List<Manga>, addCategories: List<Category>, removeCategories: List<Category>) {
        val manga = mangas.firstOrNull() ?: return

        presenter.changeMangaFavorite(manga.toDbManga())
        presenter.updateMangaCategories(manga.toDbManga(), addCategories)

        val position = adapter?.currentItems?.indexOfFirst { it -> (it as SourceItem).manga.id == manga.id }
        if (position != null) {
            adapter?.notifyItemChanged(position)
        }
        activity?.toast(activity?.getString(R.string.manga_added_library))
    }

    companion object {
        const val SOURCE_ID_KEY = "sourceId"
        const val SEARCH_QUERY_KEY = "searchQuery"

        // SY -->
        const val SMART_SEARCH_CONFIG_KEY = "smartSearchConfig"
        const val SAVED_SEARCH_CONFIG_KEY = "savedSearch"
        const val FILTERS_CONFIG_KEY = "filters"
        // SY <--
    }
}
