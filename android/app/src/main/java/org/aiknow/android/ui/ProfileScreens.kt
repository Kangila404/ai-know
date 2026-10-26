@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.aiknow.android.ui
import android.Manifest
import androidx.activity.compose.LocalActivity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import org.aiknow.android.AppModel
import org.aiknow.android.Platform
import org.aiknow.android.data.*

@Composable fun ProfileScreen(model: AppModel, navigate:(String)->Unit) {
    val activity=requireNotNull(LocalActivity.current)
    var sheet by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    Page("마이",actions={Refresh{model.refreshAccount()}}) { padding ->
        LazyColumn(Modifier.padding(padding),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(24.dp),horizontalArrangement=Arrangement.spacedBy(20.dp),verticalAlignment=Alignment.CenterVertically) {
                    RemoteImage(model.user?.obj()?.get("profile")?.text("imgUrl"),"프로필 이미지 변경",Modifier.size(72.dp).clickable{sheet="avatar"},true)
                    Column(Modifier.weight(1f)) { TextButton(onClick={sheet="nickname"}){Text("${model.user?.text("nickname")} 님",style=MaterialTheme.typography.titleLarge)};Text("오늘도 한 걸음 더 알아가요",style=MaterialTheme.typography.bodySmall) }
                }
            } }
            item { ListItem(headlineContent={Text("알림 설정")},supportingContent={Text("받고 싶은 시간에 AI 소식 받기")},leadingContent={Icon(Icons.Default.NotificationsNone,null)},modifier=Modifier.clickable{navigate("notifications")}) }
            item { ListItem(headlineContent={Text("공지사항")},leadingContent={Icon(Icons.Default.Campaign,null)},modifier=Modifier.clickable{navigate("notices")}) }
            item { HorizontalDivider(Modifier.padding(vertical=12.dp)) }
            item { TextButton(onClick={confirmation="logout"},enabled=!model.busy){Text("로그아웃")} }
            item { TextButton(onClick={confirmation="withdraw"},enabled=!model.busy){Text("회원탈퇴",color=MaterialTheme.colorScheme.error)} }
            item { Text("AI KNOW · Android ${if(Platform.demo) "데모" else "1.0"}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.outline) }
        }
    }
    confirmation?.let { type -> Confirm(if(type=="withdraw") "계정을 삭제할까요?" else "로그아웃할까요?",if(type=="withdraw") "계정 삭제는 되돌릴 수 없습니다." else "이 기기의 알림 등록도 해제됩니다.",{confirmation=null}){model.signOut(activity,type=="withdraw")} }
    val sheetState=rememberModalBottomSheetState(confirmValueChange={it!=SheetValue.Hidden||!model.busy})
    if(sheet!=null) ModalBottomSheet(onDismissRequest={if(!model.busy)sheet=null},sheetState=sheetState) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            when(sheet) {
                "nickname" -> {
                    var name by remember { mutableStateOf(model.user?.text("nickname").orEmpty()) }
                    SectionTitle("닉네임 변경")
                    OutlinedTextField(name,{name=it.take(20)},Modifier.fillMaxWidth(),label={Text("닉네임 (20자 이내)")},singleLine=true,enabled=!model.busy)
                    Button(onClick={model.action{model.api.request("api/v1/users/nickname","PATCH",payload("nickname" to name.trim()));model.refreshUser();sheet=null}},enabled=name.isNotBlank()&&!model.busy,modifier=Modifier.fillMaxWidth()){Text("저장")}
                }
                "avatar" -> { SectionTitle("나를 닮은 프로필"); LoadPane("avatars",{model.api.request("api/v1/profile")}) { data,_ ->
                    data.rows().forEach { p -> Row(Modifier.fillMaxWidth().clickable(enabled=!model.busy){model.action{model.api.request("api/v1/users/profile/${p.number("id")}","PATCH");model.refreshUser();sheet=null}}.padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                        RemoteImage(p.text("imgUrl"),null,Modifier.size(56.dp),true);Text(p.text("name"),Modifier.padding(16.dp))
                    } }
                } }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
@Composable fun NotificationScreen(model:AppModel,back:()->Unit) {
    val context=LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)model.syncPush()}
    Page("알림 설정",back) { padding-> Box(Modifier.padding(padding)) {
        LoadPane("notifications",{model.api.request("api/v1/notification-setting")}) { saved,reload ->
            var allowed by remember(saved){mutableStateOf(saved.flag("isAllowed"))}
            var time by remember(saved){mutableStateOf(saved.text("settingTime","09:00:00"))}
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Text("매일 한 번, AI를 만나는 시간",style=MaterialTheme.typography.headlineSmall)
                ListItem(headlineContent={Text("카드뉴스 알림")},trailingContent={Switch(allowed,{allowed=it},enabled=!model.busy)})
                ReminderSchedule(time,{time=it},enabled=!model.busy)
                Button(onClick={model.action{model.api.request("api/v1/notification-setting","PUT",payload("settingTime" to time,"isAllowed" to allowed));reload();if(allowed){if(Build.VERSION.SDK_INT>=33&&!model.app.push.permitted())permission.launch(Manifest.permission.POST_NOTIFICATIONS) else model.syncPush()}}},enabled=!model.busy,modifier=Modifier.fillMaxWidth()){Text("설정 저장")}
                if(!model.app.push.configured) Text(if(Platform.demo) "데모에서는 알림을 전송하지 않습니다." else "이 빌드는 푸시 연결을 준비 중입니다. 설정은 계정에 저장됩니다.",style=MaterialTheme.typography.bodySmall)
                if(!model.app.push.permitted()) TextButton(onClick={context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}){Text("기기 알림 권한 설정")}
                model.pushError?.let{Text(it,color=MaterialTheme.colorScheme.error);TextButton(onClick={model.syncPush()}){Text("기기 등록 다시 시도")}}
            }
        }
    } }
}
@Composable fun NoticeScreen(model:AppModel,back:()->Unit) {
    var page by remember{mutableIntStateOf(0)}
    var selected by remember{mutableStateOf<JsonElement?>(null)}
    Page("공지사항",back) {padding->Box(Modifier.padding(padding)){
        LoadPane(page,{model.api.request("api/v1/notices",query=mapOf("page" to "$page"))}){data,reload->
            LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                item{Refresh(reload)}
                items(data.rows()){notice->ListItem(headlineContent={Text(notice.text("title"))},supportingContent={Text(notice.text("publishedAt").take(10))},modifier=Modifier.clickable{model.action{selected=model.api.request("api/v1/notices/${notice.number("id")}")}})}
                if(data.rows().isEmpty())item{EmptyState("등록된 공지가 없습니다")}
                item{Row{TextButton(onClick={page--},enabled=page>0){Text("이전")};Spacer(Modifier.weight(1f));TextButton(onClick={page++},enabled=!data.flag("last")){Text("다음")}}}
            }
        }
    }}
    selected?.let{notice->ModalBottomSheet(onDismissRequest={selected=null}){Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){Text(notice.text("title"),style=MaterialTheme.typography.headlineSmall);Text(notice.text("content"));Spacer(Modifier.height(32.dp))}}}
}
