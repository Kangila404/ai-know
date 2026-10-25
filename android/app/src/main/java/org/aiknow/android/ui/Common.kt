@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.aiknow.android.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import coil3.SingletonImageLoader
import coil3.network.HttpException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import org.aiknow.android.BuildConfig
import org.aiknow.android.data.*

@Composable fun AiKnowTheme(appearance: String, content: @Composable () -> Unit) {
    val dark = appearance == "dark" || (appearance == "system" && isSystemInDarkTheme())
    MaterialTheme(colorScheme = if(dark) darkColorScheme(primary=Color(0xFFC7BEFF),secondary=Color(0xFFBBC9E8))
        else lightColorScheme(primary=Color(0xFF5145C6),secondary=Color(0xFF51617C),surface=Color(0xFFFCF9FF)), content=content)
}
@Composable fun Page(title: String, back: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(topBar = { TopAppBar(title={Text(title)},navigationIcon={ back?.let { IconButton(onClick=it) { Icon(Icons.AutoMirrored.Filled.ArrowBack,"뒤로") } } },actions=actions) },content=content)
}
@Composable fun Loading() { Box(Modifier.fillMaxSize().padding(32.dp),contentAlignment=Alignment.Center) { CircularProgressIndicator() } }
@Composable fun EmptyState(title: String, detail: String = "", action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.AutoAwesome,null,Modifier.size(40.dp),tint=MaterialTheme.colorScheme.primary)
        Text(title,style=MaterialTheme.typography.titleMedium)
        if(detail.isNotEmpty()) Text(detail,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(action != null) Button(onClick=onAction) { Text(action) }
    }
}
@Composable fun ErrorState(message: String, retry: () -> Unit) = EmptyState("불러오지 못했어요",message,"다시 시도",retry)
@Composable fun RemoteImage(raw: String?, description: String?, modifier: Modifier = Modifier, thumbnail: Boolean = false, fullResolution: Boolean = false) {
    val context=LocalContext.current
    val preview=remember(raw,thumbnail,fullResolution) { mediaUrl(raw,BuildConfig.API_BASE_URL,thumbnail,fullResolution) }
    val source=remember(raw) { mediaUrl(raw,BuildConfig.API_BASE_URL,original=true) }
    var original by remember(raw,thumbnail,fullResolution) { mutableStateOf(false) }
    var retry by remember(raw) { mutableIntStateOf(0) }
    var failure by remember(raw) { mutableStateOf<Throwable?>(null) }
    val url=if(original) source else preview
    key(retry,url) { SubcomposeAsyncImage(model=ImageRequest.Builder(context).data(url).apply {
        if(retry>0) { diskCachePolicy(CachePolicy.WRITE_ONLY); memoryCachePolicy(CachePolicy.WRITE_ONLY); networkCachePolicy(CachePolicy.WRITE_ONLY) }
        if(fullResolution) size(4096)
    }.build(),contentDescription=description,
        modifier=modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer),contentScale=ContentScale.Fit,
        loading={ Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp) } },
        onError={ state ->
            failure=state.result.throwable
            val code=(failure as? HttpException)?.response?.code
            if(!original && preview!=source && source?.startsWith("https://")==true &&
                (code in listOf(400,404,410,422) || failure is IllegalStateException || failure is IllegalArgumentException)) original=true
        },
        error={ Column(Modifier.fillMaxSize().padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Icon(Icons.Default.Image,"이미지를 불러오지 못했습니다",tint=MaterialTheme.colorScheme.outline)
            if(!thumbnail) {
                val code=(failure as? HttpException)?.response?.code
                Text(when(code){403->"이미지 제공처에서 접근을 허용하지 않습니다.";404,410->"이미지 파일이 없거나 주소가 만료됐습니다.";429->"이미지 요청이 많습니다. 잠시 후 다시 시도해 주세요.";else->"이미지 연결 또는 파일 형식을 확인해 주세요."},style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={url?.let{SingletonImageLoader.get(context).diskCache?.remove(it)};retry++;original=false}){Text("이미지 다시 불러오기")}
            }
        } }) }
}
@Composable fun SourceLink(label: String, url: String) {
    val uri=LocalUriHandler.current
    if(url.startsWith("https://")) TextButton(onClick={ runCatching { uri.openUri(url) } }) { Icon(Icons.Default.OpenInNew,null,Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text(label) }
}
@Composable fun SectionTitle(text: String) { Text(text,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold) }
@Composable fun Refresh(onClick: () -> Unit) { IconButton(onClick=onClick) { Icon(Icons.Default.Refresh,"새로고침") } }
@Composable fun LoadPane(key: Any, loader: suspend () -> JsonElement, content: @Composable (JsonElement, () -> Unit) -> Unit) {
    var value by remember(key) { mutableStateOf<JsonElement?>(null) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var refresh by remember(key) { mutableIntStateOf(0) }
    val currentLoader by rememberUpdatedState(loader)
    LaunchedEffect(key,refresh) {
        error=null
        try { value=currentLoader() }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { error=e.message ?: "네트워크 연결을 확인해 주세요." }
    }
    if(error != null) ErrorState(error!!) { refresh++ }
    else if(value == null) Loading()
    else content(value!!) { refresh++ }
}
fun statusLabel(value: String) = mapOf("PENDING" to "검수 대기","APPROVED" to "승인","DENIED" to "반려","READY" to "발송 대기","PUBLISHED" to "게시 중","HIDDEN" to "숨김","NEWS" to "뉴스","AI_THEORY" to "AI 이론","ADMIN" to "관리자","USER" to "일반 사용자")[value] ?: value
@Composable fun StatusChip(value: String) { SuggestionChip(onClick={},label={Text(statusLabel(value))}) }
@Composable fun Confirm(title: String, detail: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Text(detail)},confirmButton={TextButton(onClick={onDismiss();onConfirm()}){Text("확인")}},dismissButton={TextButton(onClick=onDismiss){Text("취소")}})
}
