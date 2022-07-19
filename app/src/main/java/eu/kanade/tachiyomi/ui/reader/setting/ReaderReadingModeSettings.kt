package eu.kanade.tachiyomi.ui.reader.setting

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.ReaderReadingModeSettingsBinding
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import eu.kanade.tachiyomi.util.preference.asImmediateFlow
import eu.kanade.tachiyomi.util.preference.bindToPreference
import kotlinx.coroutines.flow.launchIn
import uy.kohesive.injekt.injectLazy

/**
 * Sheet to show reader and viewer preferences.
 */
class ReaderReadingModeSettings @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    NestedScrollView(context, attrs) {

    private val preferences: PreferencesHelper by injectLazy()

    private val binding = ReaderReadingModeSettingsBinding.inflate(LayoutInflater.from(context), this, false)

    init {
        addView(binding.root)

        initGeneralPreferences()

        when ((context as ReaderActivity).viewer) {
            is PagerViewer -> initPagerPreferences()
            is WebtoonViewer -> initWebtoonPreferences()
        }
    }

    /**
     * Init general reader preferences.
     */
    private fun initGeneralPreferences() {
        binding.viewer.onItemSelectedListener = { position ->
            val readingModeType = ReadingModeType.fromSpinner(position)
            (context as ReaderActivity).presenter.setMangaReadingMode(readingModeType.flagValue)

            val mangaViewer = (context as ReaderActivity).presenter.getMangaReadingMode()
            if (mangaViewer == ReadingModeType.WEBTOON.flagValue || mangaViewer == ReadingModeType.CONTINUOUS_VERTICAL.flagValue) {
                initWebtoonPreferences()
            } else {
                initPagerPreferences()
            }
        }
        binding.viewer.setSelection((context as ReaderActivity).presenter.manga?.readingModeType?.let { ReadingModeType.fromPreference(it).prefValue } ?: ReadingModeType.DEFAULT.prefValue)

        binding.rotationMode.onItemSelectedListener = { position ->
            val rotationType = OrientationType.fromSpinner(position)
            (context as ReaderActivity).presenter.setMangaOrientationType(rotationType.flagValue)
        }
        binding.rotationMode.setSelection((context as ReaderActivity).presenter.manga?.orientationType?.let { OrientationType.fromPreference(it).prefValue } ?: OrientationType.DEFAULT.prefValue)
    }

    /**
     * Init the preferences for the pager reader.
     */
    private fun initPagerPreferences() {
        binding.webtoonPrefsGroup.root.isVisible = false
        binding.pagerPrefsGroup.root.isVisible = true

        binding.pagerPrefsGroup.tappingInverted.bindToPreference(preferences.pagerNavInverted())

        binding.pagerPrefsGroup.pagerNav.bindToPreference(preferences.navigationModePager())
        preferences.navigationModePager()
            .asImmediateFlow { binding.pagerPrefsGroup.tappingInverted.isVisible = it != 5 }
            .launchIn((context as ReaderActivity).lifecycleScope)
        // Makes so that landscape zoom gets hidden away when image scale type is not fit screen
        binding.pagerPrefsGroup.scaleType.bindToPreference(preferences.imageScaleType(), 1)
        preferences.imageScaleType()
            .asImmediateFlow { binding.pagerPrefsGroup.landscapeZoom.isVisible = it == 1 }
            .launchIn((context as ReaderActivity).lifecycleScope)
        binding.pagerPrefsGroup.landscapeZoom.bindToPreference(preferences.landscapeZoom())

        binding.pagerPrefsGroup.zoomStart.bindToPreference(preferences.zoomStart(), 1)
        binding.pagerPrefsGroup.cropBorders.bindToPreference(preferences.cropBorders())
        binding.pagerPrefsGroup.navigatePan.bindToPreference(preferences.navigateToPan())

        // Makes so that dual page invert gets hidden away when turning of dual page split
        binding.pagerPrefsGroup.dualPageSplit.bindToPreference(preferences.dualPageSplitPaged())
        preferences.dualPageSplitPaged()
            .asImmediateFlow { binding.pagerPrefsGroup.dualPageInvert.isVisible = it }
            .launchIn((context as ReaderActivity).lifecycleScope)
        binding.pagerPrefsGroup.dualPageInvert.bindToPreference(preferences.dualPageInvertPaged())

        // SY -->
        binding.pagerPrefsGroup.pageTransitionsPager.bindToPreference(preferences.pageTransitionsPager())
        binding.pagerPrefsGroup.pageLayout.bindToPreference(preferences.pageLayout())
        binding.pagerPrefsGroup.invertDoublePages.bindToPreference(preferences.invertDoublePages())
        // SY <--
    }

    /**
     * Init the preferences for the webtoon reader.
     */
    private fun initWebtoonPreferences() {
        binding.pagerPrefsGroup.root.isVisible = false
        binding.webtoonPrefsGroup.root.isVisible = true

        binding.webtoonPrefsGroup.tappingInverted.bindToPreference(preferences.webtoonNavInverted())

        binding.webtoonPrefsGroup.webtoonNav.bindToPreference(preferences.navigationModeWebtoon())
        preferences.navigationModeWebtoon()
            .asImmediateFlow { binding.webtoonPrefsGroup.tappingInverted.isVisible = it != 5 }
            .launchIn((context as ReaderActivity).lifecycleScope)
        binding.webtoonPrefsGroup.cropBordersWebtoon.bindToPreference(preferences.cropBordersWebtoon())
        binding.webtoonPrefsGroup.webtoonSidePadding.bindToIntPreference(preferences.webtoonSidePadding(), R.array.webtoon_side_padding_values)

        // Makes so that dual page invert gets hidden away when turning of dual page split
        binding.webtoonPrefsGroup.dualPageSplit.bindToPreference(preferences.dualPageSplitWebtoon())
        preferences.dualPageSplitWebtoon()
            .asImmediateFlow { binding.webtoonPrefsGroup.dualPageInvert.isVisible = it }
            .launchIn((context as ReaderActivity).lifecycleScope)
        binding.webtoonPrefsGroup.dualPageInvert.bindToPreference(preferences.dualPageInvertWebtoon())

        // SY -->
        binding.webtoonPrefsGroup.zoomOutWebtoon.bindToPreference(preferences.webtoonEnableZoomOut())
        binding.webtoonPrefsGroup.cropBordersContinuousVertical.bindToPreference(preferences.cropBordersContinuousVertical())
        binding.webtoonPrefsGroup.pageTransitionsWebtoon.bindToPreference(preferences.pageTransitionsWebtoon())
        // SY <--
    }
}
