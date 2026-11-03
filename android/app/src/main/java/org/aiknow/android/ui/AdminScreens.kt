@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.aiknow.android.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.text.selection.SelectionContainer
import coil3.compose.AsyncImage
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import org.aiknow.android.AppModel
import org.aiknow.android.Platform
import org.aiknow.android.data.*

private val titles=mapOf("alerts" to "자동화·발송 알림","submissions" to "콘텐츠 검수","articles" to "카드뉴스 관리","users" to "유저 관리","notices" to "공지 관리","profiles" to "프로필 이미지 관리","generations" to "생성 복구")
private fun endpoint(kind:String)=when(kind){"submissions"->"api/v1/admin/news-submissions";"articles"->"api/v1/admin/card-news";"profiles"->"api/v1/profile";else->"api/v1/admin/$kind"}
fun JsonElement.changed(key:String,value:JsonElement):JsonObject=JsonObject(obj()+ (key to value))
@Composable fun AdminHome(navigate:(String)->Unit) {
    Page("관리자") { padding->LazyColumn(Modifier.padding(padding),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Text("콘텐츠와 서비스를 관리하세요",style=MaterialTheme.typography.headlineSmall)}
        item{Text("이론은 승인 즉시 아카이브에 공개됩니다. 뉴스는 발송이 시작되면 공개됩니다.",style=MaterialTheme.typography.bodyMedium)}
        listOf("콘텐츠 운영" to listOf("alerts","submissions","articles"),"서비스 관리" to listOf("notices","users","profiles"),"문제 해결" to listOf("generations")).forEach { (group,kinds) ->
            item{SectionTitle(group)}
            items(kinds){kind->ElevatedCard(onClick={navigate("admin/$kind")},modifier=Modifier.fillMaxWidth()){ListItem(headlineContent={Text(titles.getValue(kind))},supportingContent={Text(when(kind){"submissions"->"뉴스·AI 이론의 접수 및 승인";"articles"->"게시 중 · 발송 대기 · 숨김 · 조회수";"generations"->"저장된 생성 응답 복구 · 새 유료 생성 없음";else->"서비스 정보 관리"})},trailingContent={Icon(Icons.Default.ChevronRight,null)})}}
        }
    } }
}
@Composable fun AdminList(model:AppModel,kind:String,back:()->Unit,open:(String,Long)->Unit) {
    var page by remember(kind){mutableIntStateOf(0)}
    var filter by remember(kind){mutableStateOf(if(kind=="submissions")"PENDING" else if(kind=="articles")"PUBLISHED" else "")}
    var role by remember(kind){mutableStateOf("")}
    var suspended by remember(kind){mutableStateOf("")}
    var query by remember(kind){mutableStateOf("")}
    var revision by remember{mutableIntStateOf(0)}
    val filters=when(kind){"submissions"->listOf("PENDING","APPROVED","DENIED");"articles"->listOf("PUBLISHED","READY","HIDDEN");else->emptyList()}
    Page(titles[kind]?:"관리",back,actions={Refresh{revision++};if(kind in listOf("notices","profiles"))IconButton(onClick={open(kind,0)}){Icon(Icons.Default.Add,"추가")}}) { padding->
        Column(Modifier.padding(padding)) {
            if(filters.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){filters.forEach{f->FilterChip(filter==f,{filter=f;page=0},label={Text(if(f.isEmpty())"전체" else statusLabel(f))})}}
            if(kind in listOf("alerts","submissions","articles"))Text(when {kind=="submissions"->"뉴스와 AI 이론 모두 이곳에서 검수합니다. 승인할 때 게시·발송 유형을 선택합니다.";filter=="READY"->"첫 유효 발송 시작 시 게시됩니다. 다음 날 발송이 보장되지는 않습니다.";filter=="HIDDEN"->"아카이브에 노출되지 않으며 발송되지 않습니다.";else->"아카이브에 공개된 카드입니다. AI 이론은 승인 즉시 게시됩니다."},Modifier.padding(horizontal=20.dp,vertical=8.dp),style=MaterialTheme.typography.bodySmall)
            if(kind=="users") {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("" to "전체 권한","ADMIN" to "관리자","USER" to "일반 사용자").forEach{(v,t)->FilterChip(role==v,{role=v;page=0},label={Text(t)})}}
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("" to "전체 상태","false" to "정상","true" to "이용 정지").forEach{(v,t)->FilterChip(suspended==v,{suspended=v;page=0},label={Text(t)})}}
            }
            if(kind=="users")OutlinedTextField(query,{query=it.take(200);page=0},Modifier.fillMaxWidth().padding(20.dp),label={Text("닉네임 또는 계정 ID")},singleLine=true)
            LoadPane("$kind:$page:$filter:$query:$role:$suspended:$revision",{
                val params=mutableMapOf("page" to "$page","size" to "20")
                if(filter.isNotEmpty())params["status"]=filter
                if(kind=="users")params["query"]=query
                if(kind=="users"){if(role.isNotEmpty())params["role"]=role;if(suspended.isNotEmpty())params["suspended"]=suspended}
                model.api.request(endpoint(kind),query=params)
            }) { data,_ ->
                LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    items(data.rows()){row->
                        val article=if(kind=="articles")row.obj()["news"]?:row else row
                        val id=article.number("id")
                        ElevatedCard(onClick={if(kind!="alerts") open(kind,id) else if(row.number("submissionId")>0) open("submissions",row.number("submissionId")) else if(row.number("cardNewsId")>0) open("articles",row.number("cardNewsId"))},modifier=Modifier.fillMaxWidth()){
                            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                                Text(article.text("title",article.text("nickname",article.text("name",article.text("task","기록 #$id")))) ,style=MaterialTheme.typography.titleMedium)
                                if(kind=="alerts") { Text(row.text("body")); Text(adminDate(row.text("createdAt")),style=MaterialTheme.typography.bodySmall) }
                                else if(kind=="users")Text("${statusLabel(row.text("role"))} · ${if(row.flag("suspended"))"이용 정지" else "정상"}")
                                else Text(statusLabel(row.text("status",row.text("publicationStatus"))),style=MaterialTheme.typography.labelMedium)
                                if(kind=="articles")LoadPane("counts:$id:$revision",{articleWithStatistics(model.api,row)}){stats,_->Text(stats.viewCounts(),style=MaterialTheme.typography.bodySmall)}
                                if(kind=="articles")Text("${statusLabel(article.text("contentType"))} · ${if(article.text("publicationDate").isNotBlank())"게시 ${adminDate(article.text("publicationDate"))}" else "승인 ${adminDate(row.text("approvedAt"))}"}",style=MaterialTheme.typography.bodySmall)
                                if(kind=="submissions")Text("접수 ${adminDate(row.text("createdAt"))}",style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if(data.rows().isEmpty())item{EmptyState("해당 항목이 없습니다")}
                    if(data is JsonObject)item{Row(Modifier.fillMaxWidth()){TextButton(onClick={page--},enabled=page>0){Text("이전")};Spacer(Modifier.weight(1f));TextButton(onClick={page++},enabled=!data.flag("last")){Text("다음")}}}
                }
            }
        }
    }
}
@Composable fun AdminDetail(model:AppModel,kind:String,id:Long,back:()->Unit,openArticle:(Long)->Unit = {}) {
    var revision by remember{mutableIntStateOf(0)}
    Page(titles[kind]?:"상세",back,actions={Refresh{revision++}}) { padding->Box(Modifier.padding(padding)){
        LoadPane("$kind:$id:$revision",{
            if(id==0L)payload("title" to "","content" to "","published" to false,"name" to "","imgUrl" to "")
            else if(kind=="profiles") model.api.request(endpoint(kind)).rows().first{it.number("id")==id}
            else model.api.request("${endpoint(kind)}/$id").let{if(kind=="articles")articleWithStatistics(model.api,it)else it}
        }) { data,_ ->
            val reload={revision++;Unit}
            when(kind){
                "submissions","articles" -> EditorialDetail(model,kind,id,data,reload,openArticle)
                "users" -> AccountDetail(model,id,data,reload)
                "notices","profiles" -> ResourceEditor(model,kind,id,data,back,reload)
                "generations" -> GenerationDetail(model,id,data,reload)
            }
        }
    } }
}
private fun articleDraft(data:JsonElement):JsonElement {
    val n=data.obj()["news"]?:JsonNull
    fun image(s:JsonElement,prefix:Boolean):JsonElement? {
        val url=s.text(if(prefix)"titleImgUrl" else "imgUrl")
        if(url.isBlank())return null
        return payload("url" to url,"origin" to s.text(if(prefix)"titleImageOrigin" else "imageOrigin","GENERATED"),"sourceUrl" to s.obj()[if(prefix)"titleImageSourceUrl" else "imageSourceUrl"],"credit" to s.obj()[if(prefix)"titleImageCredit" else "imageCredit"])
    }
    return payload("sourceTitle" to n.text("sourceTitle"),"sourceUrl" to n.text("sourceUrl"),"publishedAt" to n.text("sourcePublishedAt"),"title" to n.text("title"),"summary" to n.text("summary"),"keyPoints" to n.obj()["keyPoints"],"titleImage" to image(n,true),"slides" to JsonArray(data.array("slides").map{s->payload("sequence" to s.number("sequence"),"title" to s.text("title"),"content" to s.text("content"),"layout" to s.obj()["layout"],"image" to image(s,false))}))
}
@Composable private fun EditorialDetail(model:AppModel,kind:String,id:Long,data:JsonElement,reload:()->Unit,openArticle:(Long)->Unit) {
    val initial=if(kind=="articles")articleDraft(data) else data.obj()["draft"]?:JsonNull
    var editing by remember(data){mutableStateOf(false)}
    var draft by remember(data){mutableStateOf(initial)}
    var reason by remember(data){mutableStateOf("")}
    var type by remember(data){mutableStateOf(data.text("contentType").takeIf{it in listOf("NEWS","AI_THEORY")} ?: "")}
    var preview by remember(data){mutableStateOf(false)}
    var confirming by remember{mutableStateOf<String?>(null)}
    var categories by remember{mutableStateOf<List<JsonElement>>(emptyList())}
    var selected by remember{mutableStateOf<Set<Long>>(emptySet())}
    LaunchedEffect(Unit){try{categories=model.api.request("api/v1/cardNews/categories").rows()}catch(_:Exception){}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        StatusChip(data.text("status",data.text("publicationStatus")))
        if(kind=="submissions")Text("접수 ${adminDate(data.text("createdAt"))}",style=MaterialTheme.typography.bodySmall)
        Text("원문 작성 ${adminDate(initial.text("publishedAt"))}",style=MaterialTheme.typography.bodySmall)
        listOf("reviewedAt" to "검수","approvedAt" to "승인","firstUsedAt" to "첫 발송 시작","lastUsedAt" to "최근 발송 시작").forEach{(k,t)->if(data.text(k).isNotEmpty())Text("$t  ${adminDate(data.text(k))}",style=MaterialTheme.typography.bodySmall)}
        if(kind=="articles") {
            val news=data.obj()["news"]?:JsonNull
            Text("콘텐츠 유형: ${statusLabel(news.text("contentType"))}")
            Text(data.viewCounts(),style=MaterialTheme.typography.bodySmall)
            if(news.text("publicationDate").isNotEmpty())Text("게시 ${adminDate(news.text("publicationDate"))}",style=MaterialTheme.typography.bodySmall)
            if(data.obj()["hasStartedDelivery"]==JsonPrimitive(false))Text("아직 발송을 시작하지 않았습니다.",style=MaterialTheme.typography.bodySmall)
        }
        if(data.text("reviewNote").isNotEmpty()) Text("검수 의견: ${data.text("reviewNote")}")
        if(kind=="submissions"&&data.number("cardNewsId")>0)TextButton(onClick={openArticle(data.number("cardNewsId"))}){Text("승인된 카드 관리")}
        if(initial!=JsonNull) {
            Text(initial.text("title"),style=MaterialTheme.typography.headlineSmall)
            if(editing) DraftFields(draft){draft=it}
            else {
                TextButton(onClick={preview=!preview}){Text("콘텐츠 미리보기 ${if(preview)"접기" else "펼치기"}")}
                if(preview) {
                Text(initial.text("title"),style=MaterialTheme.typography.headlineSmall)
                Text(initial.text("summary"))
                initial.obj()["titleImage"]?.takeIf{it!=JsonNull}?.let{RemoteImage(it.text("url"),null,Modifier.fillMaxWidth().height(200.dp));Text(it.text("credit"),style=MaterialTheme.typography.bodySmall)}
                initial.array("slides").forEach{s->SectionTitle("${s.number("sequence")}. ${s.text("title")}");s.obj()["image"]?.takeIf{it!=JsonNull}?.let{i->RemoteImage(i.text("url"),null,Modifier.fillMaxWidth().height(200.dp));Text(i.text("credit"),style=MaterialTheme.typography.bodySmall);SourceLink("이미지 출처",i.text("sourceUrl"))};Text(s.text("content"))}
                SourceLink("원문",initial.text("sourceUrl"))
                }
            }
            if(kind=="articles"||data.text("status")!="APPROVED")OutlinedButton(onClick={editing=!editing;draft=initial},enabled=!model.busy){Text(if(editing)"편집 취소" else "내용 수정")}
        }
        if(kind=="articles"||data.text("status")!="APPROVED")OutlinedTextField(reason,{reason=it.take(2000)},Modifier.fillMaxWidth(),label={Text("변경 / 검수 사유")},minLines=2)
        if(editing) {
            val validation=draftValidation(draft,Platform.demo)
            validation?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            Button(onClick={confirming="edit"},enabled=!model.busy&&reason.isNotBlank()&&validation==null){Text("수정 저장")}
        }
        if(!editing&&kind=="submissions"){
            if(data.text("status")=="PENDING"){
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("NEWS","AI_THEORY").forEach{v->FilterChip(type==v,{type=v},label={Text(statusLabel(v))})}}
                Text(approvalGuide(type),style=MaterialTheme.typography.bodySmall)
                if(categories.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){categories.forEach{c->val cid=c.number("id");FilterChip(cid in selected,{selected=if(cid in selected)selected-cid else (selected+cid).take(10).toSet()},label={Text(c.text("name"))})}}
                Button(onClick={confirming="approve"},enabled=!model.busy&&type in listOf("NEWS","AI_THEORY")){Text("승인")}
                OutlinedButton(onClick={confirming="reject"},enabled=!model.busy&&reason.isNotBlank()){Text("반려")}
            } else if(data.text("status")=="DENIED")OutlinedButton(onClick={confirming="reopen"},enabled=!model.busy&&reason.isNotBlank()){Text("재검수로 되돌리기")}
        }
        if(!editing&&kind=="articles"&&data.text("publicationStatus")!="READY")OutlinedButton(onClick={confirming=if(data.text("publicationStatus")=="HIDDEN")"restore" else "hide"},enabled=!model.busy&&reason.isNotBlank()){Text(if(data.text("publicationStatus")=="HIDDEN")"공개 복원" else "숨기기")}
        if(kind=="articles"&&data.text("publicationStatus")=="READY")Text("최초 발송 전 콘텐츠는 공개 상태를 변경할 수 없습니다.",style=MaterialTheme.typography.bodySmall)
        History(model,if(kind=="articles")"ARTICLE" else "SUBMISSION",id)
    }
    confirming?.let{action->Confirm("${mapOf("edit" to "수정 저장","approve" to "승인","reject" to "반려","reopen" to "재검수","hide" to "숨김","restore" to "공개 복원")[action]}할까요?",(if(action=="approve")approvalGuide(type)+"\n\n" else "")+"승인·반려는 서버에 저장된 초안을 기준으로 처리합니다. 내용을 수정했다면 먼저 수정 저장을 해 주세요.",{confirming=null}){model.action{
        val body=when(action){"edit"->payload("version" to data.number("version"),"reason" to reason.trim(),"draft" to draft);"approve"->payload("contentType" to type,"categoryIds" to JsonArray(selected.map{JsonPrimitive(it)}));"reject"->payload("reason" to reason.trim());else->payload("version" to data.number("version"),"reason" to reason.trim())}
        model.api.request("${endpoint(kind)}/$id"+if(action=="edit")"" else "/$action",if(action=="edit")"PUT" else "POST",body);reload()
    }}}
}
@Composable private fun DraftFields(draft:JsonElement,change:(JsonElement)->Unit) {
    listOf("title" to "제목","summary" to "요약").forEach{(k,t)->OutlinedTextField(draft.text(k),{change(draft.changed(k,JsonPrimitive(it.take(if(k=="title")200 else 5000))))},Modifier.fillMaxWidth(),label={Text(t)})}
    OutlinedTextField(draft.array("keyPoints").joinToString("\n"){(it as JsonPrimitive).content},{change(draft.changed("keyPoints",JsonArray(it.lines().filter{v->v.isNotBlank()}.take(10).map{v->JsonPrimitive(v.take(1000))})))},Modifier.fillMaxWidth(),label={Text("핵심 포인트 (줄마다 하나)")})
    ImageFields("대표 이미지",draft.obj()["titleImage"]){change(draft.changed("titleImage",it))}
    draft.array("slides").forEachIndexed{index,s->
        SectionTitle("슬라이드 ${index+1}")
        fun update(next:JsonElement){change(draft.changed("slides",JsonArray(draft.array("slides").toMutableList().apply{set(index,next)})))}
        listOf("title" to "제목","content" to "본문").forEach{(k,t)->OutlinedTextField(s.text(k),{update(s.changed(k,JsonPrimitive(it.take(if(k=="title")200 else 5000))))},Modifier.fillMaxWidth(),label={Text(t)},minLines=if(k=="content")3 else 1)}
        OutlinedTextField(s.text("layout"),{update(s.changed("layout",if(it.isBlank())JsonNull else JsonPrimitive(it.take(100))))},Modifier.fillMaxWidth(),label={Text("레이아웃")})
        ImageFields("이미지",s.obj()["image"]){update(s.changed("image",it))}
        TextButton(onClick={change(draft.changed("slides",JsonArray(draft.array("slides").filterIndexed{i,_->i!=index}.mapIndexed{i,v->v.changed("sequence",JsonPrimitive(i+1))})))},enabled=draft.array("slides").size>1){Text("슬라이드 ${index+1} 삭제")}
    }
    OutlinedButton(onClick={change(draft.changed("slides",JsonArray(draft.array("slides")+payload("sequence" to draft.array("slides").size+1,"title" to "","content" to "","layout" to null,"image" to null))))},enabled=draft.array("slides").size<10){Text("슬라이드 추가")}
}
@Composable private fun ImageFields(title:String, image:JsonElement?,change:(JsonElement)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick={expanded=!expanded}){Text("$title / 출처 편집")}
    if(expanded){
        val enabled=image!=null&&image!=JsonNull
        Row{Checkbox(enabled,{change(if(it)payload("url" to "","origin" to "GENERATED","credit" to null,"sourceUrl" to null) else JsonNull)});Text("이미지 사용")}
        if(enabled){val value=image!!;RemoteImage(value.text("url"),null,Modifier.fillMaxWidth().height(120.dp));listOf("url" to "이미지 URL","credit" to "출처 표기","sourceUrl" to "원본 출처 URL").forEach{(k,t)->OutlinedTextField(value.text(k),{change(value.changed(k,if(k!="url"&&it.isEmpty())JsonNull else JsonPrimitive(it.take(if(k=="credit")500 else 2048))))},Modifier.fillMaxWidth(),label={Text(t)})};Row{listOf("SOURCE" to "출처 이미지","GENERATED" to "AI 생성").forEach{(v,t)->FilterChip(value.text("origin")==v,{change(value.changed("origin",JsonPrimitive(v)))},label={Text(t)})}}}
    }
}
@Composable private fun AccountDetail(model:AppModel,id:Long,data:JsonElement,reload:()->Unit) {
    var role by remember(data){mutableStateOf(data.text("role"))};var suspended by remember(data){mutableStateOf(data.flag("suspended"))};var reason by remember(data){mutableStateOf("")};var confirming by remember{mutableStateOf(false)}
    val self=data.text("userId")==model.user?.text("userId")
    Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Text(data.text("nickname"),style=MaterialTheme.typography.headlineSmall);Text(data.text("userId"),style=MaterialTheme.typography.bodySmall)
        if(data.text("createdAt").isNotEmpty())Text("가입 ${adminDate(data.text("createdAt"))}")
        if(data.text("lastLoginAt").isNotEmpty())Text("최근 로그인 ${adminDate(data.text("lastLoginAt"))}")
        Row{listOf("USER","ADMIN").forEach{v->FilterChip(role==v,{role=v},enabled=!self&&!model.busy,label={Text(statusLabel(v))})}}
        ListItem(headlineContent={Text("이용 정지")},trailingContent={Switch(suspended,{suspended=it},enabled=!self&&!model.busy)})
        if(self)Text("본인의 관리자 권한과 이용 상태는 변경할 수 없습니다.",style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(reason,{reason=it.take(2000)},Modifier.fillMaxWidth(),label={Text("변경 사유 (필수)")})
        Button(onClick={confirming=true},enabled=!self&&!model.busy&&reason.isNotBlank()&&(role!=data.text("role")||suspended!=data.flag("suspended"))){Text("변경 저장")};History(model,"USER",id)
    }
    if(confirming)Confirm("계정 권한과 상태를 변경할까요?","기존 로그인에도 적용됩니다.",{confirming=false}){model.action{model.api.request("api/v1/admin/users/$id","PATCH",payload("version" to data.number("version"),"role" to role,"suspended" to suspended,"reason" to reason.trim()));reload()}}
}
@Composable private fun ResourceEditor(model:AppModel,kind:String,id:Long,data:JsonElement,back:()->Unit,reload:()->Unit) {
    var edited by remember(data){mutableStateOf(data)};var deleting by remember{mutableStateOf(false)}
    val path=if(kind=="profiles")"api/v1/admin/profile" else endpoint(kind)
    Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        val fields=if(kind=="profiles")listOf("name" to "이름 (20자 이내)","imgUrl" to "이미지 경로 (20자 이내)") else listOf("title" to "제목","content" to "내용")
        fields.forEach{(k,t)->OutlinedTextField(edited.text(k),{edited=edited.changed(k,JsonPrimitive(it.take(if(kind=="profiles")20 else if(k=="title")200 else 10000)))},Modifier.fillMaxWidth(),label={Text(t)},minLines=if(k=="content")5 else 1)}
        if(kind=="notices")ListItem(headlineContent={Text("공지 공개")},trailingContent={Switch(edited.flag("published"),{edited=edited.changed("published",JsonPrimitive(it))})})
        Button(onClick={model.action{val body=JsonObject(edited.obj().filterKeys{it in if(kind=="profiles")listOf("name","imgUrl") else listOf("title","content","published")});model.api.request(path+if(id==0L)"" else "/$id",if(id==0L)"POST" else if(kind=="profiles")"PATCH" else "PUT",body);if(id==0L)back()else reload()}},enabled=!model.busy&&fields.all{edited.text(it.first).isNotBlank()}){Text("저장")}
        if(id!=0L)OutlinedButton(onClick={deleting=true},enabled=!model.busy){Text("삭제")}
    }
    if(deleting)Confirm("삭제할까요?","삭제한 항목은 복구할 수 없습니다.",{deleting=false}){model.action{model.api.request("$path/$id","DELETE");back()}}
}
@Composable private fun GenerationDetail(model:AppModel,id:Long,data:JsonElement,reload:()->Unit) {
    var reason by remember{mutableStateOf("")};var response by remember(data){mutableStateOf(data.text("recoveredResponseJson",data.text("responseJson")))};var confirming by remember{mutableStateOf(false)}
    val context=LocalContext.current
    val imageTask=data.text("task").startsWith("image_generation_")
    var png by remember(id){mutableStateOf<ByteArray?>(null)}
    var imageError by remember(id){mutableStateOf<String?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if(uri!=null)model.action{png=null;imageError=null;try{png=recoveryPng(context,uri)}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){imageError=e.message?:"이미지를 확인해 주세요."}}
    }
    Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Text("개발자용 생성 기록",style=MaterialTheme.typography.headlineSmall);Text("저장된 모델 응답을 조사하고 복구합니다. 새 유료 생성이나 콘텐츠 승인을 실행하지 않습니다.")
        Text(data.text("task"));Text(adminDate(data.text("createdAt")));Text(if(data.flag("recovered"))"복구됨" else if(data.flag("responseSaved")||data.text("responseJson").isNotBlank())"응답 저장됨" else "응답 대기");SourceLink("원문",data.text("sourceUrl"))
        listOf("requestJson" to "요청","contextJson" to "이미지 선택 참고 자료","responseJson" to "원본 응답","recoveredResponseJson" to "마지막 복구 응답").forEach{(k,t)->var show by remember{mutableStateOf(false)};TextButton(onClick={show=!show}){Text(t)};if(show)SelectionContainer{Text(data.text(k),style=MaterialTheme.typography.bodySmall)}}
        if(imageTask) {
            Text("4096×4096 이하, PNG 변환 후 10MB 이하 이미지를 선택하세요.",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!model.busy){Text("복구할 이미지 선택")}
            png?.let{AsyncImage(model=it,contentDescription="복구 이미지 미리보기",modifier=Modifier.fillMaxWidth().height(200.dp));TextButton(onClick={png=null}){Text("이미지 선택 취소")}}
            imageError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        } else OutlinedTextField(response,{response=it.take(1_000_000)},Modifier.fillMaxWidth(),label={Text("복구 응답 JSON")},minLines=5)
        OutlinedTextField(reason,{reason=it.take(2000)},Modifier.fillMaxWidth(),label={Text("복구 사유")})
        val valid=runCatching{json.parseToJsonElement(response) is JsonObject}.getOrDefault(false)
        Button(onClick={confirming=true},enabled=!model.busy&&(if(imageTask)png!=null else valid)&&reason.isNotBlank()){Text(if(imageTask)"선택한 이미지로 복구" else "응답 복구")};History(model,"GENERATION",id)
    }
    if(confirming)Confirm("저장된 응답을 복구할까요?","다음 워크플로 실행에서 재사용될 수 있습니다.",{confirming=false}){model.action{
        if(imageTask)model.api.recoverImage(id,data.number("version"),reason.trim(),requireNotNull(png))
        else model.api.request("api/v1/admin/generations/$id/recover","POST",payload("version" to data.number("version"),"reason" to reason.trim(),"responseJson" to response))
        png=null;reason="";reload()
    }}
}
@Composable private fun History(model:AppModel,type:String,id:Long) {
    var show by remember{mutableStateOf(false)};var page by remember{mutableIntStateOf(0)}
    TextButton(onClick={show=!show}){Text("변경 이력 ${if(show)"접기" else "보기"}")}
    if(show)LoadPane("history:$type:$id:$page",{model.api.request("api/v1/admin/editorial/history",query=mapOf("resourceType" to type,"resourceId" to "$id","page" to "$page"))}){history,_->
        history.rows().forEach{h->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(h.text("action"));Text(h.text("reason"));Text("${adminDate(h.text("occurredAt"))} · 관리자 ${h.number("actorId")}",style=MaterialTheme.typography.bodySmall)
            listOf("beforeJson" to "변경 전","afterJson" to "변경 후").forEach { (k,t) ->
                if(h.text(k).isNotBlank()) {
                    var expanded by remember(h){mutableStateOf(false)}
                    TextButton(onClick={expanded=!expanded}){Text(t)}
                    if(expanded)SelectionContainer{Text(h.text(k),style=MaterialTheme.typography.bodySmall)}
                }
            }
        }}}
        Row{TextButton(onClick={page--},enabled=page>0){Text("이전")};TextButton(onClick={page++},enabled=!history.flag("last")){Text("다음")}}
    }
}
