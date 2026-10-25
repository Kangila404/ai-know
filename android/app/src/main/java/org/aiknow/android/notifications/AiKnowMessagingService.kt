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
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("daily_news","오늘의 AI 카드뉴스",NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if(id != null) intent.putExtra("cardNewsId",id.toString())
        val pending = PendingIntent.getActivity(this,id?.hashCode() ?: 0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(id?.hashCode() ?: 0,NotificationCompat.Builder(this,"daily_news").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(message.notification?.title ?: "AI KNOW")
            .setContentText(message.notification?.body ?: message.data["title"] ?: "오늘의 AI 소식을 확인하세요.")
            .setAutoCancel(true).setContentIntent(pending).build())
    }
}
