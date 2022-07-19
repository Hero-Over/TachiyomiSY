package eu.kanade.tachiyomi.ui.reader.viewer

import com.fredporciuncula.flow.preferences.Preference
import eu.kanade.tachiyomi.data.preference.PreferenceValues.TappingInvertMode
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Common configuration for all viewers.
 */
abstract class ViewerConfig(preferences: PreferencesHelper, private val scope: CoroutineScope) {

    var imagePropertyChangedListener: (() -> Unit)? = null

    var navigationModeChangedListener: (() -> Unit)? = null

    var tappingInverted = TappingInvertMode.NONE
    var longTapEnabled = true
    var doubleTapAnimDuration = 500
    var volumeKeysEnabled = false
    var volumeKeysInverted = false
    var trueColor = false
    var alwaysShowChapterTransition = true
    var navigationMode = 0
        protected set

    var forceNavigationOverlay = false

    var navigationOverlayOnStart = false

    var dualPageSplit = false
        protected set

    var dualPageInvert = false
        protected set

    abstract var navigator: ViewerNavigation
        protected set

    init {
        preferences.readWithLongTap()
            .register({ longTapEnabled = it })

        preferences.doubleTapAnimSpeed()
            .register({ doubleTapAnimDuration = it })

        preferences.readWithVolumeKeys()
            .register({ volumeKeysEnabled = it })

        preferences.readWithVolumeKeysInverted()
            .register({ volumeKeysInverted = it })

        preferences.trueColor()
            .register({ trueColor = it }, { imagePropertyChangedListener?.invoke() })

        preferences.alwaysShowChapterTransition()
            .register({ alwaysShowChapterTransition = it })

        forceNavigationOverlay = preferences.showNavigationOverlayNewUser().get()
        if (forceNavigationOverlay) {
            preferences.showNavigationOverlayNewUser().set(false)
        }

        preferences.showNavigationOverlayOnStart()
            .register({ navigationOverlayOnStart = it })
    }

    protected abstract fun defaultNavigation(): ViewerNavigation

    abstract fun updateNavigation(navigationMode: Int)

    fun <T> Preference<T>.register(
        valueAssignment: (T) -> Unit,
        onChanged: (T) -> Unit = {},
    ) {
        asFlow()
            .onEach { valueAssignment(it) }
            .distinctUntilChanged()
            .onEach { onChanged(it) }
            .launchIn(scope)
    }
}
