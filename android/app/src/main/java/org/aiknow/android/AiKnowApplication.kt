package org.aiknow.android
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.google.firebase.FirebaseApp
import com.kakao.sdk.common.KakaoSdk
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import org.aiknow.android.data.Gateway
import org.aiknow.android.notifications.PushRegistration
import java.util.concurrent.TimeUnit

class AiKnowApplication : Application(), SingletonImageLoader.Factory {
    lateinit var api: Gateway
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val accountLock = Mutex()
    lateinit var push: PushRegistration
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("daily_news", "오늘의 AI 카드뉴스", NotificationManager.IMPORTANCE_DEFAULT)
        )
        api = Platform.gateway(this)
        if(!Platform.demo) {
            if(BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) KakaoSdk.init(this,BuildConfig.KAKAO_NATIVE_APP_KEY)
            FirebaseApp.initializeApp(this)
        }
        push = PushRegistration(this)
    }
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = {
            // Separate client: no API session cookies or OAuth headers on media requests.
            OkHttpClient.Builder().callTimeout(45,TimeUnit.SECONDS).build()
        })) }
        .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("images").toOkioPath()).maxSizeBytes(160L*1024*1024).build() }
        .build()
}
