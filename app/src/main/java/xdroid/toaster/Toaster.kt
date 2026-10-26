package xdroid.toaster

import androidx.annotation.StringRes
import knf.kuma.App
import knf.kuma.commons.RoundedToast

/**
 * Reemplazo local de com.shamanland:xdroid-toaster con la misma API, para que los 150+ avisos
 * de la app usen el toast redondeado y semitransparente sin tocar cada llamada.
 */
object Toaster {
    @JvmStatic fun toast(text: String?) { if (!text.isNullOrEmpty()) RoundedToast.show(text) }
    @JvmStatic fun toast(@StringRes res: Int) { toast(App.context.getString(res)) }
    @JvmStatic fun toast(format: String, vararg args: Any?) { toast(String.format(format, *args)) }
    @JvmStatic fun toastLong(text: String?) { if (!text.isNullOrEmpty()) RoundedToast.show(text, long = true) }
    @JvmStatic fun toastLong(@StringRes res: Int) { toastLong(App.context.getString(res)) }
    @JvmStatic fun toastLong(format: String, vararg args: Any?) { toastLong(String.format(format, *args)) }
}
