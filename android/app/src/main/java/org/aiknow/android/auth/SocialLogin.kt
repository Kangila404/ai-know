package org.aiknow.android.auth
import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.kakao.sdk.user.UserApiClient
import com.kakao.sdk.auth.TokenManagerProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import org.aiknow.android.BuildConfig
import org.aiknow.android.data.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SocialLogin(private val api: Gateway) {
    suspend fun login(activity: Activity, provider: String) {
        val nonce = api.request("api/v1/auth/social/$provider/challenge","POST").text("nonce")
        require(nonce.isNotBlank()) { "로그인 요청을 확인하지 못했습니다." }
        val token = when(provider) {
            "GOOGLE" -> {
                check(BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) { "Google 로그인 설정이 필요합니다." }
                val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).setNonce(nonce).build()
                val response = try {
                    CredentialManager.create(activity).getCredential(activity,GetCredentialRequest.Builder().addCredentialOption(option).build())
                } catch (e: NoCredentialException) {
                    throw IllegalStateException("로그인할 Google 계정이 없습니다. 기기에 계정을 추가한 뒤 다시 시도해 주세요.", e)
                }
                GoogleIdTokenCredential.createFrom(response.credential.data).idToken
            }
            "KAKAO" -> {
                check(BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) { "카카오 로그인 설정이 필요합니다." }
                suspendCancellableCoroutine<String> { c ->
                    UserApiClient.instance.loginWithKakaoAccount(activity,nonce = nonce) { token, error ->
                        if(c.isActive) {
                            if(error != null) c.resumeWithException(error)
                            else if(!token?.idToken.isNullOrBlank()) c.resume(token!!.idToken!!)
                            else c.resumeWithException(IllegalStateException("카카오 OpenID Connect 설정을 확인해 주세요."))
                        }
                    }
                }
            }
            else -> error("지원하지 않는 로그인 방식입니다.")
        }
        api.request("api/v1/auth/social/$provider","POST",payload("idToken" to token))
    }
    suspend fun clear(activity: Activity) {
        runCatching { CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest()) }
        if(BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) TokenManagerProvider.instance.manager.clear()
    }
}
