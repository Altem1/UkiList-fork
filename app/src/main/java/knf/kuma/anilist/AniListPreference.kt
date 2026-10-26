package knf.kuma.anilist

import android.content.Context
import android.util.AttributeSet
import androidx.lifecycle.Observer
import androidx.preference.Preference
import com.afollestad.materialdialogs.MaterialDialog
import knf.kuma.commons.safeShow
import knf.kuma.commons.toast

class AniListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    private val observer = Observer<String?> { name ->
        title = if (name == null) "Conectar con AniList" else "AniList"
        summary = if (name == null) "Sincroniza tu progreso automáticamente" else "Conectado como $name"
    }

    override fun onAttached() {
        super.onAttached()
        AniListAuth.userName.observeForever(observer)
    }

    override fun onDetached() {
        AniListAuth.userName.removeObserver(observer)
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
