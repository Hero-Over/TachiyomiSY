package eu.kanade.tachiyomi.ui.library

import android.app.Dialog
import android.os.Bundle
import com.bluelinelabs.conductor.Controller
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.isLocal
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.DialogController

class DeleteLibraryMangasDialog<T>(bundle: Bundle? = null) :
    DialogController(bundle) where T : Controller, T : DeleteLibraryMangasDialog.Listener {

    private var mangas = emptyList<Manga>()

    constructor(target: T, mangas: List<Manga>) : this() {
        this.mangas = mangas
        targetController = target
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val canDeleteChapters = mangas.any { !it.isLocal() }
        val items = when (canDeleteChapters) {
            true -> listOf(
                R.string.manga_from_library,
                R.string.downloaded_chapters,
            )
            false -> listOf(R.string.manga_from_library)
        }
            .map { resources!!.getString(it) }
            .toTypedArray()

        val selected = items
            .map { false }
            .toBooleanArray()
        return MaterialAlertDialogBuilder(activity!!)
            .setTitle(R.string.action_remove)
            .setMultiChoiceItems(items, selected) { _, which, checked ->
                selected[which] = checked
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val deleteFromLibrary = selected[0]
                val deleteChapters = canDeleteChapters && selected[1]
                (targetController as? Listener)?.deleteMangas(mangas, deleteFromLibrary, deleteChapters)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    interface Listener {
        fun deleteMangas(mangas: List<Manga>, deleteFromLibrary: Boolean, deleteChapters: Boolean)
    }
}
