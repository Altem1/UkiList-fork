package knf.kuma.anilist

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import knf.kuma.commons.DesignUtils
import knf.kuma.commons.toast
import kotlinx.coroutines.launch

class AniListCallbackActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null || !AniListAuth.parseCallback(uri)) {
            "No se pudo conectar con AniList".toast()
            backToApp()
            return
        }
        lifecycleScope.launch {
            if (AniListAuth.fetchViewer())
                "Conectado a AniList como ${AniListAuth.userName.value ?: ""}".toast()
            else
                "Error al obtener la cuenta de AniList".toast()
            backToApp()
        }
    }

    /** Regresa a la instancia existente de Main, cerrando la Custom Tab que quedó encima. */
    private fun backToApp() {
        startActivity(Intent(this, DesignUtils.mainClass).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        ))
        finish()
    }
}
