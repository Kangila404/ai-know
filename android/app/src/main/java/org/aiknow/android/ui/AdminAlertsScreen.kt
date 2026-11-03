package org.aiknow.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import org.aiknow.android.AppModel
import org.aiknow.android.data.*

@Composable fun AdminAlertsScreen(model: AppModel, back: () -> Unit, open: (String,Long) -> Unit) {
    val alerts: AdminAlertsModel=viewModel()
    val session=model.sessionGeneration
    fun load(reset: Boolean=false) = alerts.load(model.api,reset,
        isCurrent={ model.admin && model.sessionGeneration==session },
        forbidden={ try { model.refreshUser() } catch(e: CancellationException) { throw e } catch(_: Exception) { } })
    LaunchedEffect(model.admin,session) {
        if(!model.admin) alerts.clear() else if(!alerts.attempted) load()
    }
    if(!model.admin) return
    Page("자동화·발송 알림",back,actions={Refresh{load(true)}}) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {
                Text("콘텐츠 준비부터 발송까지",style=MaterialTheme.typography.headlineSmall)
                Text("발송 후보와 실제 선정 결과는 다를 수 있습니다. 검수 승인은 직접 진행해 주세요.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp))
            }
            items(alerts.rows,key={it.number("id")}) { alert ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        val type=alert.text("type")
                        val failed=type=="AUTOMATION_FAILED"
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Icon(when(type){"DELIVERY_CANDIDATE","DELIVERY_SELECTED"->Icons.Default.Schedule;"AUTOMATION_FAILED"->Icons.Default.ErrorOutline;else->Icons.Default.Notifications},null,tint=if(failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            Text(alert.text("title"),style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                        }
                        Text(alert.text("body"),style=MaterialTheme.typography.bodyMedium)
                        if(alert.text("deliveryDate").isNotBlank()) Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=MaterialTheme.shapes.small) {
                            Row(Modifier.padding(horizontal=10.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarToday,null,Modifier.size(16.dp))
                                Text("대상 날짜 ${alert.text("deliveryDate")}",style=MaterialTheme.typography.labelLarge)
                            }
                        }
                        Text(adminDate(alert.text("createdAt")),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(alert.number("submissionId")>0) TextButton(onClick={open("submissions",alert.number("submissionId"))}) { Text("콘텐츠 검수 열기"); Icon(Icons.Default.ChevronRight,null) }
                        if(alert.number("cardNewsId")>0) TextButton(onClick={open("articles",alert.number("cardNewsId"))}) { Text("카드뉴스 관리 열기"); Icon(Icons.Default.ChevronRight,null) }
                    }
                }
            }
            if(alerts.loading) item { Box(Modifier.fillMaxWidth().padding(16.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()} }
            else if(alerts.error != null) item {
                Text(alerts.error!!,color=MaterialTheme.colorScheme.error)
                TextButton(onClick={load(alerts.retryFromStart)}) { Text("다시 시도") }
            }
            else if(alerts.rows.isEmpty()) item { EmptyState("아직 알림이 없습니다","자동화 결과와 발송 후보가 준비되면 여기에 표시됩니다.") }
            else if(!alerts.last) item { OutlinedButton(onClick={load()},modifier=Modifier.fillMaxWidth()) { Text("이전 알림 더 보기") } }
        }
    }
}
