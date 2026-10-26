package org.aiknow.android.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.JsonElement
import org.aiknow.android.data.*

@Composable internal fun TodayGreeting(name:String) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("M월 d일 EEEE",Locale.KOREAN)),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        Text(if(name.isBlank())"오늘은 어떤 AI를 만날까요?"else"${name} 님의 오늘,\n새로운 발견 하나",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
    }
}

@Composable internal fun TodayFeature(card:JsonElement,open:()->Unit) {
    ElevatedCard(onClick=open,modifier=Modifier.fillMaxWidth(),colors=CardDefaults.elevatedCardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
        Box(Modifier.fillMaxWidth()) {
            RemoteImage(card.text("titleImgUrl"),null,Modifier.fillMaxWidth().height(220.dp),contentScale=ContentScale.Crop)
            Surface(Modifier.align(Alignment.TopStart).padding(14.dp),shape=CircleShape,color=MaterialTheme.colorScheme.surface){Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){Icon(Icons.Default.AutoAwesome,null,Modifier.size(14.dp));Text(statusLabel(card.text("contentType")),style=MaterialTheme.typography.labelMedium)}}
        }
        Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(card.text("title"),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text(card.text("summary"),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Default.Style,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp));Text("카드뉴스 읽기",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
                if(card.flag("read"))Icon(Icons.Default.CheckCircle,"읽음",Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
                else Icon(Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable internal fun KeyPointPanel(points:List<String>) {
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Default.TipsAndUpdates,null,tint=MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp));Text("오늘의 핵심 포인트",Modifier.weight(1f).semantics{heading()},style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer){Text("${points.size}",Modifier.padding(horizontal=10.dp,vertical=4.dp),style=MaterialTheme.typography.labelLarge)}
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            points.forEachIndexed { index,point ->
                var expanded by rememberSaveable(point){mutableStateOf(false)}
                Surface(onClick={expanded=!expanded},color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().animateContentSize().semantics{stateDescription=if(expanded)"펼침"else"접힘"}) {
                    Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.Top) {
                        Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.secondaryContainer,CircleShape),contentAlignment=Alignment.Center){Text("%02d".format(index+1),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSecondaryContainer)}
                        Text(point,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge,maxLines=if(expanded)Int.MAX_VALUE else 2,overflow=TextOverflow.Ellipsis)
                        Icon(if(expanded)Icons.Default.ExpandLess else Icons.Default.ExpandMore,if(expanded)"핵심 포인트 접기"else"핵심 포인트 펼치기",Modifier.size(20.dp),tint=MaterialTheme.colorScheme.outline)
                    }
                }
                if(index<points.lastIndex)HorizontalDivider(Modifier.padding(start=64.dp,end=16.dp),color=MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable internal fun ArchiveInvitation(open:()->Unit) {
    FilledTonalButton(onClick=open,modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(20.dp),shape=MaterialTheme.shapes.large) {
        Icon(Icons.Default.CollectionsBookmark,null,Modifier.size(28.dp));Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)){Text("궁금한 AI, 더 알아보기",style=MaterialTheme.typography.titleSmall);Text("아카이브에서 주제별로 찾아보세요",style=MaterialTheme.typography.bodySmall)}
        Icon(Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(20.dp))
    }
}
