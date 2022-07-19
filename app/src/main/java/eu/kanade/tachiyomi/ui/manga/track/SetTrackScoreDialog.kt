package eu.kanade.tachiyomi.ui.manga.track

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.os.bundleOf
import com.bluelinelabs.conductor.Controller
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.databinding.TrackScoreDialogBinding
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SetTrackScoreDialog<T> : DialogController
        where T : Controller {

    private val item: TrackItem

    private lateinit var listener: Listener

    constructor(target: T, listener: Listener, item: TrackItem) : super(
        bundleOf(KEY_ITEM_TRACK to item.track),
    ) {
        targetController = target
        this.listener = listener
        this.item = item
    }

    @Suppress("unused")
    constructor(bundle: Bundle) : super(bundle) {
        val track = bundle.getSerializable(KEY_ITEM_TRACK) as Track
        val service = Injekt.get<TrackManager>().getService(track.sync_id.toLong())!!
        item = TrackItem(track, service)
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val pickerView = TrackScoreDialogBinding.inflate(LayoutInflater.from(activity!!))
        val np = pickerView.scorePicker

        val scores = item.service.getScoreList().toTypedArray()
        np.maxValue = scores.size - 1
        np.displayedValues = scores

        // Set initial value
        val displayedScore = item.service.displayScore(item.track!!)
        if (displayedScore != "-") {
            val index = scores.indexOf(displayedScore)
            np.value = if (index != -1) index else 0
        }

        return MaterialAlertDialogBuilder(activity!!)
            .setTitle(R.string.score)
            .setView(pickerView.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                np.clearFocus()
                listener.setScore(item, np.value)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    interface Listener {
        fun setScore(item: TrackItem, score: Int)
    }
}

private const val KEY_ITEM_TRACK = "SetTrackScoreDialog.item.track"
