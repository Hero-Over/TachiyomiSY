package eu.kanade.tachiyomi.source.online

import androidx.compose.runtime.Composable
import eu.kanade.domain.manga.interactor.GetFlatMetadataById
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.InsertFlatMetadata
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.toMangaInfo
import eu.kanade.tachiyomi.ui.manga.MangaScreenState
import eu.kanade.tachiyomi.util.lang.awaitSingle
import eu.kanade.tachiyomi.util.lang.runAsObservable
import exh.metadata.metadata.base.RaisedSearchMetadata
import rx.Completable
import rx.Single
import tachiyomi.source.model.MangaInfo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.reflect.KClass

/**
 * LEWD!
 */
interface MetadataSource<M : RaisedSearchMetadata, I> : CatalogueSource {
    val getManga: GetManga get() = Injekt.get()
    val insertFlatMetadata: InsertFlatMetadata get() = Injekt.get()
    val getFlatMetadataById: GetFlatMetadataById get() = Injekt.get()

    /**
     * The class of the metadata used by this source
     */
    val metaClass: KClass<M>

    /**
     * Parse the supplied input into the supplied metadata object
     */
    suspend fun parseIntoMetadata(metadata: M, input: I)

    /**
     * Use reflection to create a new instance of metadata
     */
    private fun newMetaInstance() = metaClass.constructors.find {
        it.parameters.isEmpty()
    }?.call()
        ?: error("Could not find no-args constructor for meta class: ${metaClass.qualifiedName}!")

    /**
     * Parses metadata from the input and then copies it into the manga
     *
     * Will also save the metadata to the DB if possible
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Use the MangaInfo variant")
    fun parseToManga(manga: SManga, input: I): Completable = runAsObservable {
        parseToManga(manga.toMangaInfo(), input)
    }.toCompletable()

    suspend fun parseToManga(manga: MangaInfo, input: I): MangaInfo {
        val mangaId = manga.id()
        val metadata = if (mangaId != null) {
            val flatMetadata = getFlatMetadataById.await(mangaId)
            flatMetadata?.raise(metaClass) ?: newMetaInstance()
        } else newMetaInstance()

        parseIntoMetadata(metadata, input)
        if (mangaId != null) {
            metadata.mangaId = mangaId
            insertFlatMetadata.await(metadata)
        }

        return metadata.createMangaInfo(manga)
    }

    /**
     * Try to first get the metadata from the DB. If the metadata is not in the DB, calls the input
     * producer and parses the metadata from the input
     *
     * If the metadata needs to be parsed from the input producer, the resulting parsed metadata will
     * also be saved to the DB.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("use fetchOrLoadMetadata made for MangaInfo")
    fun getOrLoadMetadata(mangaId: Long?, inputProducer: () -> Single<I>): Single<M> =
        runAsObservable {
            fetchOrLoadMetadata(mangaId) { inputProducer().toObservable().awaitSingle() }
        }.toSingle()

    /**
     * Try to first get the metadata from the DB. If the metadata is not in the DB, calls the input
     * producer and parses the metadata from the input
     *
     * If the metadata needs to be parsed from the input producer, the resulting parsed metadata will
     * also be saved to the DB.
     */
    suspend fun fetchOrLoadMetadata(mangaId: Long?, inputProducer: suspend () -> I): M {
        val meta = if (mangaId != null) {
            val flatMetadata = getFlatMetadataById.await(mangaId)
            flatMetadata?.raise(metaClass)
        } else {
            null
        }

        return meta ?: inputProducer().let { input ->
            val newMeta = newMetaInstance()
            parseIntoMetadata(newMeta, input)
            if (mangaId != null) {
                newMeta.mangaId = mangaId
                insertFlatMetadata.await(newMeta)
            }
            newMeta
        }
    }

    @Composable
    fun DescriptionComposable(state: MangaScreenState.Success, openMetadataViewer: () -> Unit, search: (String) -> Unit)

    suspend fun MangaInfo.id() = getManga.await(key, id)?.id
    val SManga.id get() = (this as? Manga)?.id
    val SChapter.mangaId get() = (this as? Chapter)?.manga_id
}
