package org.aiknow.android.notifications
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import org.aiknow.android.*
import org.aiknow.android.data.*
import java.util.UUID

class PushRegistration(private val app: AiKnowApplication) {
    private val prefs = app.getSharedPreferences("installation",0)
    private val installationId = prefs.getString("id",null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("id",it).apply() }
    val configured get() = !Platform.demo && FirebaseApp.getApps(app).isNotEmpty()
    fun permitted(): Boolean = (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(app,Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(app).areNotificationsEnabled()
    suspend fun sync() {
        if(!configured || !permitted()) return
        val token = FirebaseMessaging.getInstance().token.await()
        app.api.request("api/v1/device-tokens","PUT",payload("token" to token,"platform" to "ANDROID","installationId" to installationId))
        prefs.edit().putString("registeredToken",token).apply()
    }
    suspend fun unregister() {
        prefs.getString("registeredToken",null)?.let { app.api.request("api/v1/device-tokens","DELETE",payload("token" to it)) }
        prefs.edit().remove("registeredToken").apply()
    }
}
