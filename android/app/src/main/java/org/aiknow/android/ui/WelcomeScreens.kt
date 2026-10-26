@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.aiknow.android.ui

import android.Manifest
import android.animation.ValueAnimator
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.aiknow.android.AppModel
import org.aiknow.android.Platform
import org.aiknow.android.R
import org.aiknow.android.data.*

private data class IntroLine(val title:String,val detail:String,val response:String,val icon:ImageVector)
private val introLines=listOf(
    IntroLine("AI 이야기,\n어디서부터 읽고 있나요?","새로운 기술은 쏟아지는데,\n무엇이 중요한지 알기 어렵죠.","맞아요, 너무 많아요",Icons.Default.TravelExplore),
    IntroLine("복잡한 소식은 덜고,\n핵심부터 알아봐요.","오늘의 AI 소식과 배경지식을\n짧은 카드뉴스로 정리했어요.","어떻게 읽나요?",Icons.Default.AutoAwesome),
    IntroLine("가볍게 밀면서,\n한 장씩 이해해요.","이미지는 크게 보고, 출처는 직접 확인해요.\n기억하고 싶은 이야기는 좋아요로 남겨요.","좋아요, 그다음은요?",Icons.Default.SwipeLeft),
    IntroLine("내 하루에 맞는\nAI 습관을 만들어요.","원하는 시간에 알림을 받고,\n지난 이야기는 아카이브에서 다시 만나요.","함께 시작해 볼게요",Icons.Default.NotificationsActive),
    IntroLine("AI를 더 쉽게,\n매일 한 장씩.","아는 만큼 가까워지는 AI.\nAI KNOW와 함께 시작해요.","AI KNOW 시작하기",Icons.Default.School)
)

/** Adapted from the user's Ethica ConversationIntro: response → receding passage history. */
@Composable fun ConversationIntro(onFinish:()->Unit) {
    val prefs=LocalContext.current.getSharedPreferences("intro",0)
    var step by rememberSaveable{mutableIntStateOf(prefs.getInt("step",0).coerceIn(introLines.indices))}
    var moving by remember{mutableStateOf(false)}
    val motion=ValueAnimator.areAnimatorsEnabled()
    LaunchedEffect(step){if(moving&&motion)delay(700);moving=false}
    fun goBack(){if(!moving&&step>0){moving=true;step--;prefs.edit().putInt("step",step).apply()}}
    BackHandler(step>0){goBack()}
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=28.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Image(painterResource(R.drawable.brand_logo),"AI KNOW",Modifier.size(36.dp))
            Spacer(Modifier.width(10.dp));Text("AI KNOW",style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)
            Spacer(Modifier.weight(1f));TextButton(onClick=onFinish){Text("건너뛰기")}
        }
        JourneyProgress(step,introLines.size,"소개")
        val density=LocalDensity.current
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val compact=density.fontScale>=1.3f||maxHeight<420.dp
            val top=if(compact)12.dp else maxHeight*.28f
            introLines.take(step+1).forEachIndexed { index,line ->
                val depth=step-index
                if(depth<=2&&(!compact||depth==0)) key(index) {
                    val scale by animateFloatAsState(if(depth==0)1f else .65f,tween(if(motion)650 else 0),label="소개 크기")
                    val y by animateDpAsState(top-(depth*120).dp,tween(if(motion)650 else 0),label="소개 이동")
                    val alpha by animateFloatAsState(when(depth){0->1f;1->.20f;else->.07f},tween(if(motion)500 else 0),label="소개 흐림")
                    Column(Modifier.fillMaxWidth().graphicsLayer{scaleX=scale;scaleY=scale;translationY=y.toPx();this.alpha=alpha;transformOrigin=TransformOrigin(.5f,0f)}
                        .blur(if(depth>0&&motion)2.dp else 0.dp,BlurredEdgeTreatment.Unbounded)
                        .then(if(depth>0)Modifier.clearAndSetSemantics{}else Modifier)
                        .heightIn(max=maxHeight-top).verticalScroll(rememberScrollState(),enabled=depth==0),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                        if(depth==0) FeatureEmblem(line.icon)
                        Text(line.title,Modifier.semantics{heading();liveRegion=LiveRegionMode.Polite},style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                        Text(line.detail,style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically) {
            if(step>0)TextButton(onClick={goBack()},enabled=!moving){Text("이전")}
            Button(onClick={if(step==introLines.lastIndex)onFinish()else{moving=true;step++;prefs.edit().putInt("step",step).apply()}},enabled=!moving,modifier=Modifier.weight(1f).heightIn(min=56.dp)) {
                Text(introLines[step].response);Spacer(Modifier.width(12.dp));Icon(Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(18.dp))
            }
        }
    }
}

@Composable internal fun JourneyProgress(step:Int,total:Int,label:String) {
    Row(Modifier.fillMaxWidth().clearAndSetSemantics{contentDescription="$label ${step+1} / $total"},horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        repeat(total){index->Box(Modifier.weight(1f).height(4.dp).background(if(index<=step)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,CircleShape))}
    }
}
@Composable internal fun FeatureEmblem(icon:ImageVector) {
    Box(Modifier.size(88.dp).background(MaterialTheme.colorScheme.primaryContainer,CircleShape),contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size(40.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)}
}

@Composable fun AccountOnboarding(model:AppModel) {
    var step by rememberSaveable(model.user?.text("userId")){mutableIntStateOf(0)}
    var nickname by rememberSaveable{mutableStateOf(model.user?.text("nickname").orEmpty().let{if(it=="새 사용자")""else it})}
    val focus=LocalFocusManager.current
    BackHandler(step>0){if(!model.busy)step--}
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){Text("나에게 맞게 시작하기",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);Text("${step+1} / 2",color=MaterialTheme.colorScheme.primary)}
        JourneyProgress(step,2,"계정 설정")
        if(step==0) {
            FeatureEmblem(Icons.Default.WavingHand)
            Text("어떻게 불러드릴까요?",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
            Text("AI KNOW에서 사용할 이름이에요.\n마이 페이지에서 언제든 바꿀 수 있어요.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            val saveName={focus.clearFocus();model.action{model.api.request("api/v1/users/nickname","PATCH",payload("nickname" to nickname.trim()));model.refreshUser();step=1}}
            OutlinedTextField(nickname,{nickname=it.take(20)},Modifier.fillMaxWidth().semantics{contentDescription="온보딩 닉네임"},label={Text("닉네임")},supportingText={Text("${nickname.trim().length} / 20")},singleLine=true,enabled=!model.busy,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Next),keyboardActions=KeyboardActions(onNext={if(nickname.isNotBlank()&&!model.busy)saveName()}))
            Button(onClick=saveName,enabled=nickname.isNotBlank()&&!model.busy,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text("다음");Spacer(Modifier.width(8.dp));Icon(Icons.AutoMirrored.Filled.ArrowForward,null)}
        } else LoadPane("onboarding-notifications",{model.api.request("api/v1/notification-setting")}) { saved,_ ->
            var time by rememberSaveable{mutableStateOf(saved.text("settingTime","09:00:00"))}
            val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->model.onboarding(time,granted)}
            FeatureEmblem(Icons.Default.NotificationsActive)
            Text("언제 만나면 좋을까요?",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
            Text("하루 한 번, 내 리듬에 맞춰요.\n알림 없이도 모든 카드를 읽을 수 있어요.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            ReminderSchedule(time,{time=it},enabled=!model.busy)
            Text("알림을 켜면 기기 권한을 확인합니다. 거절해도 가입은 완료되며 나중에 설정할 수 있어요.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(!model.app.push.configured)Text(if(Platform.demo)"데모에서는 실제 알림을 보내지 않습니다."else"현재 빌드는 푸시 연결 준비 중입니다. 선택한 시간과 수신 설정은 저장됩니다.",style=MaterialTheme.typography.bodySmall)
            Button(onClick={if(!Platform.demo&&Build.VERSION.SDK_INT>=33&&!model.app.push.permitted())permission.launch(Manifest.permission.POST_NOTIFICATIONS)else model.onboarding(time,Platform.demo||model.app.push.permitted())},enabled=!model.busy,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text("알림 받고 시작하기")}
            TextButton(onClick={model.onboarding(time,false)},enabled=!model.busy,modifier=Modifier.fillMaxWidth()){Text("알림 없이 시작하기")}
            TextButton(onClick={step=0},enabled=!model.busy){Text("이름 다시 설정")}
        }
        if(model.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable internal fun ReminderSchedule(time:String,onChange:(String)->Unit,enabled:Boolean=true) {
    var choosing by remember{mutableStateOf(false)}
    Card(onClick={choosing=true},enabled=enabled,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Schedule,null);Spacer(Modifier.width(8.dp));Text("매일 만나는 시간",style=MaterialTheme.typography.labelLarge);Spacer(Modifier.weight(1f));Icon(Icons.Default.Edit,"시간 변경",Modifier.size(20.dp))}
            Text(time.take(5),style=MaterialTheme.typography.displayMedium,fontWeight=FontWeight.Bold)
            Text("한국 시간 · 눌러서 변경",style=MaterialTheme.typography.bodySmall)
        }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("08:00:00" to "출근 전 08:00","12:30:00" to "점심 12:30","21:00:00" to "저녁 21:00").forEach{(value,label)->FilterChip(time==value,{onChange(value)},label={Text(label)},enabled=enabled)}
    }
    if(choosing) {
        val parts=time.split(':');val clock=rememberTimePickerState(parts[0].toIntOrNull()?:9,parts.getOrNull(1)?.toIntOrNull()?:0,true)
        AlertDialog(onDismissRequest={choosing=false},title={Text("알림 시간")},text={TimeInput(clock)},confirmButton={TextButton(onClick={onChange("%02d:%02d:00".format(java.util.Locale.ROOT,clock.hour,clock.minute));choosing=false}){Text("선택")}},dismissButton={TextButton(onClick={choosing=false}){Text("취소")}})
    }
}
