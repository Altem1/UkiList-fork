package knf.kuma.anilist

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import knf.kuma.commons.toast
import knf.kuma.database.CacheDB
import knf.kuma.pojos.av1.ChapterWID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor

private const val EXTRA_AID = "anilist_aid"
private const val EXTRA_SLUG = "anilist_slug"
private const val EXTRA_NAME = "anilist_name"
private const val EXTRA_NUMBER = "anilist_number"

/** Adjunta al intent del reproductor los datos del episodio necesarios para el scrobble. */
fun Intent.withScrobble(chapter: ChapterWID?): Intent = apply {
    chapter ?: return@apply
    putExtra(EXTRA_AID, chapter.aid)
    putExtra(EXTRA_SLUG, chapter.slug)
    putExtra(EXTRA_NAME, chapter.name)
    putExtra(EXTRA_NUMBER, chapter.number)
}

/**
 * Observa un Player y, al llegar al 85% o terminar el video, encola el scrobble en WorkManager
 * (sobrevive aunque el usuario cierre el reproductor de golpe).
 */
class AniListScrobbleTracker(private val context: Context, intent: Intent) : Player.Listener {

    private val aid = intent.getIntExtra(EXTRA_AID, 0)
    private val slug = intent.getStringExtra(EXTRA_SLUG)
    private val name = intent.getStringExtra(EXTRA_NAME)
    private val number = intent.getDoubleExtra(EXTRA_NUMBER, 0.0)

    private val enabled get() = slug != null && name != null && number > 0 &&
            AniListSync.isScrobbleEnabled && AniListAuth.isLoggedIn

    private var player: Player? = null
    private var sent = false
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            check()
            if (!sent) handler.postDelayed(this, 10_000)
        }
    }

    fun attach(player: Player) {
        if (!enabled) return
        this.player = player
        player.addListener(this)
        handler.postDelayed(poll, 10_000)
    }

    /** Llamar antes de liberar el player: hace una última revisión del progreso. */
    fun detach() {
        check()
        handler.removeCallbacks(poll)
        player?.removeListener(this)
        player = null
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) send()
    }

    private fun check() {
        val p = player ?: return
        val duration = p.duration
        if (duration > 0 && p.currentPosition >= duration * 0.85) send()
    }

    private fun send() {
        if (sent || !enabled) return
        sent = true
        AniListScrobbleWorker.enqueue(context, aid, slug!!, name!!, floor(number).toInt())
    }
}

class AniListScrobbleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!AniListAuth.isLoggedIn) return@withContext Result.success()
        val slug = inputData.getString(EXTRA_SLUG) ?: return@withContext Result.failure()
        val aid = inputData.getInt(EXTRA_AID, 0).takeIf { it != 0 }
            ?: CacheDB.INSTANCE.directoryDAO().findBySlug(slug)?.aid
            ?: return@withContext Result.failure()
        val name = inputData.getString(EXTRA_NAME) ?: return@withContext Result.failure()
        val episode = inputData.getInt(EXTRA_NUMBER, 0)
        val mediaId = AniListMatcher.findAniListId(aid, slug, name) ?: return@withContext Result.retry()
        val (current, total) = AniListApi.currentProgress(mediaId) ?: return@withContext Result.retry()
        if (current != null && current >= episode) return@withContext Result.success()
        val status = if (total != null && episode >= total) "COMPLETED" else "CURRENT"
        if (AniListApi.saveProgress(mediaId, episode, status)) {
            withContext(Dispatchers.Main) { "AniList: $name, episodio $episode registrado".toast() }
            Result.success()
        } else Result.retry()
    }

    companion object {
        fun enqueue(context: Context, aid: Int, slug: String, name: String, episode: Int) {
            val request = OneTimeWorkRequestBuilder<AniListScrobbleWorker>()
                .setInputData(workDataOf(EXTRA_AID to aid, EXTRA_SLUG to slug, EXTRA_NAME to name, EXTRA_NUMBER to episode))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("anilist-scrobble-$slug-$episode", ExistingWorkPolicy.KEEP, request)
        }
    }
}
