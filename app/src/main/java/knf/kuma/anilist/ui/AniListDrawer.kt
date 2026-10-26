package knf.kuma.anilist.ui

import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.bumptech.glide.Glide
import knf.kuma.R
import knf.kuma.anilist.AniListAuth
import knf.kuma.anilist.AniListImportWorker
import knf.kuma.commons.safeShow
import knf.kuma.commons.toast
import kotlinx.coroutines.launch

/** Header del menú lateral: usuario y avatar de AniList, y la nube con los apartados de AniList. */
object AniListDrawer {

    fun bind(activity: AppCompatActivity, header: View, openSettings: () -> Unit) {
        if (header.getTag(R.id.header_avatar) == true) return
        header.setTag(R.id.header_avatar, true)
        val name = header.findViewById<TextView>(R.id.backupLocation)
        val avatar = header.findViewById<ImageView>(R.id.header_avatar)
        AniListAuth.userName.observe(activity) { user ->
            name.text = user ?: "Conectar con AniList"
        }
        AniListAuth.avatarUrl.observe(activity) { url ->
            if (url.isNullOrBlank())
                avatar.setImageResource(R.mipmap.ic_launcher)
            else
                Glide.with(activity).load(url).circleCrop().placeholder(R.mipmap.ic_launcher).into(avatar)
        }
        // Sesiones iniciadas antes de guardar el avatar: lo pedimos una vez.
        if (AniListAuth.isLoggedIn && AniListAuth.avatarUrl.value.isNullOrBlank())
            activity.lifecycleScope.launch { AniListAuth.fetchViewer() }
        val onUserClick = View.OnClickListener {
            if (AniListAuth.isLoggedIn) openSettings() else AniListAuth.launchLogin(activity)
        }
        name.setOnClickListener(onUserClick)
        avatar.setOnClickListener(onUserClick)
        header.findViewById<View>(R.id.action_login).setOnClickListener { showMenu(activity, openSettings) }
    }

    private fun showMenu(activity: AppCompatActivity, openSettings: () -> Unit) {
        if (!AniListAuth.isLoggedIn) {
            AniListAuth.launchLogin(activity)
            return
        }
        val user = AniListAuth.userName.value ?: ""
        val options = listOf("Mi lista", "Perfil en AniList", "Estadísticas", "Sincronizar ahora", "Ajustes de sincronización", "Cerrar sesión")
        MaterialDialog(activity).safeShow {
            title(text = user)
            listItems(items = options) { _, index, _ ->
                when (index) {
                    0 -> AniListActivity.open(activity)
                    1 -> openUrl(activity, "https://anilist.co/user/$user/")
                    2 -> openUrl(activity, "https://anilist.co/user/$user/stats/anime/overview")
                    3 -> {
                        AniListImportWorker.runNow(activity)
                        "Sincronizando con AniList...".toast()
                    }
                    4 -> openSettings()
                    5 -> MaterialDialog(activity).safeShow {
                        message(text = "¿Cerrar sesión de AniList?")
                        positiveButton(text = "Cerrar sesión") { AniListAuth.logout() }
                        negativeButton(text = "Cancelar")
                    }
                }
            }
        }
    }

    fun openUrl(activity: AppCompatActivity, url: String) {
        try {
            CustomTabsIntent.Builder().build().launchUrl(activity, Uri.parse(url))
        } catch (e: Exception) {
            "No se pudo abrir el navegador".toast()
        }
    }
}
