package knf.kuma.anilist

import android.net.Uri
import knf.kuma.commons.JsExtractor
import knf.kuma.database.CacheDB
import knf.kuma.pojos.av1.DirectoryAV1Min
import java.text.Normalizer

/** Empareja animes de animeav1 con AniList y cachea el resultado en anilist_mapping. */
object AniListMatcher {

    private val dao get() = CacheDB.INSTANCE.aniListMappingDAO()

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).lowercase().replace(Regex("[^a-z0-9]"), "")

    /** AniList → app. Solo acepta coincidencias exactas de título para no marcar el anime equivocado. */
    suspend fun findAppAnime(media: AniListApi.Media): AniListMapping? {
        dao.byAniList(media.id)?.let { return it }
        val wanted = media.titles.map(::normalize).filter { it.isNotEmpty() }.toSet()
        for (title in media.titles.take(3)) {
            val candidates = searchCatalog(title)
            val match = candidates.firstOrNull { normalize(it.name) in wanted }
                ?: candidates.singleOrNull()?.takeIf { wanted.any { w -> normalize(it.name).startsWith(w) || w.startsWith(normalize(it.name)) } }
            if (match != null)
                return AniListMapping(match.aid, match.slug, media.id).also { dao.add(it) }
        }
        return null
    }

    /** App → AniList. Usa el título del anime (rōmaji en animeav1) para buscar su Media ID. */
    fun findAniListId(aid: Int, slug: String, name: String): Int? {
        dao.byAid(aid)?.let { return it.anilistId }
        val results = AniListApi.search(name)
        val n = normalize(name)
        val match = results.firstOrNull { m -> m.titles.any { normalize(it) == n } } ?: results.firstOrNull()
        return match?.id?.also { dao.add(AniListMapping(aid, slug, it)) }
    }

    private suspend fun searchCatalog(query: String): List<DirectoryAV1Min> {
        val url = Uri.parse("https://animeav1.com/catalogo").buildUpon()
            .appendQueryParameter("search", query).build().toString()
        val result = JsExtractor.processLink(url) ?: return emptyList()
        return (0 until result.length()).mapNotNull { runCatching { DirectoryAV1Min.fromJson(result.getJSONObject(it)) }.getOrNull() }
    }
}
