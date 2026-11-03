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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.*
import org.aiknow.android.*
import org.aiknow.android.R
import org.aiknow.android.data.*
import org.aiknow.android.notifications.PushDestination

@Composable fun AiKnowApp(model: AppModel) {
    val context=LocalContext.current
    val introPrefs=remember{context.getSharedPreferences("intro",0)}
    var intro by rememberSaveable { mutableStateOf(!introPrefs.getBoolean("seen",false)) }
    AiKnowTheme {
        Surface(Modifier.fillMaxSize()) {
            when {
                model.starting -> Loading()
                model.user == null && intro && !model.browsingAsGuest -> ConversationIntro { introPrefs.edit().putBoolean("seen",true).apply();intro=false }
                model.user == null && !model.browsingAsGuest -> LoginScreen(model){introPrefs.edit().putInt("step",0).apply();intro=true}
                model.user != null && !model.user!!.flag("onboardingCompleted") -> AccountOnboarding(model)
                else -> key(model.sessionGeneration,model.user?.text("userId") ?: "guest") { SignedInApp(model) }
            }
            model.loginReason?.let { reason ->
                AlertDialog(onDismissRequest={model.loginReason=null},title={Text("로그인이 필요한 기능이에요")},text={Text(reason)},confirmButton={TextButton(onClick={model.loginReason=null;model.showLogin=true}){Text("로그인")}},dismissButton={TextButton(onClick={model.loginReason=null}){Text("계속 둘러보기")}})
            }
            if(model.showLogin && model.user==null) Dialog(onDismissRequest={if(!model.busy)model.showLogin=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
                Surface(Modifier.fillMaxSize()) { Column {
                    TextButton(onClick={model.showLogin=false;model.error=null},enabled=!model.busy,modifier=Modifier.statusBarsPadding()){Text("닫기")}
                    Box(Modifier.weight(1f)) { LoginScreen(model,allowGuest=false){} }
                } }
            }
            model.error?.let { message -> AlertDialog(onDismissRequest={model.error=null},title={Text("안내")},text={Text(message)},confirmButton={TextButton(onClick={model.error=null}){Text("확인")}}) }
        }
    }
}
@Composable private fun LoginScreen(model: AppModel, allowGuest:Boolean=true, replayIntro:()->Unit) {
    val activity=requireNotNull(LocalActivity.current)
    Box(Modifier.fillMaxSize().safeDrawingPadding(),contentAlignment=Alignment.Center) {
    Column(Modifier.widthIn(max=408.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.brand_logo),"AI Know",Modifier.size(82.dp))
        Spacer(Modifier.height(24.dp))
        Text("AI KNOW",style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Bold)
        Text("AI를 더 쉽게,\n매일 한 장씩",style=MaterialTheme.typography.headlineMedium,textAlign=TextAlign.Center,modifier=Modifier.padding(vertical=20.dp))
        Text("복잡한 AI 이야기를 핵심만 담아 전해드려요.",textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(48.dp))
        if(Platform.demo) {
            Text("디자인 확인용 데모 · 운영 계정 및 서버와 분리",style=MaterialTheme.typography.labelMedium)
            Button(onClick={model.signIn(activity,"DEMO")},enabled=!model.busy,modifier=Modifier.fillMaxWidth()) { Text("데모 둘러보기") }
        } else {
            model.providers.forEach { provider ->
                val configured=if(provider=="GOOGLE") BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank() else BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()
                SocialLoginButton(provider,configured&&!model.busy) { model.signIn(activity,provider) }
                Spacer(Modifier.height(12.dp))
                if(!configured) Text("${if(provider=="GOOGLE") "Google" else "카카오"} 로그인 연결 준비 중",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(bottom=12.dp))
            }
            if(model.providers.isEmpty()) EmptyState("로그인을 준비 중이에요","연결 상태를 확인하고 다시 시도해 주세요.")
            TextButton(onClick={model.restore()}) { Text("연결 다시 확인") }
        }
        if(model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(allowGuest) {
            OutlinedButton(onClick={model.browseAsGuest()},enabled=!model.busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("로그인 없이 둘러보기")}
            TextButton(onClick=replayIntro,enabled=!model.busy){Text("소개 다시 보기")}
        }
        Text("계속하면 서비스 이용약관 및 개인정보 처리방침에 동의하게 됩니다.",textAlign=TextAlign.Center,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=18.dp))
    }
    }
}
@Composable internal fun SocialLoginButton(provider:String,enabled:Boolean,onClick:()->Unit) {
    val kakao=provider=="KAKAO"
    OutlinedButton(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=MaterialTheme.shapes.medium,
        contentPadding=PaddingValues(horizontal=18.dp,vertical=14.dp),
        colors=ButtonDefaults.outlinedButtonColors(containerColor=if(kakao)Color(0xFFFEE500) else MaterialTheme.colorScheme.surface,contentColor=if(kakao)Color(0xFF191919) else MaterialTheme.colorScheme.onSurface)) {
        Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
            Image(painterResource(if(kakao)R.drawable.kakao_symbol else R.drawable.google_symbol),null,Modifier.align(Alignment.CenterStart).size(20.dp))
            Text(if(kakao)"카카오로 계속하기" else "Google로 계속하기",Modifier.padding(horizontal=30.dp),textAlign=TextAlign.Center,style=MaterialTheme.typography.titleMedium)
        }
    }
}
@Composable private fun SignedInApp(model: AppModel) {
    val nav=rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route=entry?.destination?.route ?: "today"
    val roots=listOf("today","archive","me","admin")
    LaunchedEffect(model.admin,route) { if(!model.admin && route.startsWith("admin")) nav.navigate(if(model.user==null) "today" else "me") { popUpTo("today");launchSingleTop=true } }
    LaunchedEffect(model.pendingPush) {
        val pending=model.pendingPush ?: return@LaunchedEffect
        val session=model.sessionGeneration
        try {
            when(val destination=pending.destination) {
                is PushDestination.Card -> nav.navigate("card/${destination.id}") { launchSingleTop=true }
                is PushDestination.AdminAlert -> if(model.authorizeAdminPush() && model.sessionGeneration==session && model.pendingPush==pending) {
                    nav.navigate("admin/alerts") { launchSingleTop=true }
                }
            }
        } finally { model.consumePush(pending) }
    }
    Scaffold(topBar={if(model.user==null && route in roots) Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
        Text("둘러보는 중",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
        TextButton(onClick={nav.navigate("notices")}){Text("공지사항")}
        TextButton(onClick={model.showLogin=true}){Text("로그인")}
    }},bottomBar={
        if(route in roots) NavigationBar {
            val tabs=listOf(Triple("today","오늘",Icons.Default.AutoAwesome),Triple("archive","아카이브",Icons.Default.CollectionsBookmark),Triple("me","마이",Icons.Default.Person)) + if(model.admin) listOf(Triple("admin","관리자",Icons.Default.AdminPanelSettings)) else emptyList()
            tabs.forEach { (path,label,icon) -> NavigationBarItem(selected=route==path,onClick={if(path!="me" || model.requireLogin("내 프로필과 알림 설정은 로그인 후 이용할 수 있어요."))nav.navigate(path){popUpTo("today"){saveState=true};launchSingleTop=true;restoreState=true}},icon={Icon(icon,null)},label={Text(label)}) }
        }
    }) { padding ->
        NavHost(nav,"today",Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable("today") { TodayScreen(model,{nav.navigate("card/$it")},{nav.navigate("archive")},{if(model.requireLogin("알림 시간과 수신 설정은 로그인 후 이용할 수 있어요."))nav.navigate("notifications")}) }
            composable("archive") { ArchiveScreen(model){nav.navigate("card/$it")} }
            composable("card/{id}") { e -> CardScreen(model,e.arguments!!.getString("id")!!.toLong()){nav.popBackStack()} }
            composable("me") { if(model.user!=null)ProfileScreen(model){nav.navigate(it)} }
            composable("notifications") { if(model.user!=null)NotificationScreen(model){nav.popBackStack()} }
            composable("notices") { NoticeScreen(model){nav.popBackStack()} }
            composable("admin") { if(model.admin) AdminHome {nav.navigate(it)} }
            composable("admin/{kind}") { e -> if(model.admin) {
                val kind=e.arguments!!.getString("kind")!!
                if(kind=="alerts") AdminAlertsScreen(model,{nav.popBackStack()}) { target,id->nav.navigate("admin/$target/$id") }
                else AdminList(model,kind,{nav.popBackStack()}) {target,id->nav.navigate("admin/$target/$id")}
            } }
            composable("admin/{kind}/{id}") { e -> if(model.admin) AdminDetail(model,e.arguments!!.getString("kind")!!,e.arguments!!.getString("id")!!.toLong(),back={nav.popBackStack()},openArticle={nav.navigate("admin/articles/$it")}) }
        }
    }
}
