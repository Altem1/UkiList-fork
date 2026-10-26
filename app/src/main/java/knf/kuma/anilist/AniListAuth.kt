package knf.kuma.anilist

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.edit
import androidx.lifecycle.MutableLiveData
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import knf.kuma.App
import knf.kuma.BuildConfig
import knf.kuma.commons.noCrash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object AniListAuth {

    const val REDIRECT_URI = "ukiku://anilist-callback"
    private const val AUTH_URL = "https://anilist.co/api/v2/oauth/authorize"
    const val API_URL = "https://graphql.anilist.co"

    private const val KEY_TOKEN = "access_token"
    private const val KEY_EXPIRES = "expires_at"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"

    val client: OkHttpClient by lazy { OkHttpClient() }

    val userName = MutableLiveData<String?>()

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            "anilist_secure",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            App.context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    init {
        noCrash { userName.postValue(if (isLoggedIn) prefs.getString(KEY_USER_NAME, null) else null) }
    }

    val token: String?
        get() = if (isLoggedIn) prefs.getString(KEY_TOKEN, null) else null

    val userId: Int
        get() = prefs.getInt(KEY_USER_ID, -1)

    val isLoggedIn: Boolean
        get() = prefs.getString(KEY_TOKEN, null) != null &&
                System.currentTimeMillis() < prefs.getLong(KEY_EXPIRES, Long.MAX_VALUE)

    val isConfigured: Boolean
        get() = BuildConfig.ANILIST_CLIENT_ID.isNotBlank()

    fun launchLogin(context: Context) {
        val uri = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", BuildConfig.ANILIST_CLIENT_ID)
            .appendQueryParameter("response_type", "token")
            .build()
        CustomTabsIntent.Builder().build().launchUrl(context, uri)
    }

    /** Implicit grant: ukiku://anilist-callback#access_token=...&expires_in=... */
    fun parseCallback(uri: Uri): Boolean {
        val params = Uri.parse("?" + (uri.fragment ?: return false))
        val accessToken = params.getQueryParameter("access_token") ?: return false
        val expiresIn = params.getQueryParameter("expires_in")?.toLongOrNull()
        prefs.edit {
            putString(KEY_TOKEN, accessToken)
            putLong(KEY_EXPIRES, expiresIn?.let { System.currentTimeMillis() + it * 1000 } ?: Long.MAX_VALUE)
        }
        return true
    }

    suspend fun fetchViewer(): Boolean = withContext(Dispatchers.IO) {
        val data = query("query { Viewer { id name } }") ?: return@withContext false
        val viewer = data.optJSONObject("Viewer") ?: return@withContext false
        prefs.edit {
            putInt(KEY_USER_ID, viewer.getInt("id"))
            putString(KEY_USER_NAME, viewer.getString("name"))
        }
        userName.postValue(viewer.getString("name"))
        true
    }

    fun logout() {
        prefs.edit { clear() }
        userName.postValue(null)
    }

    /** Ejecuta una query/mutación GraphQL y devuelve el objeto "data", o null si falla. Llamar fuera del hilo principal. */
    fun query(query: String, variables: JSONObject = JSONObject()): JSONObject? {
        val body = JSONObject().put("query", query).put("variables", variables).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(API_URL).post(body)
            .header("Accept", "application/json")
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401) logout()
                if (!response.isSuccessful) return null
                JSONObject(response.body.string()).optJSONObject("data")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
