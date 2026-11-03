package org.aiknow.android.notifications
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.aiknow.android.*

class AiKnowMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val app = application as AiKnowApplication
        app.scope.launch { app.accountLock.withLock { runCatching {
            app.api.request("api/v1/users")
            app.push.sync()
        } } }
    }
    override fun onMessageReceived(message: RemoteMessage) {
        val app = application as AiKnowApplication
        if(!app.push.permitted()) return
        val id = message.data["cardNewsId"]?.toLongOrNull()?.takeIf { it > 0 }
        val adminId = message.data["adminAlertId"]?.toLongOrNull()?.takeIf { it > 0 }
        val channel = if(adminId != null) "admin_operations" else "daily_news"
        val notificationKey = if(adminId != null) "admin-$adminId" else "card-$id"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("daily_news","오늘의 AI 카드뉴스",NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if(adminId != null) intent.putExtra("adminAlertId",adminId.toString())
        else if(id != null) intent.putExtra("cardNewsId",id.toString())
        val pending = PendingIntent.getActivity(this,notificationKey.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(notificationKey,0,NotificationCompat.Builder(this,channel).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(message.notification?.title ?: "AI KNOW")
            .setContentText(message.notification?.body ?: message.data["title"] ?: "오늘의 AI 소식을 확인하세요.")
            .setAutoCancel(true).setContentIntent(pending).build())
    }
}
