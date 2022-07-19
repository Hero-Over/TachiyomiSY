package eu.kanade.tachiyomi.data.backup.full

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.AbstractBackupRestore
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.full.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.full.models.BackupFlatMetadata
import eu.kanade.tachiyomi.data.backup.full.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.full.models.BackupManga
import eu.kanade.tachiyomi.data.backup.full.models.BackupMergedMangaReference
import eu.kanade.tachiyomi.data.backup.full.models.BackupSavedSearch
import eu.kanade.tachiyomi.data.backup.full.models.BackupSerializer
import eu.kanade.tachiyomi.data.backup.full.models.BackupSource
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import exh.EXHMigrations
import exh.source.MERGED_SOURCE_ID
import okio.buffer
import okio.gzip
import okio.source
import java.util.Date

class FullBackupRestore(context: Context, notifier: BackupNotifier) : AbstractBackupRestore<FullBackupManager>(context, notifier) {

    override suspend fun performRestore(uri: Uri): Boolean {
        backupManager = FullBackupManager(context)

        @Suppress("BlockingMethodInNonBlockingContext")
        val backupString = context.contentResolver.openInputStream(uri)!!.source().gzip().buffer().use { it.readByteArray() }
        val backup = backupManager.parser.decodeFromByteArray(BackupSerializer, backupString)

        restoreAmount = backup.backupManga.size + 1 /* SY --> */ + 1 /* SY <-- */ // +1 for categories, +1 for saved searches

        // Restore categories
        if (backup.backupCategories.isNotEmpty()) {
            restoreCategories(backup.backupCategories)
        }

        // SY -->
        if (backup.backupSavedSearches.isNotEmpty()) {
            restoreSavedSearches(backup.backupSavedSearches)
        }
        // SY <--

        // Store source mapping for error messages
        val backupMaps = backup.backupBrokenSources.map { BackupSource(it.name, it.sourceId) } + backup.backupSources
        sourceMapping = backupMaps.associate { it.sourceId to it.name }

        // Restore individual manga, sort by merged source so that merged source manga go last and merged references get the proper ids
        backup.backupManga /* SY --> */.sortedBy { it.source == MERGED_SOURCE_ID } /* SY <-- */.forEach {
            if (job?.isActive != true) {
                return false
            }

            restoreManga(it, backup.backupCategories)
        }

        // TODO: optionally trigger online library + tracker update

        return true
    }

    private suspend fun restoreCategories(backupCategories: List<BackupCategory>) {
        backupManager.restoreCategories(backupCategories)

        restoreProgress += 1
        showRestoreProgress(restoreProgress, restoreAmount, context.getString(R.string.categories))
    }

    // SY -->
    private suspend fun restoreSavedSearches(backupSavedSearches: List<BackupSavedSearch>) {
        backupManager.restoreSavedSearches(backupSavedSearches)

        restoreProgress += 1
        showRestoreProgress(restoreProgress, restoreAmount, context.getString(R.string.saved_searches))
    }
    // SY <--

    private suspend fun restoreManga(backupManga: BackupManga, backupCategories: List<BackupCategory>) {
        val manga = backupManga.getMangaImpl()
        val chapters = backupManga.getChaptersImpl()
        val categories = backupManga.categories.map { it.toInt() }
        val history = backupManga.brokenHistory.map { BackupHistory(it.url, it.lastRead) } + backupManga.history
        val tracks = backupManga.getTrackingImpl()
        // SY -->
        val mergedMangaReferences = backupManga.mergedMangaReferences
        val flatMetadata = backupManga.flatMetadata
        val customManga = backupManga.getCustomMangaInfo()
        // SY <--

        // SY -->
        EXHMigrations.migrateBackupEntry(manga)
        // SY <--

        try {
            restoreMangaData(manga, chapters, categories, history, tracks, backupCategories/* SY --> */, mergedMangaReferences, flatMetadata, customManga/* SY <-- */)
        } catch (e: Exception) {
            val sourceName = sourceMapping[manga.source] ?: manga.source.toString()
            errors.add(Date() to "${manga.title} [$sourceName]: ${e.message}")
        }

        restoreProgress += 1
        showRestoreProgress(restoreProgress, restoreAmount, manga.title)
    }

    /**
     * Returns a manga restore observable
     *
     * @param manga manga data from json
     * @param chapters chapters data from json
     * @param categories categories data from json
     * @param history history data from json
     * @param tracks tracking data from json
     */
    private suspend fun restoreMangaData(
        manga: Manga,
        chapters: List<Chapter>,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
        // SY -->
        mergedMangaReferences: List<BackupMergedMangaReference>,
        flatMetadata: BackupFlatMetadata?,
        customManga: CustomMangaManager.MangaJson?,
        // SY -->
    ) {
        val dbManga = backupManager.getMangaFromDatabase(manga.url, manga.source)
        if (dbManga == null) {
            // Manga not in database
            restoreMangaFetch(manga, chapters, categories, history, tracks, backupCategories/* SY --> */, mergedMangaReferences, flatMetadata, customManga/* SY <-- */)
        } else {
            // Manga in database
            // Copy information from manga already in database
            backupManager.restoreMangaNoFetch(manga, dbManga)
            // Fetch rest of manga information
            restoreMangaNoFetch(manga, chapters, categories, history, tracks, backupCategories/* SY --> */, mergedMangaReferences, flatMetadata, customManga/* SY <-- */)
        }
    }

    /**
     * Fetches manga information
     *
     * @param manga manga that needs updating
     * @param chapters chapters of manga that needs updating
     * @param categories categories that need updating
     */
    private suspend fun restoreMangaFetch(
        manga: Manga,
        chapters: List<Chapter>,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
        // SY -->
        mergedMangaReferences: List<BackupMergedMangaReference>,
        flatMetadata: BackupFlatMetadata?,
        customManga: CustomMangaManager.MangaJson?,
        // SY <--
    ) {
        try {
            val fetchedManga = backupManager.restoreManga(manga)
            fetchedManga.id ?: return
            backupManager.restoreChaptersForManga(fetchedManga, chapters)

            restoreExtraForManga(fetchedManga, categories, history, tracks, backupCategories /* SY --> */, mergedMangaReferences, flatMetadata, customManga/* SY <-- */)
        } catch (e: Exception) {
            errors.add(Date() to "${manga.title} - ${e.message}")
        }
    }

    private suspend fun restoreMangaNoFetch(
        backupManga: Manga,
        chapters: List<Chapter>,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
        // SY -->
        mergedMangaReferences: List<BackupMergedMangaReference>,
        flatMetadata: BackupFlatMetadata?,
        customManga: CustomMangaManager.MangaJson?,
        // SY <--
    ) {
        backupManager.restoreChaptersForManga(backupManga, chapters)

        restoreExtraForManga(backupManga, categories, history, tracks, backupCategories/* SY --> */, mergedMangaReferences, flatMetadata, customManga/* SY <-- */)
    }

    private suspend fun restoreExtraForManga(
        manga: Manga,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
        // SY -->
        mergedMangaReferences: List<BackupMergedMangaReference>,
        flatMetadata: BackupFlatMetadata?,
        customManga: CustomMangaManager.MangaJson?,
        // SY <--
    ) {
        // Restore categories
        backupManager.restoreCategoriesForManga(manga, categories, backupCategories)

        // Restore history
        backupManager.restoreHistoryForManga(history)

        // Restore tracking
        backupManager.restoreTrackForManga(manga, tracks)

        // SY -->
        // Restore merged manga references if its a merged manga
        backupManager.restoreMergedMangaReferencesForManga(manga.id!!, mergedMangaReferences)

        // Restore flat metadata for metadata sources
        flatMetadata?.let { backupManager.restoreFlatMetadata(manga.id!!, it) }

        // Restore Custom Info
        customManga?.id = manga.id!!
        customManga?.let { customMangaManager.saveMangaInfo(it) }
        // SY <--
    }
}
