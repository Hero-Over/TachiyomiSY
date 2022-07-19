package eu.kanade.tachiyomi.data.track

import android.content.Context
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.bangumi.Bangumi
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
import eu.kanade.tachiyomi.data.track.komga.Komga
import eu.kanade.tachiyomi.data.track.mangaupdates.MangaUpdates
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori

class TrackManager(context: Context) {

    companion object {
        const val MYANIMELIST = 1L
        const val ANILIST = 2L
        const val KITSU = 3L
        const val SHIKIMORI = 4L
        const val BANGUMI = 5L
        const val KOMGA = 6L
        const val MANGA_UPDATES = 7L

        // SY --> Mangadex from Neko
        const val MDLIST = 60L
        // SY <--
    }

    val mdList = MdList(context, MDLIST)

    val myAnimeList = MyAnimeList(context, MYANIMELIST)
    val aniList = Anilist(context, ANILIST)
    val kitsu = Kitsu(context, KITSU)
    val shikimori = Shikimori(context, SHIKIMORI)
    val bangumi = Bangumi(context, BANGUMI)
    val komga = Komga(context, KOMGA)
    val mangaUpdates = MangaUpdates(context, MANGA_UPDATES)

    val services = listOf(mdList, myAnimeList, aniList, kitsu, shikimori, bangumi, komga, mangaUpdates)

    fun getService(id: Long) = services.find { it.id == id }

    fun hasLoggedServices() = services.any { it.isLogged }
}
