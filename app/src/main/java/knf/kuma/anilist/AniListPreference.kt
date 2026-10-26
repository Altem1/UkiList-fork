package knf.kuma.anilist

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import androidx.lifecycle.Observer
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.afollestad.materialdialogs.MaterialDialog
import knf.kuma.commons.safeShow
import knf.kuma.commons.toast
import knf.kuma.database.CacheDB

class AniListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    private val observer = Observer<String?> { name ->
        title = if (name == null) "Conectar con AniList" else "AniList"
        summary = if (name == null) "Sincroniza tu progreso automáticamente" else "Conectado como $name"
        librarySwitch?.isEnabled = name != null
        scrobbleSwitch?.isEnabled = name != null
    }

    private val failedObserver = Observer<Int> { count ->
        exceptionsPref?.isVisible = count > 0
        exceptionsPref?.summary = "$count episodio${if (count == 1) "" else "s"} sin sincronizar"
    }

    private val failedCount by lazy { CacheDB.INSTANCE.aniListScrobbleDAO().failedCountLive() }
    private val exceptionsPref get() = preferenceManager?.findPreference<Preference>("anilist_exceptions")
    private val librarySwitch get() = preferenceManager?.findPreference<SwitchPreference>(AniListSync.KEY_LIBRARY)
    private val scrobbleSwitch get() = preferenceManager?.findPreference<SwitchPreference>(AniListSync.KEY_SCROBBLE)

    override fun onAttached() {
        super.onAttached()
        librarySwitch?.apply {
            AniListSync.lastImport?.let { summary = it }
            setOnPreferenceChangeListener { _, newValue ->
                if (newValue == true) {
                    AniListImportWorker.runNow(context)
                    AniListImportWorker.schedule(context)
                    "Importando tu lista de AniList...".toast()
                } else AniListImportWorker.cancel(context)
                true
            }
        }
        exceptionsPref?.setOnPreferenceClickListener {
            context.startActivity(Intent(context, AniListExceptionsActivity::class.java))
            true
        }
        failedCount.observeForever(failedObserver)
        AniListAuth.userName.observeForever(observer)
    }

    override fun onDetached() {
        AniListAuth.userName.removeObserver(observer)
        failedCount.removeObserver(failedObserver)
        super.onDetached()
    }

    override fun onClick() {
        when {
            !AniListAuth.isConfigured -> "Falta ANILIST_CLIENT_ID en local.properties".toast()
            AniListAuth.isLoggedIn -> MaterialDialog(context).safeShow {
                message(text = "¿Desconectar tu cuenta de AniList?")
                positiveButton(text = "Desconectar") { AniListAuth.logout() }
                negativeButton(text = "Cancelar")
            }
            else -> AniListAuth.launchLogin(context)
        }
    }
}
