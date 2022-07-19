package eu.kanade.tachiyomi.ui.main

import android.animation.ValueAnimator
import android.app.SearchManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.Gravity
import android.view.Menu
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.appcompat.view.ActionMode
import androidx.core.animation.doOnEnd
import androidx.core.graphics.ColorUtils
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceDialogController
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import dev.chrisbanes.insetter.applyInsetter
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.data.updater.AppUpdateResult
import eu.kanade.tachiyomi.databinding.MainActivityBinding
import eu.kanade.tachiyomi.extension.api.ExtensionGithubApi
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.FabController
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController
import eu.kanade.tachiyomi.ui.base.controller.NoAppBarElevationController
import eu.kanade.tachiyomi.ui.base.controller.RootController
import eu.kanade.tachiyomi.ui.base.controller.TabbedController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.base.controller.setRoot
import eu.kanade.tachiyomi.ui.browse.BrowseController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.download.DownloadController
import eu.kanade.tachiyomi.ui.library.LibraryController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.ui.more.MoreController
import eu.kanade.tachiyomi.ui.more.NewUpdateDialogController
import eu.kanade.tachiyomi.ui.recent.history.HistoryController
import eu.kanade.tachiyomi.ui.recent.updates.UpdatesController
import eu.kanade.tachiyomi.ui.setting.SettingsMainController
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.preference.asImmediateFlow
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getThemeColor
import eu.kanade.tachiyomi.util.system.isTablet
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setNavigationBarTransparentCompat
import eu.kanade.tachiyomi.widget.ActionModeWithToolbar
import exh.EXHMigrations
import exh.eh.EHentaiUpdateWorker
import exh.source.BlacklistedSources
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import exh.uconfig.WarnConfigureDialogController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import uy.kohesive.injekt.injectLazy
import java.util.LinkedList

class MainActivity : BaseActivity() {

    lateinit var binding: MainActivityBinding

    private lateinit var router: Router

    private val startScreenId = R.id.nav_library
    private var isConfirmingExit: Boolean = false
    private var isHandlingShortcut: Boolean = false

    /**
     * App bar lift state for backstack
     */
    private val backstackLiftState = mutableMapOf<String, Boolean>()

    private val chapterCache: ChapterCache by injectLazy()

    // To be checked by splash screen. If true then splash screen will be removed.
    var ready = false

    // SY -->
    // Idle-until-urgent
    private var firstPaint = false
    private val iuuQueue = LinkedList<() -> Unit>()

    private fun initWhenIdle(task: () -> Unit) {
        // Avoid sync issues by enforcing main thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw IllegalStateException("Can only be called on main thread!")
        }

        if (firstPaint) {
            task()
        } else {
            iuuQueue += task
        }
    }
    // SY <--

    override fun onCreate(savedInstanceState: Bundle?) {
        // Prevent splash screen showing up on configuration changes
        val splashScreen = if (savedInstanceState == null) installSplashScreen() else null

        // Set up shared element transition and disable overlay so views don't show above system bars
        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        setExitSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        window.sharedElementsUseOverlay = false

        super.onCreate(savedInstanceState)

        val didMigration = if (savedInstanceState == null) EXHMigrations.upgrade(preferences) else false

        binding = MainActivityBinding.inflate(layoutInflater)

        // Do not let the launcher create a new activity http://stackoverflow.com/questions/16283079
        if (!isTaskRoot) {
            finish()
            return
        }

        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // Draw edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding.fabLayout.rootFab.applyInsetter {
            ignoreVisibility(true)
            type(navigationBars = true) {
                margin()
            }
        }
        binding.bottomNav?.applyInsetter {
            type(navigationBars = true) {
                padding()
            }
        }

        val startTime = System.currentTimeMillis()
        splashScreen?.setKeepVisibleCondition {
            val elapsed = System.currentTimeMillis() - startTime
            elapsed <= SPLASH_MIN_DURATION || (!ready && elapsed <= SPLASH_MAX_DURATION)
        }
        setSplashScreenExitAnimation(splashScreen)

        if (binding.sideNav != null) {
            preferences.sideNavIconAlignment()
                .asImmediateFlow {
                    binding.sideNav?.menuGravity = when (it) {
                        1 -> Gravity.CENTER
                        2 -> Gravity.BOTTOM
                        else -> Gravity.TOP
                    }
                }
                .launchIn(lifecycleScope)
        }

        nav.setOnItemSelectedListener { item ->
            val id = item.itemId

            val currentRoot = router.backstack.firstOrNull()
            if (currentRoot?.tag()?.toIntOrNull() != id) {
                when (id) {
                    R.id.nav_library -> router.setRoot(LibraryController(), id)
                    R.id.nav_updates -> router.setRoot(UpdatesController(), id)
                    R.id.nav_history -> router.setRoot(HistoryController(), id)
                    R.id.nav_browse -> router.setRoot(BrowseController(), id)
                    R.id.nav_more -> router.setRoot(MoreController(), id)
                }
            } else if (!isHandlingShortcut) {
                when (id) {
                    R.id.nav_library -> {
                        val controller = router.getControllerWithTag(id.toString()) as? LibraryController
                        controller?.showSettingsSheet()
                    }
                    R.id.nav_updates -> {
                        if (router.backstackSize == 1) {
                            router.pushController(DownloadController())
                        }
                    }
                    R.id.nav_history -> {
                        if (router.backstackSize == 1) {
                            try {
                                val historyController = router.backstack[0].controller as HistoryController
                                historyController.resumeLastChapterRead()
                            } catch (e: Exception) {
                                toast(R.string.cant_open_last_read_chapter)
                            }
                        }
                    }
                    R.id.nav_more -> {
                        if (router.backstackSize == 1) {
                            router.pushController(SettingsMainController())
                        }
                    }
                }
            }
            true
        }

        val container: ViewGroup = binding.controllerContainer
        router = Conductor.attachRouter(this, container, savedInstanceState)
            .setPopRootControllerMode(Router.PopRootControllerMode.NEVER)
        router.addChangeListener(
            object : ControllerChangeHandler.ControllerChangeListener {
                override fun onChangeStarted(
                    to: Controller?,
                    from: Controller?,
                    isPush: Boolean,
                    container: ViewGroup,
                    handler: ControllerChangeHandler,
                ) {
                    syncActivityViewWithController(to, from, isPush)
                }

                override fun onChangeCompleted(
                    to: Controller?,
                    from: Controller?,
                    isPush: Boolean,
                    container: ViewGroup,
                    handler: ControllerChangeHandler,
                ) {
                }
            },
        )
        if (!router.hasRootController()) {
            // Set start screen
            if (!handleIntentAction(intent)) {
                setSelectedNavItem(startScreenId)
            }
        }
        syncActivityViewWithController()

        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        if (savedInstanceState == null) {
            // Reset Incognito Mode on relaunch
            preferences.incognitoMode().set(false)

            // Show changelog prompt on update
            if (didMigration) {
                WhatsNewDialogController().showDialog(router)
            }
            // EXH <--

            // SY -->
            initWhenIdle {
                // Upload settings
                if (preferences.enableExhentai().get() &&
                    preferences.exhShowSettingsUploadWarning().get()
                ) {
                    WarnConfigureDialogController.uploadSettings(router)
                }
                // Scheduler uploader job if required

                EHentaiUpdateWorker.scheduleBackground(this)
            }
            // SY <--
        } else {
            // Restore selected nav item
            router.backstack.firstOrNull()?.tag()?.toIntOrNull()?.let {
                nav.menu.findItem(it).isChecked = true
            }
        }
        // SY -->
        if (!preferences.isHentaiEnabled().get()) {
            BlacklistedSources.HIDDEN_SOURCES += EH_SOURCE_ID
            BlacklistedSources.HIDDEN_SOURCES += EXH_SOURCE_ID
        }
        // SY -->

        merge(preferences.showUpdatesNavBadge().asFlow(), preferences.unreadUpdatesCount().asFlow())
            .onEach { setUnreadUpdatesBadge() }
            .launchIn(lifecycleScope)

        preferences.extensionUpdatesCount()
            .asImmediateFlow { setExtensionsBadge() }
            .launchIn(lifecycleScope)

        preferences.downloadedOnly()
            .asImmediateFlow { binding.downloadedOnly.isVisible = it }
            .launchIn(lifecycleScope)

        binding.incognitoMode.isVisible = preferences.incognitoMode().get()
        preferences.incognitoMode().asFlow()
            .drop(1)
            .onEach {
                binding.incognitoMode.isVisible = it

                // Close BrowseSourceController and its MangaController child when incognito mode is disabled
                if (!it) {
                    val fg = router.backstack.lastOrNull()?.controller
                    if (fg is BrowseSourceController || fg is MangaController && fg.fromSource) {
                        router.popToRoot()
                    }
                }
            }
            .launchIn(lifecycleScope)

        // SY -->
        preferences.bottomBarLabels()
            .asImmediateFlow { setNavLabelVisibility() }
            .launchIn(lifecycleScope)
        // SY <--
    }

    /**
     * Sets custom splash screen exit animation on devices prior to Android 12.
     *
     * When custom animation is used, status and navigation bar color will be set to transparent and will be restored
     * after the animation is finished.
     */
    private fun setSplashScreenExitAnimation(splashScreen: SplashScreen?) {
        val setNavbarScrim = {
            // Make sure navigation bar is on bottom before we modify it
            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
                if (insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom > 0) {
                    val elevation = binding.bottomNav?.elevation ?: 0F
                    window.setNavigationBarTransparentCompat(this@MainActivity, elevation)
                }
                insets
            }
            ViewCompat.requestApplyInsets(binding.root)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && splashScreen != null) {
            val oldStatusColor = window.statusBarColor
            val oldNavigationColor = window.navigationBarColor
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT

            splashScreen.setOnExitAnimationListener { splashProvider ->
                // For some reason the SplashScreen applies (incorrect) Y translation to the iconView
                splashProvider.iconView.translationY = 0F

                val activityAnim = ValueAnimator.ofFloat(1F, 0F).apply {
                    interpolator = LinearOutSlowInInterpolator()
                    duration = SPLASH_EXIT_ANIM_DURATION
                    addUpdateListener { va ->
                        val value = va.animatedValue as Float
                        binding.root.translationY = value * 16.dpToPx
                    }
                }

                val splashAnim = ValueAnimator.ofFloat(1F, 0F).apply {
                    interpolator = FastOutSlowInInterpolator()
                    duration = SPLASH_EXIT_ANIM_DURATION
                    addUpdateListener { va ->
                        val value = va.animatedValue as Float
                        splashProvider.view.alpha = value
                    }
                    doOnEnd {
                        splashProvider.remove()
                        window.statusBarColor = oldStatusColor
                        window.navigationBarColor = oldNavigationColor
                        setNavbarScrim()
                    }
                }

                activityAnim.start()
                splashAnim.start()
            }
        } else {
            setNavbarScrim()
        }
    }

    override fun onNewIntent(intent: Intent) {
        if (!handleIntentAction(intent)) {
            super.onNewIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        checkForUpdates()
    }

    private fun checkForUpdates() {
        lifecycleScope.launchIO {
            // App updates
            if (BuildConfig.INCLUDE_UPDATER) {
                try {
                    val result = AppUpdateChecker().checkForUpdate(this@MainActivity)
                    if (result is AppUpdateResult.NewUpdate) {
                        NewUpdateDialogController(result).showDialog(router)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e)
                }
            }

            // Extension updates
            try {
                ExtensionGithubApi().checkForUpdates(
                    this@MainActivity,
                    fromAvailableExtensionList = true,
                )?.let { pendingUpdates ->
                    preferences.extensionUpdatesCount().set(pendingUpdates.size)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    private fun setUnreadUpdatesBadge() {
        val updates = if (preferences.showUpdatesNavBadge().get()) preferences.unreadUpdatesCount().get() else 0
        if (updates > 0) {
            nav.getOrCreateBadge(R.id.nav_updates).apply {
                number = updates
                setContentDescriptionQuantityStringsResource(R.plurals.notification_chapters_generic)
            }
        } else {
            nav.removeBadge(R.id.nav_updates)
        }
    }

    private fun setExtensionsBadge() {
        val updates = preferences.extensionUpdatesCount().get()
        if (updates > 0) {
            nav.getOrCreateBadge(R.id.nav_browse).apply {
                number = updates
                setContentDescriptionQuantityStringsResource(R.plurals.update_check_notification_ext_updates)
            }
        } else {
            nav.removeBadge(R.id.nav_browse)
        }
    }

    private fun handleIntentAction(intent: Intent): Boolean {
        val notificationId = intent.getIntExtra("notificationId", -1)
        if (notificationId > -1) {
            NotificationReceiver.dismissNotification(applicationContext, notificationId, intent.getIntExtra("groupId", 0))
        }

        isHandlingShortcut = true

        when (intent.action) {
            SHORTCUT_LIBRARY -> setSelectedNavItem(R.id.nav_library)
            SHORTCUT_RECENTLY_UPDATED -> setSelectedNavItem(R.id.nav_updates)
            SHORTCUT_RECENTLY_READ -> setSelectedNavItem(R.id.nav_history)
            SHORTCUT_CATALOGUES -> setSelectedNavItem(R.id.nav_browse)
            SHORTCUT_EXTENSIONS -> {
                if (router.backstackSize > 1) {
                    router.popToRoot()
                }
                setSelectedNavItem(R.id.nav_browse)
                router.pushController(BrowseController(toExtensions = true))
            }
            SHORTCUT_MANGA -> {
                val extras = intent.extras ?: return false
                val fgController = router.backstack.lastOrNull()?.controller as? MangaController
                if (fgController?.mangaId != extras.getLong(MangaController.MANGA_EXTRA)) {
                    router.popToRoot()
                    setSelectedNavItem(R.id.nav_library)
                    router.pushController(RouterTransaction.with(MangaController(extras)))
                }
            }
            SHORTCUT_DOWNLOADS -> {
                if (router.backstackSize > 1) {
                    router.popToRoot()
                }
                setSelectedNavItem(R.id.nav_more)
                router.pushController(DownloadController())
            }
            Intent.ACTION_SEARCH, Intent.ACTION_SEND, "com.google.android.gms.actions.SEARCH_ACTION" -> {
                // If the intent match the "standard" Android search intent
                // or the Google-specific search intent (triggered by saying or typing "search *query* on *Tachiyomi*" in Google Search/Google Assistant)

                // Get the search query provided in extras, and if not null, perform a global search with it.
                val query = intent.getStringExtra(SearchManager.QUERY) ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                if (query != null && query.isNotEmpty()) {
                    if (router.backstackSize > 1) {
                        router.popToRoot()
                    }
                    router.pushController(GlobalSearchController(query))
                }
            }
            INTENT_SEARCH -> {
                val query = intent.getStringExtra(INTENT_SEARCH_QUERY)
                if (query != null && query.isNotEmpty()) {
                    val filter = intent.getStringExtra(INTENT_SEARCH_FILTER)
                    if (router.backstackSize > 1) {
                        router.popToRoot()
                    }
                    router.pushController(GlobalSearchController(query, filter))
                }
            }
            else -> {
                isHandlingShortcut = false
                return false
            }
        }

        ready = true
        isHandlingShortcut = false
        return true
    }

    @Suppress("UNNECESSARY_SAFE_CALL")
    override fun onDestroy() {
        super.onDestroy()

        // Binding sometimes isn't actually instantiated yet somehow
        nav?.setOnItemSelectedListener(null)
        binding?.toolbar?.setNavigationOnClickListener(null)
    }

    override fun onBackPressed() {
        val backstackSize = router.backstackSize
        if (backstackSize == 1 && router.getControllerWithTag("$startScreenId") == null) {
            // Return to start screen
            setSelectedNavItem(startScreenId)
        } else if (shouldHandleExitConfirmation()) {
            // Exit confirmation (resets after 2 seconds)
            lifecycleScope.launchUI { resetExitConfirmation() }
        } else if (backstackSize == 1 || !router.handleBack()) {
            // Regular back (i.e. closing the app)
            if (preferences.autoClearChapterCache()) {
                chapterCache.clear()
            }
            super.onBackPressed()
        }
    }

    override fun onSupportActionModeStarted(mode: ActionMode) {
        binding.appbar.apply {
            tag = isTransparentWhenNotLifted
            isTransparentWhenNotLifted = false
        }
        // Color taken from m3_appbar_background
        window.statusBarColor = ColorUtils.compositeColors(
            getColor(R.color.m3_appbar_overlay_color),
            getThemeColor(R.attr.colorSurface),
        )
        super.onSupportActionModeStarted(mode)
    }

    override fun onSupportActionModeFinished(mode: ActionMode) {
        binding.appbar.apply {
            isTransparentWhenNotLifted = (tag as? Boolean) ?: false
            tag = null
        }
        window.statusBarColor = getThemeColor(android.R.attr.statusBarColor)
        super.onSupportActionModeFinished(mode)
    }

    fun startActionModeAndToolbar(modeCallback: ActionModeWithToolbar.Callback): ActionModeWithToolbar {
        binding.actionToolbar.start(modeCallback)
        return binding.actionToolbar
    }

    private suspend fun resetExitConfirmation() {
        isConfirmingExit = true
        val toast = toast(R.string.confirm_exit, Toast.LENGTH_LONG)
        delay(2000)
        toast.cancel()
        isConfirmingExit = false
    }

    private fun shouldHandleExitConfirmation(): Boolean {
        return router.backstackSize == 1 &&
            router.getControllerWithTag("$startScreenId") != null &&
            preferences.confirmExit() &&
            !isConfirmingExit
    }

    fun setSelectedNavItem(itemId: Int) {
        if (!isFinishing) {
            nav.selectedItemId = itemId
        }
    }

    private fun syncActivityViewWithController(
        to: Controller? = null,
        from: Controller? = null,
        isPush: Boolean = true,
    ) {
        var internalTo = to

        if (internalTo == null) {
            // Should go here when the activity is recreated and dialog controller is on top of the backstack
            // Then we'll assume the top controller is the parent controller of this dialog
            val backstack = router.backstack
            internalTo = backstack.lastOrNull()?.controller
            if (internalTo is DialogController || internalTo is PreferenceDialogController) {
                internalTo = backstack.getOrNull(backstack.size - 2)?.controller ?: return
            }
        } else {
            // Ignore changes for normal transactions
            if (from is DialogController || internalTo is DialogController) {
                return
            }
            if (from is PreferenceDialogController || internalTo is PreferenceDialogController) {
                return
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(router.backstackSize != 1)

        // Always show appbar again when changing controllers
        binding.appbar.setExpanded(true)

        if ((from == null || from is RootController) && internalTo !is RootController) {
            showNav(false)
        }
        if (internalTo is RootController) {
            // Always show bottom nav again when returning to a RootController
            showNav(true)
        }

        if (from is TabbedController) {
            from.cleanupTabs(binding.tabs)
        }
        if (internalTo is TabbedController) {
            if (internalTo.configureTabs(binding.tabs)) {
                binding.tabs.isVisible = true
            }
        } else {
            binding.tabs.isVisible = false
        }

        if (from is FabController) {
            from.cleanupFab(binding.fabLayout.rootFab)
        }
        if (internalTo is FabController) {
            binding.fabLayout.rootFab.show()
            internalTo.configureFab(binding.fabLayout.rootFab)
        } else {
            binding.fabLayout.rootFab.hide()
        }

        val isFullComposeController = internalTo is FullComposeController<*>
        binding.appbar.isVisible = !isFullComposeController
        binding.controllerContainer.enableScrollingBehavior(!isFullComposeController)

        if (!isTablet()) {
            // Save lift state
            if (isPush) {
                if (router.backstackSize > 1) {
                    // Save lift state
                    from?.let {
                        backstackLiftState[it.instanceId] = binding.appbar.isLifted
                    }
                } else {
                    backstackLiftState.clear()
                }
                binding.appbar.isLifted = false
            } else {
                internalTo?.let {
                    binding.appbar.isLifted = backstackLiftState.getOrElse(it.instanceId) { false }
                }
                from?.let {
                    backstackLiftState.remove(it.instanceId)
                }
            }

            binding.root.isLiftAppBarOnScroll = internalTo !is NoAppBarElevationController
        }
    }

    private fun showNav(visible: Boolean) {
        showBottomNav(visible)
        showSideNav(visible)
    }

    // Also used from some controllers to swap bottom nav with action toolbar
    fun showBottomNav(visible: Boolean) {
        if (visible) {
            binding.bottomNav?.slideUp()
            // SY -->
            binding.bottomNav?.menu?.let { updateNavMenu(it) }
            // SY <--
        } else {
            binding.bottomNav?.slideDown()
        }
    }

    private fun showSideNav(visible: Boolean) {
        binding.sideNav?.isVisible = visible
        // SY -->
        binding.sideNav?.let { updateNavMenu(it.menu) }
        // SY <--
    }

    // SY -->
    private fun updateNavMenu(menu: Menu) {
        menu.findItem(R.id.nav_updates).isVisible = preferences.showNavUpdates().get()
        menu.findItem(R.id.nav_history).isVisible = preferences.showNavHistory().get()
    }
    // SY <--

    private val nav: NavigationBarView
        get() = binding.bottomNav ?: binding.sideNav!!

    // SY -->
    private fun setNavLabelVisibility() {
        if (preferences.bottomBarLabels().get()) {
            nav.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED
        } else {
            nav.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_SELECTED
        }
    }
    // SY <--

    init {
        registerSecureActivity(this)
    }

    companion object {
        // Splash screen
        private const val SPLASH_MIN_DURATION = 500 // ms
        private const val SPLASH_MAX_DURATION = 5000 // ms
        private const val SPLASH_EXIT_ANIM_DURATION = 400L // ms

        // Shortcut actions
        const val SHORTCUT_LIBRARY = "eu.kanade.tachiyomi.SHOW_LIBRARY"
        const val SHORTCUT_RECENTLY_UPDATED = "eu.kanade.tachiyomi.SHOW_RECENTLY_UPDATED"
        const val SHORTCUT_RECENTLY_READ = "eu.kanade.tachiyomi.SHOW_RECENTLY_READ"
        const val SHORTCUT_CATALOGUES = "eu.kanade.tachiyomi.SHOW_CATALOGUES"
        const val SHORTCUT_DOWNLOADS = "eu.kanade.tachiyomi.SHOW_DOWNLOADS"
        const val SHORTCUT_MANGA = "eu.kanade.tachiyomi.SHOW_MANGA"
        const val SHORTCUT_EXTENSIONS = "eu.kanade.tachiyomi.EXTENSIONS"

        const val INTENT_SEARCH = "eu.kanade.tachiyomi.SEARCH"
        const val INTENT_SEARCH_QUERY = "query"
        const val INTENT_SEARCH_FILTER = "filter"
    }
}
