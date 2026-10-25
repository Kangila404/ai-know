package org.aiknow.android
import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.aiknow.android.data.*
object Platform {
    const val demo = false
    fun gateway(context: Context): Gateway {
        require(BuildConfig.API_BASE_URL.startsWith("https://")) { "App API requires HTTPS" }
        return HttpGateway(BuildConfig.API_BASE_URL, SessionCookies(BuildConfig.API_BASE_URL.toHttpUrl(),SessionVault(context,BuildConfig.API_BASE_URL)))
    }
}
