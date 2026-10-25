package org.aiknow.android
import android.app.Activity
import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.credentials.exceptions.GetCredentialCancellationException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.aiknow.android.auth.SocialLogin
import org.aiknow.android.data.*

class AppModel(application: Application) : AndroidViewModel(application) {
    val app = application as AiKnowApplication
    val api = app.api
    var user by mutableStateOf<JsonElement?>(null); private set
    var starting by mutableStateOf(true); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var pushError by mutableStateOf<String?>(null); private set
    var providers by mutableStateOf<List<String>>(emptyList()); private set
    var pendingCard by mutableStateOf<Long?>(null)
    var contentRevision by mutableIntStateOf(0); private set
    fun contentChanged() { contentRevision++ }
    private val prefs = application.getSharedPreferences("appearance",0)
    var appearance by mutableStateOf(prefs.getString("theme","system") ?: "system"); private set
    val admin get() = user?.text("userRole") == "ADMIN"
    init {
        api.onUnauthorized = { viewModelScope.launch { user = null } }
        restore()
    }
    fun theme(value: String) { appearance=value; prefs.edit().putString("theme",value).apply() }
    fun restore() = viewModelScope.launch {
        starting = true; error = null
        try { user = api.request("api/v1/users") }
        catch(e: ApiException) { if(e.status != 401) error=e.message }
        catch(e: Exception) { error="서버에 연결할 수 없습니다. 네트워크를 확인해 주세요." }
        finally { starting=false }
        loadProviders()
        if(user != null) syncPush()
    }
    private suspend fun loadProviders() {
        try { providers = api.request("api/v1/auth/social/providers").rows().mapNotNull { (it as? JsonPrimitive)?.content }.filter { it in listOf("GOOGLE","KAKAO") } }
        catch(e: Exception) { if(user == null) error="로그인 제공자를 불러오지 못했습니다. 다시 시도해 주세요." }
    }
    fun signIn(activity: Activity, provider: String) = action {
        app.accountLock.withLock {
            if(!Platform.demo) SocialLogin(api).login(activity,provider)
            else api.request("api/v1/auth/demo","POST")
            user=api.request("api/v1/users")
        }
        syncPush()
    }
    suspend fun refreshUser() { user=api.request("api/v1/users") }
    fun refreshAccount() {
        if(user == null || busy) return
        viewModelScope.launch { app.accountLock.withLock {
            if(user != null) try { refreshUser() } catch(_: Exception) { }
        } }
    }
    fun onboarding() = action { user=api.request("api/v1/users/onboarding","PATCH") }
    fun syncPush() = viewModelScope.launch {
        app.accountLock.withLock {
            if(user != null) try { app.push.sync(); pushError=null } catch(_: Exception) { pushError="기기 알림 등록에 실패했습니다. 다시 시도해 주세요." }
        }
    }
    fun signOut(activity: Activity, withdraw: Boolean = false) = action {
        app.accountLock.withLock {
            app.push.unregister()
            if(withdraw) api.request("api/v1/users/me","DELETE") else api.request("api/v1/auth/logout","POST")
            api.clearSession(); user=null; pendingCard=null
            if(!Platform.demo) SocialLogin(api).clear(activity)
        }
    }
    fun action(block: suspend () -> Unit) {
        if(busy) return
        busy=true; error=null
        viewModelScope.launch {
            try { block() }
            catch(_: GetCredentialCancellationException) { }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { error=if(e is ApiException) e.message else "요청을 완료하지 못했습니다. 연결 또는 로그인 설정을 확인해 주세요." }
            finally { busy=false }
        }
    }
}
