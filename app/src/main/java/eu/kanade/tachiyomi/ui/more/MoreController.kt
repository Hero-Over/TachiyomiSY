package eu.kanade.tachiyomi.ui.more

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import eu.kanade.presentation.more.MoreScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.ComposeController
import eu.kanade.tachiyomi.ui.base.controller.NoAppBarElevationController
import eu.kanade.tachiyomi.ui.base.controller.RootController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.category.CategoryController
import eu.kanade.tachiyomi.ui.download.DownloadController
import eu.kanade.tachiyomi.ui.recent.history.HistoryController
import eu.kanade.tachiyomi.ui.recent.updates.UpdatesController
import eu.kanade.tachiyomi.ui.setting.SettingsBackupController
import eu.kanade.tachiyomi.ui.setting.SettingsMainController
import exh.ui.batchadd.BatchAddController

class MoreController :
    ComposeController<MorePresenter>(),
    RootController,
    NoAppBarElevationController {

    override fun getTitle() = resources?.getString(R.string.label_more)

    override fun createPresenter() = MorePresenter()

    @Composable
    override fun ComposeContent(nestedScrollInterop: NestedScrollConnection) {
        MoreScreen(
            nestedScrollInterop = nestedScrollInterop,
            presenter = presenter,
            onClickDownloadQueue = { router.pushController(DownloadController()) },
            onClickCategories = { router.pushController(CategoryController()) },
            onClickBackupAndRestore = { router.pushController(SettingsBackupController()) },
            onClickSettings = { router.pushController(SettingsMainController()) },
            onClickAbout = { router.pushController(AboutController()) },
            // SY -->
            onClickBatchAdd = { router.pushController(BatchAddController()) },
            onClickUpdates = { router.pushController(UpdatesController()) },
            onClickHistory = { router.pushController(HistoryController()) },
            // SY <--
        )
    }

    companion object {
        const val URL_HELP = "https://tachiyomi.org/help/"
    }
}
