@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.aiknow.android.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.aiknow.android.AppModel
import org.aiknow.android.data.*

@Composable fun TodayScreen(model: AppModel, open: (Long)->Unit, archive: ()->Unit, settings: ()->Unit) {
    var reload by remember { mutableIntStateOf(0) }
    Page("오늘의 AI",actions={Refresh{reload++};IconButton(onClick=settings){Icon(Icons.Default.NotificationsNone,"알림 설정")}}) { padding ->
        Box(Modifier.padding(padding)) {
            LoadPane(reload,{
                try { model.api.request("api/v1/cardNews/today") } catch(e:ApiException) { if(e.status==404) JsonNull else throw e }
            }) { data,_ ->
                if(data==JsonNull) EmptyState("오늘의 소식을 준비 중이에요","먼저 아카이브에서 AI 이론을 만나보세요.","아카이브 보기",archive)
                else LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    item { Text("AI를 알아가는 작은 습관",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge) }
                    item { NewsTile(data,false){open(data.number("id"))} }
                    item { SectionTitle("오늘의 핵심 포인트") }
                    items(data.array("keyPoints")) { point -> Card(Modifier.fillMaxWidth()) { Text((point as JsonPrimitive).content,Modifier.padding(20.dp),style=MaterialTheme.typography.bodyLarge) } }
                }
            }
        }
    }
}
@Composable fun NewsTile(card: JsonElement, compact: Boolean, open: ()->Unit) {
    ElevatedCard(onClick=open,modifier=Modifier.fillMaxWidth()) {
        if(compact) Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
            RemoteImage(card.text("titleImgUrl"),null,Modifier.size(88.dp),true)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(card.text("title"),style=MaterialTheme.typography.titleMedium)
                Text("${statusLabel(card.text("contentType"))} · ${card.text("publicationDate")}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(card.flag("read")) Text("읽음",style=MaterialTheme.typography.labelSmall)
            }
            if(card.flag("liked")) Icon(Icons.Default.Favorite,"좋아요",tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(18.dp))
        } else Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            RemoteImage(card.text("titleImgUrl"),null,Modifier.fillMaxWidth().height(240.dp))
            Text(card.text("title"),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text(card.text("summary"),style=MaterialTheme.typography.bodyLarge)
            Text(card.array("categories").joinToString(" · "){it.text("name")}.ifBlank{statusLabel(card.text("contentType"))}+" · "+card.text("publicationDate"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("카드뉴스 읽기 →",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
        }
    }
}
@Composable fun ArchiveScreen(model: AppModel, open: (Long)->Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var likes by rememberSaveable { mutableStateOf(false) }
    var categories by remember { mutableStateOf<List<JsonElement>>(emptyList()) }
    var cards by remember { mutableStateOf<List<JsonElement>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(0) }
    var more by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { try { categories=model.api.request("api/v1/cardNews/categories").rows() } catch(_:Exception) {} }
    LaunchedEffect(query,category,likes,reload,model.contentRevision) { page=0; cards=emptyList();more=true }
    LaunchedEffect(query,category,likes,reload,page,model.contentRevision) {
        loading=true; error=null
        try {
            delay(250)
            val params=mutableMapOf("page" to "$page","size" to "20","query" to query.trim(),"isOnlyLiked" to "$likes")
            category?.let{params["categoryId"]=it}
            val result=model.api.request("api/v1/cardNews",query=params).rows()
            cards=if(page==0) result else (cards+result).distinctBy{it.number("id")};more=result.size==20
        } catch(e:CancellationException) {throw e} catch(e:Exception){error=e.message} finally {loading=false}
    }
    Page("아카이브",actions={Refresh{reload++}}) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(query,{query=it.take(200)},Modifier.fillMaxWidth().padding(horizontal=20.dp),placeholder={Text("궁금한 AI 이야기를 검색하세요")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,shape=MaterialTheme.shapes.extraLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(likes,{likes=!likes},label={Text("좋아요")},leadingIcon={Icon(Icons.Default.FavoriteBorder,null,Modifier.size(18.dp))})
                FilterChip(category==null,{category=null},label={Text("전체")})
                categories.forEach{ c-> FilterChip(category==c.text("id"),{category=c.text("id")},label={Text(c.text("name"))}) }
            }
            LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                items(cards,key={it.number("id")}) { card -> NewsTile(card,true){open(card.number("id"))} }
                if(loading) item { Box(Modifier.fillMaxWidth().padding(20.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()} }
                else if(error!=null) item { ErrorState(error!!){reload++} }
                else if(cards.isEmpty()) item { EmptyState("아직 카드뉴스가 없어요","다른 검색어나 카테고리를 선택해 보세요.") }
                else if(more) item { TextButton(onClick={page++},modifier=Modifier.fillMaxWidth()){Text("더 보기")} }
            }
        }
    }
}
@Composable fun CardScreen(model: AppModel,id:Long,back:()->Unit) {
    var showSources by remember(id) { mutableStateOf(false) }
    var expandedImage by remember(id) { mutableStateOf<String?>(null) }
    Page("카드뉴스",back,actions={IconButton(onClick={showSources=true}){Icon(Icons.Default.Info,"출처 및 이미지 정보")}}) { padding -> Box(Modifier.padding(padding)) {
        LoadPane(id,{
            coroutineScope {
                val card=async{model.api.request("api/v1/cardNews/$id")}
                val slides=async{model.api.request("api/v1/cardNews/cardSlides",query=mapOf("cardNewsId" to "$id"))}
                payload("card" to card.await(),"slides" to slides.await())
            }
        }) { data,_ ->
            val card=data.obj().getValue("card")
            val slides=data.array("slides").sortedBy{it.number("sequence")}
            val pager=rememberPagerState(pageCount={slides.size})
            val scope=rememberCoroutineScope()
            var liked by remember(id){mutableStateOf(card.flag("liked"))}
            var read by remember(id){mutableStateOf(card.flag("read"))}
            var readError by remember(id){mutableStateOf<String?>(null)}
            LaunchedEffect(pager.settledPage,slides.size,read) {
                if(slides.isNotEmpty() && pager.settledPage==slides.lastIndex && !read) {
                    delay(2000)
                    try { model.api.request("api/v1/cardNews/$id/read","PUT");read=true;model.contentChanged() }
                    catch(e:CancellationException){throw e}
                    catch(_:Exception){readError="읽음 기록을 저장하지 못했습니다. 아래 버튼으로 다시 시도해 주세요."}
                }
            }
            if(showSources) ModalBottomSheet(onDismissRequest={showSources=false}) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    SectionTitle("출처 및 이미지 정보")
                    SourceLink(card.text("sourceTitle","원문 보기"),card.text("sourceUrl"))
                    SectionTitle("표지 이미지")
                    Text(card.text("titleImageCredit","출처 표기 미제공"),style=MaterialTheme.typography.bodySmall)
                    SourceLink("표지 이미지 출처",card.text("titleImageSourceUrl"))
                    slides.forEachIndexed { i,s -> if(s.text("imageCredit").isNotEmpty()||s.text("imageSourceUrl").isNotEmpty()) {
                        SectionTitle("${i+1}번째 이미지")
                        Text(s.text("imageCredit"),style=MaterialTheme.typography.bodySmall)
                        SourceLink("이미지 출처",s.text("imageSourceUrl"))
                    } }
                    TextButton(onClick={showSources=false}){Text("닫기")}
                }
            }
            if(slides.isEmpty()) EmptyState("아직 슬라이드가 없어요")
            else Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("${pager.currentPage+1} / ${slides.size}",style=MaterialTheme.typography.labelLarge,modifier=Modifier.weight(1f))
                    IconButton(onClick={model.action{val r=model.api.request("api/v1/cardNews/$id/like","PATCH",payload("liked" to !liked));liked=r.flag("liked");model.contentChanged()}},enabled=!model.busy){Icon(if(liked)Icons.Default.Favorite else Icons.Default.FavoriteBorder,if(liked)"좋아요 취소" else "좋아요",tint=MaterialTheme.colorScheme.primary)}
                }
                HorizontalPager(pager,Modifier.weight(1f),beyondViewportPageCount=1) { index ->
                    ReadingPage(slides[index]){expandedImage=slides[index].text("imgUrl")}
                }
                readError?.let{Text(it,Modifier.padding(horizontal=24.dp),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
                LinearProgressIndicator(progress={(pager.currentPage+1).toFloat()/slides.size},modifier=Modifier.fillMaxWidth().padding(horizontal=24.dp))
                Button(onClick={if(pager.currentPage==slides.lastIndex) model.action{if(!read){model.api.request("api/v1/cardNews/$id/read","PUT");read=true;model.contentChanged()};back()} else scope.launch{pager.animateScrollToPage(pager.currentPage+1)}},enabled=!model.busy,modifier=Modifier.fillMaxWidth().padding(20.dp)){Text(if(pager.currentPage==slides.lastIndex) "다 읽었어요" else "다음 카드")}
            }
        }
    } }
    expandedImage?.let { raw -> ImageViewer(raw){expandedImage=null} }
}

@Composable private fun ReadingPage(slide:JsonElement,expand:()->Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal=12.dp,vertical=8.dp)) {
        val density=LocalDensity.current
        var textHeight by remember(slide){mutableStateOf(180.dp)}
        val imageHeight=maxOf(minOf(maxWidth*0.75f,260.dp),maxHeight-textHeight)
        val pageHeight=maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Card(Modifier.fillMaxWidth().heightIn(min=pageHeight)) {
                if(slide.text("imgUrl").isNotEmpty()) Box {
                    RemoteImage(slide.text("imgUrl"),null,Modifier.fillMaxWidth().height(imageHeight))
                    FilledTonalIconButton(onClick=expand,modifier=Modifier.align(Alignment.BottomEnd).padding(12.dp)){Icon(Icons.Default.ZoomIn,"이미지 확대")}
                }
                Column(Modifier.onSizeChanged{textHeight=with(density){it.height.toDp()}}.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text(slide.text("title"),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    Text(slide.text("content"),style=MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable private fun ImageViewer(raw:String,close:()->Unit) {
    var scale by remember(raw){mutableFloatStateOf(1f)}
    var offset by remember(raw){mutableStateOf(Offset.Zero)}
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            Page("이미지 확대",close,actions={TextButton(onClick={scale=1f;offset=Offset.Zero}){Text("원래 크기")}}) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                        val density=LocalDensity.current
                        val width=with(density){maxWidth.toPx()};val height=with(density){maxHeight.toPx()}
                        val transform=rememberTransformableState { zoom,pan,_ ->
                            scale=(scale*zoom).coerceIn(1f,6f)
                            offset=Offset((offset.x+pan.x).coerceIn(-width*(scale-1)/2,width*(scale-1)/2),(offset.y+pan.y).coerceIn(-height*(scale-1)/2,height*(scale-1)/2))
                        }
                        RemoteImage(raw,"확대 가능한 카드 이미지",Modifier.fillMaxSize().graphicsLayer {scaleX=scale;scaleY=scale;translationX=offset.x;translationY=offset.y}.transformable(transform),fullResolution=true)
                    }
                    Text("두 손가락으로 확대하고 드래그해서 살펴보세요.",Modifier.padding(20.dp),style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
