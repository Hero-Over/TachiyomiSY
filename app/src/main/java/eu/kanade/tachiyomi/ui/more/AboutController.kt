package eu.kanade.tachiyomi.ui.more

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import eu.kanade.presentation.more.about.AboutScreen
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.data.updater.AppUpdateResult
import eu.kanade.tachiyomi.ui.base.controller.BasicComposeController
import eu.kanade.tachiyomi.ui.base.controller.NoAppBarElevationController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.main.WhatsNewDialogController
import eu.kanade.tachiyomi.util.lang.launchNow
import eu.kanade.tachiyomi.util.lang.toDateTimestampString
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.system.toast
import logcat.LogPriority
import uy.kohesive.injekt.injectLazy
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AboutController : BasicComposeController(), NoAppBarElevationController {

    private val preferences: PreferencesHelper by injectLazy()
    private val updateChecker by lazy { AppUpdateChecker() }

    override fun getTitle() = resources?.getString(R.string.pref_category_about)

    @Composable
    override fun ComposeContent(nestedScrollInterop: NestedScrollConnection) {
        AboutScreen(
            nestedScrollInterop = nestedScrollInterop,
            checkVersion = this::checkVersion,
            getFormattedBuildTime = this::getFormattedBuildTime,
            onClickLicenses = {
                router.pushController(LicensesController())
            },
            onClickWhatsNew = {
                WhatsNewDialogController().showDialog(router)
            },
        )
    }

    /**
     * Checks version and shows a user prompt if an update is available.
     */
    private fun checkVersion() {
        if (activity == null) return

        activity!!.toast(R.string.update_check_look_for_updates)

        launchNow {
            try {
                when (val result = updateChecker.checkForUpdate(activity!!, isUserPrompt = true)) {
                    is AppUpdateResult.NewUpdate -> {
                        NewUpdateDialogController(result).showDialog(router)
                    }
                    is AppUpdateResult.NoNewUpdate -> {
                        activity?.toast(R.string.update_check_no_new_updates)
                    }
                    else -> {}
                }
            } catch (error: Exception) {
                activity?.toast(error.message)
                logcat(LogPriority.ERROR, error)
            }
        }
    }

    private fun getFormattedBuildTime(): String {
        return try {
            val inputDf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US)
            inputDf.timeZone = TimeZone.getTimeZone("UTC")
            val buildTime = inputDf.parse(BuildConfig.BUILD_TIME)

            val outputDf = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT,
                Locale.getDefault(),
            )
            outputDf.timeZone = TimeZone.getDefault()

            buildTime!!.toDateTimestampString(preferences.dateFormat())
        } catch (e: Exception) {
            BuildConfig.BUILD_TIME
        }
    }
}
