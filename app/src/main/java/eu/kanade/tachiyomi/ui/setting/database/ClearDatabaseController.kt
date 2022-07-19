package eu.kanade.tachiyomi.ui.setting.database

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import dev.chrisbanes.insetter.applyInsetter
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.Payload
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ClearDatabaseControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.FabController
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.util.system.toast

class ClearDatabaseController :
    NucleusController<ClearDatabaseControllerBinding, ClearDatabasePresenter>(),
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnUpdateListener,
    FabController {

    private var recycler: RecyclerView? = null
    private var adapter: FlexibleAdapter<ClearDatabaseSourceItem>? = null

    private var menu: Menu? = null

    private var actionFab: ExtendedFloatingActionButton? = null

    init {
        setHasOptionsMenu(true)
    }

    override fun createBinding(inflater: LayoutInflater): ClearDatabaseControllerBinding {
        return ClearDatabaseControllerBinding.inflate(inflater)
    }

    override fun createPresenter(): ClearDatabasePresenter {
        return ClearDatabasePresenter()
    }

    override fun getTitle(): String? {
        return activity?.getString(R.string.pref_clear_database)
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        binding.recycler.applyInsetter {
            type(navigationBars = true) {
                padding()
            }
        }

        adapter = FlexibleAdapter<ClearDatabaseSourceItem>(null, this, true)
        binding.recycler.adapter = adapter
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.setHasFixedSize(true)
        adapter?.fastScroller = binding.fastScroller
        recycler = binding.recycler
    }

    override fun onDestroyView(view: View) {
        adapter = null
        super.onDestroyView(view)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.generic_selection, menu)
        this.menu = menu
        menu.forEach { menuItem -> menuItem.isVisible = (adapter?.itemCount ?: 0) > 0 }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val adapter = adapter ?: return false
        when (item.itemId) {
            R.id.action_select_all -> adapter.selectAll()
            R.id.action_select_inverse -> {
                adapter.currentItems.forEachIndexed { index, _ ->
                    adapter.toggleSelection(index)
                }
            }
        }
        updateFab()
        adapter.notifyItemRangeChanged(0, adapter.itemCount, Payload.SELECTION)
        return super.onOptionsItemSelected(item)
    }

    override fun onUpdateEmptyView(size: Int) {
        if (size > 0) {
            binding.emptyView.hide()
        } else {
            binding.emptyView.show(activity!!.getString(R.string.database_clean))
        }

        menu?.forEach { menuItem -> menuItem.isVisible = size > 0 }
    }

    override fun onItemClick(view: View?, position: Int): Boolean {
        val adapter = adapter ?: return false
        adapter.toggleSelection(position)
        adapter.notifyItemChanged(position, Payload.SELECTION)
        updateFab()
        return true
    }

    fun setItems(items: List<ClearDatabaseSourceItem>) {
        adapter?.updateDataSet(items)
    }

    override fun configureFab(fab: ExtendedFloatingActionButton) {
        fab.setIconResource(R.drawable.ic_delete_24dp)
        fab.setText(R.string.action_delete)
        fab.hide()
        fab.setOnClickListener {
            val ctrl = ClearDatabaseSourcesDialog()
            ctrl.targetController = this
            ctrl.showDialog(router)
        }
        actionFab = fab
    }

    private fun updateFab() {
        val adapter = adapter ?: return
        if (adapter.selectedItemCount > 0) {
            actionFab?.show()
        } else {
            actionFab?.hide()
        }
    }

    override fun cleanupFab(fab: ExtendedFloatingActionButton) {
        actionFab?.setOnClickListener(null)
        actionFab = null
    }

    class ClearDatabaseSourcesDialog : DialogController() {
        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val item = arrayOf(
                activity!!.getString(R.string.clear_database_confirmation),
                activity!!.getString(R.string.clear_db_exclude_read),
            )
            val selected = booleanArrayOf(true, true)
            return MaterialAlertDialogBuilder(activity!!)
                // .setMessage(R.string.clear_database_confirmation)
                // SY -->
                .setMultiChoiceItems(item, selected) { _, which, checked ->
                    if (which == 0) {
                        (dialog as AlertDialog).listView.setItemChecked(which, true)
                    } else {
                        selected[which] = checked
                    }
                }
                // SY <--
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    (targetController as? ClearDatabaseController)?.clearDatabaseForSelectedSources(selected.last())
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun clearDatabaseForSelectedSources(/* SY --> */ keepReadManga: Boolean/* SY <-- */) {
        val adapter = adapter ?: return
        val selectedSourceIds = adapter.selectedPositions.mapNotNull { position ->
            adapter.getItem(position)?.source?.id
        }
        presenter.clearDatabaseForSourceIds(selectedSourceIds, /* SY --> */ keepReadManga /* SY <-- */)
        actionFab!!.isVisible = false
        adapter.clearSelection()
        adapter.notifyDataSetChanged()
        activity?.toast(R.string.clear_database_completed)
    }
}
