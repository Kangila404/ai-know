@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.aiknow.android.ui
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import org.aiknow.android.*
import org.aiknow.android.data.*

@Composable fun AiKnowApp(model: AppModel) {
    AiKnowTheme(model.appearance) {
        Surface(Modifier.fillMaxSize()) {
            when {
                model.starting -> Loading()
                model.user == null -> LoginScreen(model)
                !model.user!!.flag("onboardingCompleted") -> Onboarding(model)
                else -> SignedInApp(model)
            }
            model.error?.let { message -> AlertDialog(onDismissRequest={model.error=null},title={Text("안내")},text={Text(message)},confirmButton={TextButton(onClick={model.error=null}){Text("확인")}}) }
        }
    }
}
@Composable private fun LoginScreen(model: AppModel) {
    val activity=requireNotNull(LocalActivity.current)
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.Center) {
        Icon(Icons.Default.AutoAwesome,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text("AI KNOW",style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Bold)
        Text("매일 한 장씩,\nAI를 알아가는 시간",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.padding(vertical=20.dp))
        Text("오늘의 AI 소식과 꼭 알아야 할 개념을\n짧고 쉬운 카드뉴스로 만나보세요.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(48.dp))
        if(Platform.demo) {
            Text("디자인 확인용 데모 · 운영 계정 및 서버와 분리",style=MaterialTheme.typography.labelMedium)
            Button(onClick={model.signIn(activity,"DEMO")},enabled=!model.busy,modifier=Modifier.fillMaxWidth()) { Text("데모 둘러보기") }
        } else {
            model.providers.forEach { provider ->
                val configured=if(provider=="GOOGLE") BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank() else BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()
                OutlinedButton(onClick={model.signIn(activity,provider)},enabled=configured&&!model.busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                    Text(if(provider=="GOOGLE") "Google로 계속하기" else "카카오로 계속하기")
                }
                if(!configured) Text("${if(provider=="GOOGLE") "Google" else "카카오"} 로그인 연결 준비 중",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(bottom=12.dp))
            }
            if(model.providers.isEmpty()) EmptyState("로그인을 준비 중이에요","연결 상태를 확인하고 다시 시도해 주세요.")
            TextButton(onClick={model.restore()}) { Text("연결 다시 확인") }
        }
        if(model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
@Composable private fun Onboarding(model: AppModel) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Text("AI가 낯설어도\n괜찮아요",style=MaterialTheme.typography.displaySmall)
        listOf("오늘" to "검수된 AI 소식을 카드로 읽어요.","아카이브" to "뉴스와 AI 이론을 찾아보고 좋아요로 모아둬요.","나에게 맞는 알림" to "원하는 시간에 새로운 이야기를 받아요.").forEach { (title,body) ->
            ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp)) { SectionTitle(title); Text(body,Modifier.padding(top=12.dp)) } }
        }
        Button(onClick={model.onboarding()},enabled=!model.busy,modifier=Modifier.fillMaxWidth()) { Text("시작하기") }
    }
}
@Composable private fun SignedInApp(model: AppModel) {
    val nav=rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route=entry?.destination?.route ?: "today"
    val roots=listOf("today","archive","me","admin")
    LaunchedEffect(model.admin) { if(!model.admin && route.startsWith("admin")) nav.navigate("me") { popUpTo("today");launchSingleTop=true } }
    LaunchedEffect(model.pendingCard) { model.pendingCard?.let { nav.navigate("card/$it"); model.pendingCard=null } }
    Scaffold(bottomBar={
        if(route in roots) NavigationBar {
            val tabs=listOf(Triple("today","오늘",Icons.Default.AutoAwesome),Triple("archive","아카이브",Icons.Default.CollectionsBookmark),Triple("me","마이",Icons.Default.Person)) + if(model.admin) listOf(Triple("admin","관리자",Icons.Default.AdminPanelSettings)) else emptyList()
            tabs.forEach { (path,label,icon) -> NavigationBarItem(selected=route==path,onClick={nav.navigate(path){popUpTo("today"){saveState=true};launchSingleTop=true;restoreState=true}},icon={Icon(icon,null)},label={Text(label)}) }
        }
    }) { padding ->
        NavHost(nav,"today",Modifier.padding(bottom=padding.calculateBottomPadding())) {
            composable("today") { TodayScreen(model,{nav.navigate("card/$it")},{nav.navigate("archive")},{nav.navigate("notifications")}) }
            composable("archive") { ArchiveScreen(model){nav.navigate("card/$it")} }
            composable("card/{id}") { e -> CardScreen(model,e.arguments!!.getString("id")!!.toLong()){nav.popBackStack()} }
            composable("me") { ProfileScreen(model){nav.navigate(it)} }
            composable("notifications") { NotificationScreen(model){nav.popBackStack()} }
            composable("notices") { NoticeScreen(model){nav.popBackStack()} }
            composable("admin") { if(model.admin) AdminHome {nav.navigate(it)} }
            composable("admin/{kind}") { e -> if(model.admin) AdminList(model,e.arguments!!.getString("kind")!!,{nav.popBackStack()}) {kind,id->nav.navigate("admin/$kind/$id")} }
            composable("admin/{kind}/{id}") { e -> if(model.admin) AdminDetail(model,e.arguments!!.getString("kind")!!,e.arguments!!.getString("id")!!.toLong()){nav.popBackStack()} }
        }
    }
}
