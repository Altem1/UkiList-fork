package knf.kuma.anilist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import knf.kuma.commons.toast
import kotlinx.coroutines.launch

class AniListCallbackActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null || !AniListAuth.parseCallback(uri)) {
            "No se pudo conectar con AniList".toast()
            finish()
            return
        }
        lifecycleScope.launch {
            if (AniListAuth.fetchViewer())
                "Conectado a AniList como ${AniListAuth.userName.value ?: ""}".toast()
            else
                "Error al obtener la cuenta de AniList".toast()
            finish()
        }
    }
}
