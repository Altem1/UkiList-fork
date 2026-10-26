package knf.kuma.anilist

import android.util.Log
import knf.kuma.App
import knf.kuma.database.CacheDB
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Collections
import kotlin.math.floor

/**
 * Da estado de sync (nube) a los episodios vistos que no tienen uno: los marcados antes de conectar AniList
 * o por caminos que no hacen scrobble. Se compara con el progreso real en AniList:
 * los que AniList ya tiene quedan sincronizados; si la app va más adelante, se sube el episodio más alto.
 */
object AniListBackfill {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Una revisión por anime y por sesión de la app, para no consultar AniList cada vez que se abre la ficha.
    private val checked = Collections.synchronizedSet(HashSet<Int>())

    fun check(aid: Int, slug: String, name: String) {
        if (!AniListAuth.isLoggedIn || aid == 0 || !checked.add(aid)) return
        scope.launch {
            runCatching { run(aid, slug, name) }.onFailure {
                checked.remove(aid)
                Log.w("AniList", "Backfill falló para $slug", it)
            }
        }
    }

    private fun run(aid: Int, slug: String, name: String) {
        val dao = CacheDB.INSTANCE.aniListScrobbleDAO()
        val seen = CacheDB.INSTANCE.recordAV1DAO().byAid(aid).map { floor(it.number).toInt() }.filter { it > 0 }.toSet()
        val missing = seen - dao.episodes(slug).toSet()
        if (missing.isEmpty()) return
        val mediaId = CacheDB.INSTANCE.aniListMappingDAO().byAid(aid)?.anilistId
            ?: when (val r = AniListMatcher.findAniListId(aid, slug, name)) {
                is AniListMatcher.Lookup.Found -> r.mediaId
                AniListMatcher.Lookup.NotFound -> return
                AniListMatcher.Lookup.NetworkError -> error("sin red")
            }
        val current = (AniListApi.currentProgress(mediaId) ?: error("sin red")).first ?: 0
        Log.i("AniList", "Backfill: $slug vistos=${seen.size} sin estado=${missing.size} progreso AniList=$current")
        dao.addIgnore(missing.map {
            AniListScrobbleState(slug, it, aid, name,
                if (it <= current) AniListScrobbleState.SYNCED else AniListScrobbleState.PENDING)
        })
        val localMax = seen.max()
        if (localMax > current && AniListSync.isScrobbleEnabled)
            AniListScrobbleWorker.enqueue(App.context, aid, slug, name, localMax)
    }
}
