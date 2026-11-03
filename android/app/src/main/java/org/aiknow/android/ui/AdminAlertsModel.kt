package org.aiknow.android.ui

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import org.aiknow.android.data.*

/** Owned by the alerts navigation entry, so opening a detail doesn't discard its pages. */
class AdminAlertsModel : ViewModel() {
    var rows by mutableStateOf<List<JsonElement>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var last by mutableStateOf(false); private set
    var attempted by mutableStateOf(false); private set
    var retryFromStart by mutableStateOf(false); private set
    private var nextPage=0
    private var generation=0
    private var request: Job?=null

    fun clear() {
        generation++; request?.cancel(); request=null
        rows=emptyList(); loading=false; error=null; last=false; nextPage=0; attempted=false; retryFromStart=false
    }

    fun load(api: Gateway, reset: Boolean=false, isCurrent: () -> Boolean, forbidden: suspend () -> Unit) {
        if(!isCurrent()) { clear(); return }
        if(!reset && (loading || last)) return
        if(reset) request?.cancel()
        val token=++generation
        val page=if(reset) 0 else nextPage
        loading=true; error=null; attempted=true; retryFromStart=reset
        request=viewModelScope.launch {
            try {
                val response=api.request("api/v1/admin/alerts",query=mapOf("page" to "$page","size" to "20"))
                if(token != generation || !isCurrent()) return@launch
                rows=((if(reset) emptyList() else rows)+response.rows()).distinctBy { it.number("id") }
                nextPage=page+1; last=response.flag("last") || response.rows().isEmpty(); retryFromStart=false
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) {
                if(token != generation || !isCurrent()) return@launch
                if(e is ApiException && e.status in listOf(401,403)) {
                    rows=emptyList(); nextPage=0; last=false
                    error=e.message
                    forbidden()
                } else error=if(e is ApiException) e.message else "알림을 불러오지 못했습니다. 다시 시도해 주세요."
            } finally { if(token==generation) loading=false }
        }
    }
}
