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
import org.aiknow.android.notifications.*

class AppModel(application: Application) : AndroidViewModel(application) {
    val app = application as AiKnowApplication
    val api = app.api
    var user by mutableStateOf<JsonElement?>(null); private set
    var starting by mutableStateOf(true); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var pushError by mutableStateOf<String?>(null); private set
    var providers by mutableStateOf<List<String>>(emptyList()); private set
    var pendingPush by mutableStateOf<PendingPush?>(null); private set
    private var pushSequence = 0L
    fun acceptPush(adminAlertId: String?, cardNewsId: String?) {
        PushDestination.parse(adminAlertId, cardNewsId)?.let { pendingPush=PendingPush(it, ++pushSequence) }
    }
    fun consumePush(push: PendingPush) { if(pendingPush == push) pendingPush=null }
    var contentRevision by mutableIntStateOf(0); private set
    fun contentChanged() { contentRevision++ }
    val admin get() = user?.text("userRole") == "ADMIN"
    var browsingAsGuest by mutableStateOf(false); private set
    var showLogin by mutableStateOf(false)
    var loginReason by mutableStateOf<String?>(null)
    var sessionGeneration by mutableIntStateOf(0); private set
    private val userRefreshLock=kotlinx.coroutines.sync.Mutex()
    fun browseAsGuest() {
        if(user != null || busy) return
        api.clearSession(); pendingPush=null; error=null
        browsingAsGuest=true; showLogin=false; loginReason=null; sessionGeneration++
    }
    fun requireLogin(reason:String):Boolean {
        if(user != null) return true
        if(!showLogin && loginReason==null) loginReason=reason
        return false
    }
    private fun clearAccount() {
        api.clearSession(); user=null; pendingPush=null
        browsingAsGuest=false; showLogin=false; loginReason=null; sessionGeneration++
    }
    init {
        api.onUnauthorized = { viewModelScope.launch { if(user != null) clearAccount() } }
        restore()
    }
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
            browsingAsGuest=false; showLogin=false; loginReason=null; sessionGeneration++
        }
        syncPush()
    }
    suspend fun refreshUser(): Boolean {
        val generation=sessionGeneration
        val account=user?.text("userId") ?: return false
        return userRefreshLock.withLock {
            if(generation != sessionGeneration || user?.text("userId") != account) return@withLock false
            val refreshed=api.request("api/v1/users")
            if(generation != sessionGeneration || user?.text("userId") != account) return@withLock false
            user=refreshed
            true
        }
    }
    suspend fun authorizeAdminPush(): Boolean {
        if(user == null) return false
        val generation=sessionGeneration
        return try {
            val current=refreshUser()
            if(current && !admin) error="관리자 계정으로만 볼 수 있는 알림입니다."
            current && admin
        } catch(e: CancellationException) { throw e }
        catch(_: Exception) {
            if(generation==sessionGeneration && user != null) error="관리자 권한을 확인하지 못했습니다. 연결 상태를 확인하고 다시 시도해 주세요."
            false
        }
    }
    fun refreshAccount() {
        if(user == null || busy) return
        viewModelScope.launch { app.accountLock.withLock {
            if(user != null) try { refreshUser() } catch(e: CancellationException) { throw e } catch(_: Exception) { }
        } }
    }
    fun onboarding(time:String, notifications:Boolean) = action {
        user=completeOnboarding(api,time,notifications)
        if(notifications) syncPush()
    }
    fun syncPush() = viewModelScope.launch {
        app.accountLock.withLock {
            if(user != null) try { app.push.sync(); pushError=null } catch(_: Exception) { pushError="기기 알림 등록에 실패했습니다. 다시 시도해 주세요." }
        }
    }
    fun signOut(activity: Activity, withdraw: Boolean = false) = action {
        app.accountLock.withLock {
            app.push.unregister()
            if(withdraw) api.request("api/v1/users/me","DELETE") else api.request("api/v1/auth/logout","POST")
            clearAccount()
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
