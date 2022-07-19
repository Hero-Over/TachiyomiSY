package eu.kanade.tachiyomi.ui.manga.chapter

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import com.bluelinelabs.conductor.Controller
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.widget.DialogCustomDownloadView

/**
 * Dialog used to let user select amount of chapters to download.
 */
class DownloadCustomChaptersDialog<T> : DialogController
        where T : Controller, T : DownloadCustomChaptersDialog.Listener {

    /**
     * Maximum number of chapters to download in download chooser.
     */
    private val maxChapters: Int

    /**
     * Initialize dialog.
     * @param maxChapters maximal number of chapters that user can download.
     */
    constructor(target: T, maxChapters: Int) : super(
        // Add maximum number of chapters to download value to bundle.
        bundleOf(KEY_ITEM_MAX to maxChapters),
    ) {
        targetController = target
        this.maxChapters = maxChapters
    }

    /**
     * Restore dialog.
     * @param bundle bundle containing data from state restore.
     */
    @Suppress("unused")
    constructor(bundle: Bundle) : super(bundle) {
        // Get maximum chapters to download from bundle
        val maxChapters = bundle.getInt(KEY_ITEM_MAX, 0)
        this.maxChapters = maxChapters
    }

    /**
     * Called when dialog is being created.
     */
    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val activity = activity!!

        // Initialize view that lets user select number of chapters to download.
        val view = DialogCustomDownloadView(activity).apply {
            setMinMax(0, maxChapters)
        }

        // Build dialog.
        // when positive dialog is pressed call custom listener.
        return MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.custom_download)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                (targetController as? Listener)?.downloadCustomChapters(view.amount)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    interface Listener {
        fun downloadCustomChapters(amount: Int)
    }
}

// Key to retrieve max chapters from bundle on process death.
private const val KEY_ITEM_MAX = "DownloadCustomChaptersDialog.int.maxChapters"
