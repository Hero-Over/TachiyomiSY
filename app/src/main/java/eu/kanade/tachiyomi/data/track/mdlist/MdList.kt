package eu.kanade.tachiyomi.data.track.mdlist

import android.content.Context
import android.graphics.Color
import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.toMangaInfo
import eu.kanade.tachiyomi.util.lang.awaitSingle
import eu.kanade.tachiyomi.util.lang.runAsObservable
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.md.utils.FollowStatus
import exh.md.utils.MdUtil
import tachiyomi.source.model.MangaInfo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MdList(private val context: Context, id: Long) : TrackService(id) {

    private val mdex by lazy { MdUtil.getEnabledMangaDex(Injekt.get()) }

    @StringRes
    override fun nameRes(): Int = R.string.mdlist

    override fun getLogo(): Int {
        return R.drawable.ic_tracker_mangadex_logo
    }

    override fun getLogoColor(): Int {
        return Color.rgb(43, 48, 53)
    }

    override fun getStatusList(): List<Int> {
        return FollowStatus.values().map { it.int }
    }

    override fun getStatus(status: Int): String =
        context.resources.getStringArray(R.array.md_follows_options).asList()[status]

    override fun getScoreList() = IntRange(0, 10).map(Int::toString)

    override fun displayScore(track: Track) = track.score.toInt().toString()

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        return withIOContext {
            val mdex = mdex ?: throw MangaDexNotFoundException()

            val remoteTrack = mdex.fetchTrackingInfo(track.tracking_url)
            val followStatus = FollowStatus.fromInt(track.status)

            // this updates the follow status in the metadata
            // allow follow status to update
            if (remoteTrack.status != followStatus.int) {
                if (mdex.updateFollowStatus(MdUtil.getMangaId(track.tracking_url), followStatus)) {
                    remoteTrack.status = followStatus.int
                } else {
                    track.status = remoteTrack.status
                }
            }

            if (remoteTrack.score != track.score) {
                mdex.updateRating(track)
            }

            // mangadex wont update chapters if manga is not follows this prevents unneeded network call

            /*if (followStatus != FollowStatus.UNFOLLOWED) {
                if (track.total_chapters != 0 && track.last_chapter_read == track.total_chapters) {
                    track.status = FollowStatus.COMPLETED.int
                    mdex.updateFollowStatus(MdUtil.getMangaId(track.tracking_url), FollowStatus.COMPLETED)
                }
                if (followStatus == FollowStatus.PLAN_TO_READ && track.last_chapter_read > 0) {
                    val newFollowStatus = FollowStatus.READING
                    track.status = FollowStatus.READING.int
                    mdex.updateFollowStatus(MdUtil.getMangaId(track.tracking_url), newFollowStatus)
                    remoteTrack.status = newFollowStatus.int
                }

                mdex.updateReadingProgress(track)
            } else if (track.last_chapter_read != 0) {
                // When followStatus has been changed to unfollowed 0 out read chapters since dex does
                track.last_chapter_read = 0
            }*/
            track
        }
    }

    override fun getCompletionStatus(): Int = FollowStatus.COMPLETED.int

    override fun getReadingStatus(): Int = FollowStatus.READING.int

    override fun getRereadingStatus(): Int = FollowStatus.RE_READING.int

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track = update(
        refresh(track).also {
            if (it.status == FollowStatus.UNFOLLOWED.int) {
                it.status = if (hasReadChapters) {
                    FollowStatus.READING.int
                } else FollowStatus.PLAN_TO_READ.int
            }
        },
    )

    override suspend fun refresh(track: Track): Track {
        return withIOContext {
            val mdex = mdex ?: throw MangaDexNotFoundException()
            val remoteTrack = mdex.fetchTrackingInfo(track.tracking_url)
            track.copyPersonalFrom(remoteTrack)
            /*if (track.total_chapters == 0 && mangaMetadata.status == SManga.COMPLETED) {
                track.total_chapters = mangaMetadata.maxChapterNumber ?: 0
            }*/
            track
        }
    }

    fun createInitialTracker(dbManga: Manga, mdManga: Manga = dbManga): Track {
        return Track.create(TrackManager.MDLIST).apply {
            manga_id = dbManga.id!!
            status = FollowStatus.UNFOLLOWED.int
            tracking_url = MdUtil.baseUrl + mdManga.url
            title = mdManga.title
        }
    }

    override suspend fun search(query: String): List<TrackSearch> {
        return withIOContext {
            val mdex = mdex ?: throw MangaDexNotFoundException()
            mdex.fetchSearchManga(1, query, FilterList())
                .flatMap { page ->
                    runAsObservable {
                        page.mangas.map {
                            toTrackSearch(mdex.getMangaDetails(it.toMangaInfo()))
                        }
                    }
                }
                .awaitSingle()
        }
    }

    private fun toTrackSearch(mangaInfo: MangaInfo): TrackSearch = TrackSearch.create(TrackManager.MDLIST).apply {
        tracking_url = MdUtil.baseUrl + mangaInfo.key
        title = mangaInfo.title
        cover_url = mangaInfo.cover
        summary = mangaInfo.description
    }

    override suspend fun login(username: String, password: String): Unit = throw Exception("not used")

    override fun logout() {
        super.logout()
        preferences.trackToken(this).delete()
    }

    class MangaDexNotFoundException : Exception("Mangadex not enabled")
}
