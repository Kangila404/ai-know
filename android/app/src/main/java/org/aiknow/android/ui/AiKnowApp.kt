@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.aiknow.android.ui
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import org.aiknow.android.*
import org.aiknow.android.R
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
        Image(painterResource(R.drawable.brand_logo),"AI Know",Modifier.size(82.dp))
        Spacer(Modifier.height(24.dp))
        Text("AI KNOW",style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Bold)
        Text("AI를 더 쉽게,\n매일 한 장씩",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.padding(vertical=20.dp))
        Text("복잡한 AI 이야기를 핵심만 담아 전해드려요.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(48.dp))
        if(Platform.demo) {
            Text("디자인 확인용 데모 · 운영 계정 및 서버와 분리",style=MaterialTheme.typography.labelMedium)
            Button(onClick={model.signIn(activity,"DEMO")},enabled=!model.busy,modifier=Modifier.fillMaxWidth()) { Text("데모 둘러보기") }
        } else {
            model.providers.forEach { provider ->
                val configured=if(provider=="GOOGLE") BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank() else BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()
                OutlinedButton(onClick={model.signIn(activity,provider)},enabled=configured&&!model.busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                    if(provider=="GOOGLE"){Image(painterResource(R.drawable.google_symbol),null,Modifier.size(20.dp));Spacer(Modifier.width(12.dp))}
                    Text(if(provider=="GOOGLE") "Google로 계속하기" else "카카오로 계속하기")
                }
                if(!configured) Text("${if(provider=="GOOGLE") "Google" else "카카오"} 로그인 연결 준비 중",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(bottom=12.dp))
            }
            if(model.providers.isEmpty()) EmptyState("로그인을 준비 중이에요","연결 상태를 확인하고 다시 시도해 주세요.")
            TextButton(onClick={model.restore()}) { Text("연결 다시 확인") }
        }
        if(model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("계속하면 서비스 이용약관 및 개인정보 처리방침에 동의하게 됩니다.",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=18.dp))
    }
}
@Composable private fun Onboarding(model: AppModel) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        TextButton(onClick={model.onboarding()},enabled=!model.busy,modifier=Modifier.align(Alignment.End)){Text("건너뛰기")}
        Text("하루 한 장의\nAI 인사이트",style=MaterialTheme.typography.displaySmall)
        listOf("오늘" to "복잡한 AI 소식을 카드 한 장으로 쉽고 빠르게 만나보세요.","마음에 드는 이야기는 좋아요로" to "좋아요한 카드와 관심 있는 카테고리를 아카이브에서 다시 볼 수 있어요.","매일 새로운 AI 이야기" to "알림 설정으로 오늘의 카드가 도착했을 때 놓치지 않고 확인해 보세요.").forEach { (title,body) ->
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
            composable("admin/{kind}/{id}") { e -> if(model.admin) AdminDetail(model,e.arguments!!.getString("kind")!!,e.arguments!!.getString("id")!!.toLong(),back={nav.popBackStack()},openArticle={nav.navigate("admin/articles/$it")}) }
        }
    }
}
