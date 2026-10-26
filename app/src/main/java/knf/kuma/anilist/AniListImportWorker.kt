package knf.kuma.anilist

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import knf.kuma.R
import knf.kuma.database.CacheDB
import knf.kuma.pojos.av1.Organizer
import knf.kuma.retrofit.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.floor

/** Importa la lista de AniList a la app: estado de seguimiento + episodios vistos. Nunca borra nada local. */
class AniListImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!AniListAuth.isLoggedIn || !AniListSync.isLibrarySyncEnabled) return@withContext Result.success()
        if (AniListAuth.userId == -1) AniListAuth.fetchViewer()
        val entries = AniListApi.userList(AniListAuth.userId) ?: return@withContext Result.retry()
        val organizerDAO = CacheDB.INSTANCE.organizerDAO()
        val recordDAO = CacheDB.INSTANCE.recordAV1DAO()
        var imported = 0
        var unmatched = 0
        for ((index, entry) in entries.withIndex()) {
            progress(index + 1, entries.size, entry.media.titles.first())
            try {
                val mapping = AniListMatcher.findAppAnime(entry.media)
                if (mapping == null) {
                    unmatched++
                    continue
                }
                val state = stateFor(entry.status)
                val current = organizerDAO.getByAid(mapping.aid)?.organizer
                val episodes = if (entry.status == "COMPLETED") Int.MAX_VALUE else entry.progress
                if (episodes > 0) {
                    val dir = Repository.getDirectory(mapping.slug)
                    if (dir != null) {
                        val seen = dir.chapters.filter { it.number <= episodes }
                        seen.forEach { recordDAO.addChapterIgnore(it.asRecord(dir)) }
                        CacheDB.INSTANCE.aniListScrobbleDAO().addIgnore(seen.map {
                            AniListScrobbleState(dir.slug, floor(it.number).toInt(), dir.aid, dir.name, AniListScrobbleState.SYNCED)
                        })
                        if (state != null && current?.state != state)
                            organizerDAO.add(dir.asOrganizer(state))
                    }
                } else if (state != null && current == null) {
                    organizerDAO.add(Organizer(mapping.aid, entry.media.titles.first(), mapping.slug, -1, state))
                }
                imported++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        Log.i("AniList", "Importados $imported, sin coincidencia $unmatched de ${entries.size}")
        val unmatchedStr = if (unmatched > 0) " " + applicationContext.getString(R.string.anilist_import_unmatched, unmatched) else ""
        AniListSync.lastImport = applicationContext.getString(R.string.anilist_import_summary, imported, entries.size) + unmatchedStr
        notify(builder().setContentTitle(applicationContext.getString(R.string.anilist_import_complete_title))
            .setContentText(AniListSync.lastImport).setOngoing(false).setAutoCancel(true).build())
        Result.success()
    }

    private fun builder() = NotificationCompat.Builder(applicationContext, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)

    /** Muestra "Importando X de Y" y mantiene el trabajo en primer plano para que Android no lo corte. */
    private suspend fun progress(current: Int, total: Int, title: String) {
        val notification = builder()
            .setContentTitle(applicationContext.getString(R.string.anilist_import_progress_title, current, total))
            .setContentText(title)
            .setProgress(total, current, false)
            .setOngoing(true)
            .build()
        try {
            setForeground(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                else ForegroundInfo(NOTIFICATION_ID, notification)
            )
        } catch (e: Exception) {
            // En segundo plano Android 12+ puede negar el primer plano: seguimos sin él.
            notify(notification)
        }
    }

    private fun notify(notification: android.app.Notification) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, applicationContext.getString(R.string.anilist_channel_name), NotificationManager.IMPORTANCE_LOW))
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    /** Estados de Organizer: 1 viendo, 2 considerando, 3 completado, 4 abandonado, 5 pausado. */
    private fun stateFor(status: String): Int? = when (status) {
        "CURRENT", "REPEATING" -> 1
        "PLANNING" -> 2
        "COMPLETED" -> 3
        "DROPPED" -> 4
        "PAUSED" -> 5
        else -> null
    }

    companion object {
        private const val NAME = "anilist-import"
        private const val CHANNEL = "anilist_sync"
        private const val NOTIFICATION_ID = 7006

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<AniListImportWorker>().setConstraints(network).build()
            )
        }

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "$NAME-periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AniListImportWorker>(12, TimeUnit.HOURS).setConstraints(network).build()
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("$NAME-periodic")
        }

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    }
}

/** Preferencias de sincronización (claves de los switches en Ajustes). */
object AniListSync {
    const val KEY_LIBRARY = "anilist_sync_library"
    const val KEY_SCROBBLE = "anilist_auto_scrobble"
    private const val KEY_LAST_IMPORT = "anilist_last_import"

    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(knf.kuma.App.context)

    val isLibrarySyncEnabled get() = prefs.getBoolean(KEY_LIBRARY, false)
    val isScrobbleEnabled get() = prefs.getBoolean(KEY_SCROBBLE, false)

    var lastImport: String?
        get() = prefs.getString(KEY_LAST_IMPORT, null)
        set(value) = prefs.edit().putString(KEY_LAST_IMPORT, value).apply()
}
