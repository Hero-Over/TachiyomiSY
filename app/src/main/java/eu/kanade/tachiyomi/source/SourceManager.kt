package eu.kanade.tachiyomi.source

import android.content.Context
import eu.kanade.domain.source.interactor.GetSourceData
import eu.kanade.domain.source.interactor.UpsertSourceData
import eu.kanade.domain.source.model.SourceData
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.source.online.all.Hitomi
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.source.online.all.NHentai
import eu.kanade.tachiyomi.source.online.all.PervEden
import eu.kanade.tachiyomi.source.online.english.EightMuses
import eu.kanade.tachiyomi.source.online.english.HBrowse
import eu.kanade.tachiyomi.source.online.english.Pururin
import eu.kanade.tachiyomi.source.online.english.Tsumino
import eu.kanade.tachiyomi.util.lang.launchIO
import exh.log.xLogD
import exh.source.BlacklistedSources
import exh.source.DelegatedHttpSource
import exh.source.EH_SOURCE_ID
import exh.source.EIGHTMUSES_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import exh.source.EnhancedHttpSource
import exh.source.HBROWSE_SOURCE_ID
import exh.source.PERV_EDEN_EN_SOURCE_ID
import exh.source.PERV_EDEN_IT_SOURCE_ID
import exh.source.PURURIN_SOURCE_ID
import exh.source.TSUMINO_SOURCE_ID
import exh.source.handleSourceLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import rx.Observable
import tachiyomi.source.model.ChapterInfo
import tachiyomi.source.model.MangaInfo
import uy.kohesive.injekt.injectLazy
import kotlin.reflect.KClass

class SourceManager(private val context: Context) {

    private val extensionManager: ExtensionManager by injectLazy()
    private val getSourceData: GetSourceData by injectLazy()
    private val upsertSourceData: UpsertSourceData by injectLazy()

    private val sourcesMap = mutableMapOf<Long, Source>()
    private val stubSourcesMap = mutableMapOf<Long, StubSource>()

    private val _catalogueSources: MutableStateFlow<List<CatalogueSource>> = MutableStateFlow(listOf())
    val catalogueSources: Flow<List<CatalogueSource>> = _catalogueSources
    val onlineSources: Flow<List<HttpSource>> =
        _catalogueSources.map { sources -> sources.filterIsInstance<HttpSource>() }

    // SY -->
    private val prefs: PreferencesHelper by injectLazy()

    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    // SY <--

    init {
        createInternalSources().forEach { registerSource(it) }

        // SY -->
        // Create internal sources
        createEHSources().forEach { registerSource(it) }

        // Watch the preference and manage Exhentai
        prefs.enableExhentai().asFlow()
            .drop(1)
            .onEach {
                if (it) {
                    registerSource(EHentai(EXH_SOURCE_ID, true, context))
                } else {
                    sourcesMap.remove(EXH_SOURCE_ID)
                }
            }.launchIn(scope)

        registerSource(MergedSource())
        // SY <--
    }

    fun get(sourceKey: Long): Source? {
        return sourcesMap[sourceKey]
    }

    fun getOrStub(sourceKey: Long): Source {
        return sourcesMap[sourceKey] ?: stubSourcesMap.getOrPut(sourceKey) {
            runBlocking { createStubSource(sourceKey) }
        }
    }

    fun getOnlineSources() = sourcesMap.values.filterIsInstance<HttpSource>()

    fun getCatalogueSources() = sourcesMap.values.filterIsInstance<CatalogueSource>()

    fun getStubSources(): List<StubSource> {
        val onlineSourceIds = getOnlineSources().map { it.id }
        return stubSourcesMap.values.filterNot { it.id in onlineSourceIds }
    }

    // SY -->
    fun getVisibleOnlineSources() = sourcesMap.values.filterIsInstance<HttpSource>().filter {
        it.id !in BlacklistedSources.HIDDEN_SOURCES
    }

    fun getVisibleCatalogueSources() = sourcesMap.values.filterIsInstance<CatalogueSource>().filter {
        it.id !in BlacklistedSources.HIDDEN_SOURCES
    }

    fun getDelegatedCatalogueSources() = sourcesMap.values.filterIsInstance<EnhancedHttpSource>().mapNotNull { enhancedHttpSource ->
        enhancedHttpSource.enhancedSource as? DelegatedHttpSource
    }
    // SY <--

    internal fun registerSource(source: Source) {
        // EXH -->
        val sourceQName = source::class.qualifiedName
        val factories = DELEGATED_SOURCES.entries.filter { it.value.factory }.map { it.value.originalSourceQualifiedClassName }
        val delegate = if (sourceQName != null) {
            val matched = factories.find { sourceQName.startsWith(it) }
            if (matched != null) {
                DELEGATED_SOURCES[matched]
            } else DELEGATED_SOURCES[sourceQName]
        } else null
        val newSource = if (source is HttpSource && delegate != null) {
            xLogD("Delegating source: %s -> %s!", sourceQName, delegate.newSourceClass.qualifiedName)
            val enhancedSource = EnhancedHttpSource(
                source,
                delegate.newSourceClass.constructors.find { it.parameters.size == 2 }!!.call(source, context),
            )

            currentDelegatedSources[enhancedSource.originalSource.id] = DelegatedSource(
                enhancedSource.originalSource.name,
                enhancedSource.originalSource.id,
                enhancedSource.originalSource::class.qualifiedName ?: delegate.originalSourceQualifiedClassName,
                (enhancedSource.enhancedSource as DelegatedHttpSource)::class,
                delegate.factory,
            )
            enhancedSource
        } else source

        if (source.id in BlacklistedSources.BLACKLISTED_EXT_SOURCES) {
            xLogD("Removing blacklisted source: (id: %s, name: %s, lang: %s)!", source.id, source.name, (source as? CatalogueSource)?.lang)
            return
        }
        // EXH <--

        if (!sourcesMap.containsKey(source.id)) {
            sourcesMap[source.id] = newSource
        }
        registerStubSource(source.toSourceData())
        triggerCatalogueSources()
    }

    private fun registerStubSource(sourceData: SourceData) {
        launchIO {
            val dbSourceData = getSourceData.await(sourceData.id)

            if (dbSourceData != sourceData) {
                upsertSourceData.await(sourceData)
            }
            if (stubSourcesMap[sourceData.id]?.toSourceData() != sourceData) {
                stubSourcesMap[sourceData.id] = StubSource(sourceData)
            }
        }
    }

    internal fun unregisterSource(source: Source) {
        sourcesMap.remove(source.id)
        triggerCatalogueSources()
        // SY -->
        currentDelegatedSources.remove(source.id)
        // SY <--
    }

    private fun triggerCatalogueSources() {
        _catalogueSources.update {
            sourcesMap.values.filterIsInstance<CatalogueSource>()
        }
    }

    private fun createInternalSources(): List<Source> = listOf(
        LocalSource(context),
    )

    // SY -->
    private fun createEHSources(): List<Source> {
        val sources = listOf<HttpSource>(
            EHentai(EH_SOURCE_ID, false, context),
        )
        return if (prefs.enableExhentai().get()) {
            sources + EHentai(EXH_SOURCE_ID, true, context)
        } else sources
    }

    // SY <--

    private suspend fun createStubSource(id: Long): StubSource {
        getSourceData.await(id)?.let {
            return StubSource(it)
        }
        extensionManager.getSourceData(id)?.let {
            registerStubSource(it)
            return StubSource(it)
        }
        return StubSource(SourceData(id, "", ""))
    }

    @Suppress("OverridingDeprecatedMember")
    open inner class StubSource(val sourceData: SourceData) : Source {

        override val name: String = sourceData.name

        override val lang: String = sourceData.lang

        override val id: Long = sourceData.id

        override suspend fun getMangaDetails(manga: MangaInfo): MangaInfo {
            throw getSourceNotInstalledException()
        }

        override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
            return Observable.error(getSourceNotInstalledException())
        }

        override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
            throw getSourceNotInstalledException()
        }

        override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
            return Observable.error(getSourceNotInstalledException())
        }

        override suspend fun getPageList(chapter: ChapterInfo): List<tachiyomi.source.model.Page> {
            throw getSourceNotInstalledException()
        }

        override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
            return Observable.error(getSourceNotInstalledException())
        }

        override fun toString(): String {
            if (name.isNotBlank() && lang.isNotBlank()) {
                return "$name (${lang.uppercase()})"
            }
            return id.toString()
        }

        fun getSourceNotInstalledException(): SourceNotInstalledException {
            return SourceNotInstalledException(toString())
        }
    }

    inner class SourceNotInstalledException(val sourceString: String) :
        Exception(context.getString(R.string.source_not_installed, sourceString))

    // SY -->
    companion object {
        private const val fillInSourceId = Long.MAX_VALUE
        val DELEGATED_SOURCES = listOf(
            DelegatedSource(
                "Pururin",
                PURURIN_SOURCE_ID,
                "eu.kanade.tachiyomi.extension.en.pururin.Pururin",
                Pururin::class,
            ),
            DelegatedSource(
                "Tsumino",
                TSUMINO_SOURCE_ID,
                "eu.kanade.tachiyomi.extension.en.tsumino.Tsumino",
                Tsumino::class,
            ),
            DelegatedSource(
                "MangaDex",
                fillInSourceId,
                "eu.kanade.tachiyomi.extension.all.mangadex",
                MangaDex::class,
                true,
            ),
            DelegatedSource(
                "HBrowse",
                HBROWSE_SOURCE_ID,
                "eu.kanade.tachiyomi.extension.en.hbrowse.HBrowse",
                HBrowse::class,
            ),
            DelegatedSource(
                "8Muses",
                EIGHTMUSES_SOURCE_ID,
                "eu.kanade.tachiyomi.extension.en.eightmuses.EightMuses",
                EightMuses::class,
            ),
            DelegatedSource(
                "Hitomi",
                fillInSourceId,
                "eu.kanade.tachiyomi.extension.all.hitomi.Hitomi",
                Hitomi::class,
                true,
            ),
            DelegatedSource(
                "PervEden English",
                PERV_EDEN_EN_SOURCE_ID,
                "eu.kanade.tachiyomi.extension.en.perveden.Perveden",
                PervEden::class,
            ),
            DelegatedSource(
                "PervEden Italian",
                PERV_EDEN_IT_SOURCE_ID,
                "eu.kanade.tachiyomi.extension.it.perveden.Perveden",
                PervEden::class,
            ),
            DelegatedSource(
                "NHentai",
                fillInSourceId,
                "eu.kanade.tachiyomi.extension.all.nhentai.NHentai",
                NHentai::class,
                true,
            ),
        ).associateBy { it.originalSourceQualifiedClassName }

        val currentDelegatedSources = ListenMutableMap(mutableMapOf<Long, DelegatedSource>(), ::handleSourceLibrary)

        data class DelegatedSource(
            val sourceName: String,
            val sourceId: Long,
            val originalSourceQualifiedClassName: String,
            val newSourceClass: KClass<out DelegatedHttpSource>,
            val factory: Boolean = false,
        )
    }

    class ListenMutableMap<K, V>(private val internalMap: MutableMap<K, V>, val listener: () -> Unit) : MutableMap<K, V> {
        override val size: Int
            get() = internalMap.size
        override fun containsKey(key: K): Boolean = internalMap.containsKey(key)
        override fun containsValue(value: V): Boolean = internalMap.containsValue(value)
        override fun get(key: K): V? = internalMap[key]
        override fun isEmpty(): Boolean = internalMap.isEmpty()
        override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
            get() = internalMap.entries
        override val keys: MutableSet<K>
            get() = internalMap.keys
        override val values: MutableCollection<V>
            get() = internalMap.values
        override fun clear() {
            val clearResult = internalMap.clear()
            listener()
            return clearResult
        }

        override fun put(key: K, value: V): V? {
            val putResult = internalMap.put(key, value)
            if (putResult == null) {
                listener()
            }
            return putResult
        }

        override fun putAll(from: Map<out K, V>) {
            internalMap.putAll(from)
            listener()
        }

        override fun remove(key: K): V? {
            val removeResult = internalMap.remove(key)
            if (removeResult != null) {
                listener()
            }
            return removeResult
        }
    }

    // SY <--
}
