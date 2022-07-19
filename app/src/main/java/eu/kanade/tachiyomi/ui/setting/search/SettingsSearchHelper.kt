package eu.kanade.tachiyomi.ui.setting.search

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.forEach
import androidx.preference.get
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.setting.SettingsAdvancedController
import eu.kanade.tachiyomi.ui.setting.SettingsAppearanceController
import eu.kanade.tachiyomi.ui.setting.SettingsBackupController
import eu.kanade.tachiyomi.ui.setting.SettingsBrowseController
import eu.kanade.tachiyomi.ui.setting.SettingsController
import eu.kanade.tachiyomi.ui.setting.SettingsDownloadController
import eu.kanade.tachiyomi.ui.setting.SettingsEhController
import eu.kanade.tachiyomi.ui.setting.SettingsGeneralController
import eu.kanade.tachiyomi.ui.setting.SettingsLibraryController
import eu.kanade.tachiyomi.ui.setting.SettingsMangaDexController
import eu.kanade.tachiyomi.ui.setting.SettingsReaderController
import eu.kanade.tachiyomi.ui.setting.SettingsSecurityController
import eu.kanade.tachiyomi.ui.setting.SettingsTrackingController
import eu.kanade.tachiyomi.util.lang.launchNow
import eu.kanade.tachiyomi.util.system.isLTR
import exh.md.utils.MdUtil
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

object SettingsSearchHelper {
    private var prefSearchResultList: MutableList<SettingsSearchResult> = mutableListOf()

    /**
     * All subclasses of `SettingsController` should be listed here, in order to have their preferences searchable.
     */
    // SY -->
    private val settingControllersList: List<KClass<out SettingsController>> = run {
        val controllers = mutableListOf(
            SettingsAdvancedController::class,
            SettingsAppearanceController::class,
            SettingsBackupController::class,
            SettingsBrowseController::class,
            SettingsDownloadController::class,
            SettingsGeneralController::class,
            SettingsLibraryController::class,
            SettingsReaderController::class,
            SettingsSecurityController::class,
            SettingsTrackingController::class,
        )
        val preferences = Injekt.get<PreferencesHelper>()
        if (MdUtil.getEnabledMangaDexs(preferences).isNotEmpty()) {
            controllers += SettingsMangaDexController::class
        }
        if (preferences.isHentaiEnabled().get()) {
            controllers += SettingsEhController::class
        }
        controllers
    }

    // SY <--

    /**
     * Must be called to populate `prefSearchResultList`
     */
    @SuppressLint("RestrictedApi")
    fun initPreferenceSearchResults(context: Context) {
        val preferenceManager = PreferenceManager(context)
        prefSearchResultList.clear()

        launchNow {
            settingControllersList.forEach { kClass ->
                val ctrl = kClass.createInstance()
                val settingsPrefScreen = ctrl.setupPreferenceScreen(preferenceManager.createPreferenceScreen(context))
                val prefCount = settingsPrefScreen.preferenceCount
                for (i in 0 until prefCount) {
                    val rootPref = settingsPrefScreen[i]
                    if (rootPref.title == null) continue // no title, not a preference. (note: only info notes appear to not have titles)
                    getSettingSearchResult(ctrl, rootPref, "${settingsPrefScreen.title}")
                }
            }
        }
    }

    fun getFilteredResults(query: String): List<SettingsSearchResult> {
        return prefSearchResultList.filter {
            val inTitle = it.title.contains(query, true)
            val inSummary = it.summary.contains(query, true)
            val inBreadcrumb = it.breadcrumb.contains(query, true)

            return@filter inTitle || inSummary || inBreadcrumb
        }
    }

    /**
     * Extracts the data needed from a `Preference` to create a `SettingsSearchResult`, and then adds it to `prefSearchResultList`
     * Future enhancement: make bold the text matched by the search query.
     */
    private fun getSettingSearchResult(
        ctrl: SettingsController,
        pref: Preference,
        breadcrumbs: String = "",
    ) {
        when {
            pref is PreferenceGroup -> {
                val breadcrumbsStr = addLocalizedBreadcrumb(breadcrumbs, "${pref.title}")
                pref.forEach {
                    getSettingSearchResult(ctrl, it, breadcrumbsStr) // recursion
                }
            }
            pref is PreferenceCategory -> {
                val breadcrumbsStr = addLocalizedBreadcrumb(breadcrumbs, "${pref.title}")
                pref.forEach {
                    getSettingSearchResult(ctrl, it, breadcrumbsStr) // recursion
                }
            }
            (pref.title != null && pref.isVisible) -> {
                // Is an actual preference
                val title = pref.title.toString()
                // ListPreferences occasionally run into ArrayIndexOutOfBoundsException issues
                val summary = try { pref.summary?.toString() ?: "" } catch (e: Throwable) { "" }
                val breadcrumbsStr = addLocalizedBreadcrumb(breadcrumbs, "${pref.title}")

                prefSearchResultList.add(
                    SettingsSearchResult(
                        key = pref.key,
                        title = title,
                        summary = summary,
                        breadcrumb = breadcrumbsStr,
                        searchController = ctrl,
                    ),
                )
            }
        }
    }

    private fun addLocalizedBreadcrumb(path: String, node: String): String {
        return if (Resources.getSystem().isLTR) {
            // This locale reads left to right.
            "$path > $node"
        } else {
            // This locale reads right to left.
            "$node < $path"
        }
    }

    data class SettingsSearchResult(
        val key: String?,
        val title: String,
        val summary: String,
        val breadcrumb: String,
        val searchController: SettingsController,
    )
}
