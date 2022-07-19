package eu.kanade.tachiyomi.ui.setting

import android.app.Activity
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.NoLoginTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.bangumi.BangumiApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.setting.track.TrackLoginDialog
import eu.kanade.tachiyomi.ui.setting.track.TrackLogoutDialog
import eu.kanade.tachiyomi.util.preference.add
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.iconRes
import eu.kanade.tachiyomi.util.preference.infoPreference
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.widget.preference.TrackerPreference
import uy.kohesive.injekt.injectLazy
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsTrackingController :
    SettingsController(),
    TrackLoginDialog.Listener,
    TrackLogoutDialog.Listener {

    private val trackManager: TrackManager by injectLazy()
    private val sourceManager: SourceManager by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_tracking

        switchPreference {
            key = Keys.autoUpdateTrack
            titleRes = R.string.pref_auto_update_manga_sync
            defaultValue = true
        }

        preferenceCategory {
            titleRes = R.string.services

            trackPreference(trackManager.myAnimeList) {
                activity?.openInBrowser(MyAnimeListApi.authUrl(), forceDefaultBrowser = true)
            }
            trackPreference(trackManager.aniList) {
                activity?.openInBrowser(AnilistApi.authUrl(), forceDefaultBrowser = true)
            }
            trackPreference(trackManager.kitsu) {
                val dialog = TrackLoginDialog(trackManager.kitsu, R.string.email)
                dialog.targetController = this@SettingsTrackingController
                dialog.showDialog(router)
            }
            trackPreference(trackManager.mangaUpdates) {
                val dialog = TrackLoginDialog(trackManager.mangaUpdates, R.string.username)
                dialog.targetController = this@SettingsTrackingController
                dialog.showDialog(router)
            }
            trackPreference(trackManager.shikimori) {
                activity?.openInBrowser(ShikimoriApi.authUrl(), forceDefaultBrowser = true)
            }
            trackPreference(trackManager.bangumi) {
                activity?.openInBrowser(BangumiApi.authUrl(), forceDefaultBrowser = true)
            }
            infoPreference(R.string.tracking_info)
        }

        preferenceCategory {
            titleRes = R.string.enhanced_services

            trackPreference(trackManager.komga) {
                val acceptedSources = trackManager.komga.getAcceptedSources()
                val hasValidSourceInstalled = sourceManager.getCatalogueSources()
                    .any { it::class.qualifiedName in acceptedSources }

                if (hasValidSourceInstalled) {
                    trackManager.komga.loginNoop()
                    updatePreference(trackManager.komga.id)
                } else {
                    context.toast(R.string.tracker_komga_warning, Toast.LENGTH_LONG)
                }
            }

            infoPreference(R.string.enhanced_tracking_info)
        }
    }

    private inline fun PreferenceGroup.trackPreference(
        service: TrackService,
        crossinline login: () -> Unit,
    ): TrackerPreference {
        return add(
            TrackerPreference(context).apply {
                key = Keys.trackUsername(service.id)
                titleRes = service.nameRes()
                iconRes = service.getLogo()
                iconColor = service.getLogoColor()
                onClick {
                    if (service.isLogged) {
                        if (service is NoLoginTrackService) {
                            service.logout()
                            updatePreference(service.id)
                        } else {
                            val dialog = TrackLogoutDialog(service)
                            dialog.targetController = this@SettingsTrackingController
                            dialog.showDialog(router)
                        }
                    } else {
                        login()
                    }
                }
            },
        )
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)

        // Manually refresh OAuth trackers' holders
        updatePreference(trackManager.myAnimeList.id)
        updatePreference(trackManager.aniList.id)
        updatePreference(trackManager.shikimori.id)
        updatePreference(trackManager.bangumi.id)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.settings_tracking, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_tracking_help -> activity?.openInBrowser(HELP_URL)
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updatePreference(id: Long) {
        val pref = findPreference(Keys.trackUsername(id)) as? TrackerPreference
        pref?.notifyChanged()
    }

    override fun trackLoginDialogClosed(service: TrackService) {
        updatePreference(service.id)
    }

    override fun trackLogoutDialogClosed(service: TrackService) {
        updatePreference(service.id)
    }
}

private const val HELP_URL = "https://tachiyomi.org/help/guides/tracking/"
