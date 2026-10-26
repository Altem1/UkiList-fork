package knf.kuma.anilist

import org.json.JSONArray
import org.json.JSONObject

/** Consultas GraphQL a AniList. Todas son bloqueantes: llamar fuera del hilo principal. */
object AniListApi {

    data class Media(val id: Int, val titles: List<String>, val episodes: Int?)

    data class Entry(val media: Media, val status: String, val progress: Int)

    private val MEDIA_FIELDS = "id episodes synonyms title { romaji english native }"

    /** Elemento de la lista del usuario para la pantalla AniList (portada, progreso, puntuación). */
    data class ListItem(
        val mediaId: Int,
        val title: String,
        val cover: String?,
        val status: String,
        val progress: Int,
        val episodes: Int?,
        val score: Double,
        val updatedAt: Long
    )

    fun userListDetailed(userId: Int): List<ListItem>? {
        val data = AniListAuth.query(
            "query (\$u: Int) { MediaListCollection(userId: \$u, type: ANIME) { lists { entries { status progress score(format: POINT_10_DECIMAL) updatedAt media { id episodes title { romaji english native } coverImage { large } } } } } }",
            JSONObject().put("u", userId)
        ) ?: return null
        val lists = data.getJSONObject("MediaListCollection").getJSONArray("lists")
        val result = mutableListOf<ListItem>()
        for (i in 0 until lists.length()) {
            val entries = lists.getJSONObject(i).getJSONArray("entries")
            for (j in 0 until entries.length()) {
                val e = entries.getJSONObject(j)
                val m = e.getJSONObject("media")
                result += ListItem(
                    m.getInt("id"),
                    m.getJSONObject("title").let { t -> listOf("romaji", "english", "native").mapNotNull { k -> t.optString(k).takeIf { it.isNotBlank() && it != "null" } }.firstOrNull() ?: "" },
                    m.optJSONObject("coverImage")?.optString("large"),
                    e.getString("status"),
                    e.optInt("progress"),
                    if (m.isNull("episodes")) null else m.getInt("episodes"),
                    e.optDouble("score", 0.0),
                    e.optLong("updatedAt")
                )
            }
        }
        return result.distinctBy { it.mediaId }.sortedByDescending { it.updatedAt }
    }

    private fun JSONObject.toMedia(): Media {
        val title = getJSONObject("title")
        val titles = listOf("romaji", "english", "native").mapNotNull { title.optString(it).takeIf { s -> s.isNotBlank() && s != "null" } } +
                (optJSONArray("synonyms") ?: JSONArray()).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
        return Media(getInt("id"), titles, if (isNull("episodes")) null else getInt("episodes"))
    }

    /** null si falló la conexión; lista vacía si AniList no encontró nada. */
    fun search(title: String): List<Media>? {
        val data = AniListAuth.query(
            "query (\$s: String) { Page(perPage: 8) { media(search: \$s, type: ANIME) { $MEDIA_FIELDS } } }",
            JSONObject().put("s", title)
        ) ?: return null
        val arr = data.getJSONObject("Page").getJSONArray("media")
        return (0 until arr.length()).map { arr.getJSONObject(it).toMedia() }
    }

    fun userList(userId: Int): List<Entry>? {
        val data = AniListAuth.query(
            "query (\$u: Int) { MediaListCollection(userId: \$u, type: ANIME) { lists { entries { status progress media { $MEDIA_FIELDS } } } } }",
            JSONObject().put("u", userId)
        ) ?: return null
        val lists = data.getJSONObject("MediaListCollection").getJSONArray("lists")
        val result = mutableListOf<Entry>()
        for (i in 0 until lists.length()) {
            val entries = lists.getJSONObject(i).getJSONArray("entries")
            for (j in 0 until entries.length()) {
                val e = entries.getJSONObject(j)
                result += Entry(e.getJSONObject("media").toMedia(), e.getString("status"), e.optInt("progress"))
            }
        }
        return result.distinctBy { it.media.id }
    }

    /** Progreso actual del usuario en ese anime (null si no está en su lista) y total de episodios. */
    fun currentProgress(mediaId: Int): Pair<Int?, Int?>? {
        val data = AniListAuth.query(
            "query (\$m: Int) { Media(id: \$m) { episodes mediaListEntry { progress } } }",
            JSONObject().put("m", mediaId)
        )?.optJSONObject("Media") ?: return null
        val progress = data.optJSONObject("mediaListEntry")?.optInt("progress")
        val total = if (data.isNull("episodes")) null else data.getInt("episodes")
        return progress to total
    }

    /** status: CURRENT, COMPLETED, ... Devuelve true si AniList aceptó el cambio. */
    fun saveProgress(mediaId: Int, progress: Int, status: String?): Boolean {
        val vars = JSONObject().put("m", mediaId).put("p", progress)
        status?.let { vars.put("s", it) }
        return AniListAuth.query(
            "mutation (\$m: Int, \$p: Int, \$s: MediaListStatus) { SaveMediaListEntry(mediaId: \$m, progress: \$p, status: \$s) { id progress status } }",
            vars
        )?.optJSONObject("SaveMediaListEntry") != null
    }
}
