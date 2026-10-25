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
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import org.aiknow.android.AppModel
import org.aiknow.android.data.*

private val titles=mapOf("submissions" to "콘텐츠 검수","articles" to "카드뉴스 관리","users" to "유저 관리","analytics" to "조회 통계","notices" to "공지 관리","profiles" to "프로필 이미지 관리","generations" to "생성 기록 · 개발자용")
private fun endpoint(kind:String)=when(kind){"submissions"->"api/v1/admin/news-submissions";"articles"->"api/v1/admin/card-news";"profiles"->"api/v1/profile";"analytics"->"api/v1/admin/analytics/cards";else->"api/v1/admin/$kind"}
fun JsonElement.changed(key:String,value:JsonElement):JsonObject=JsonObject(obj()+ (key to value))
@Composable fun AdminHome(navigate:(String)->Unit) {
    Page("관리자") { padding->LazyColumn(Modifier.padding(padding),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Text("콘텐츠와 서비스를 관리하세요",style=MaterialTheme.typography.headlineSmall)}
        item{Text("이론은 승인 즉시 아카이브에 공개됩니다. 뉴스는 발송이 시작되면 공개됩니다.",style=MaterialTheme.typography.bodyMedium)}
        items(titles.keys.filter{it!="generations"}){kind->ElevatedCard(onClick={navigate("admin/$kind")},modifier=Modifier.fillMaxWidth()){ListItem(headlineContent={Text(titles.getValue(kind))},trailingContent={Icon(Icons.Default.ChevronRight,null)})}}
        item{var advanced by remember{mutableStateOf(false)};TextButton(onClick={advanced=!advanced}){Text("개발자 도구 ${if(advanced) "접기" else "보기"}")};if(advanced){Text("생성 기록은 n8n 오류 조사용이며 콘텐츠 검수와 별개입니다.",style=MaterialTheme.typography.bodySmall);TextButton(onClick={navigate("admin/generations")}){Text("생성 기록 확인")}}}
    } }
}
@Composable fun AdminList(model:AppModel,kind:String,back:()->Unit,open:(String,Long)->Unit) {
    var page by remember(kind){mutableIntStateOf(0)}
    var filter by remember(kind){mutableStateOf(if(kind=="submissions")"PENDING" else "")}
    var query by remember(kind){mutableStateOf("")}
    var revision by remember{mutableIntStateOf(0)}
    val filters=when(kind){"submissions"->listOf("PENDING","APPROVED","DENIED");"articles"->listOf("","PUBLISHED","READY","HIDDEN");else->emptyList()}
    Page(titles[kind]?:"관리",back,actions={Refresh{revision++};if(kind in listOf("notices","profiles"))IconButton(onClick={open(kind,0)}){Icon(Icons.Default.Add,"추가")}}) { padding->
        Column(Modifier.padding(padding)) {
            if(filters.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){filters.forEach{f->FilterChip(filter==f,{filter=f;page=0},label={Text(if(f.isEmpty())"전체" else statusLabel(f))})}}
            if(kind in listOf("users","analytics"))OutlinedTextField(query,{query=it.take(200);page=0},Modifier.fillMaxWidth().padding(20.dp),label={Text("검색")},singleLine=true)
            LoadPane("$kind:$page:$filter:$query:$revision",{
                val params=mutableMapOf("page" to "$page","size" to "20")
                if(filter.isNotEmpty())params["status"]=filter
                if(kind in listOf("users","analytics"))params["query"]=query
                model.api.request(endpoint(kind),query=params)
            }) { data,_ ->
                LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(kind=="analytics")item{LoadPane("stats:$revision",{model.api.request("api/v1/admin/analytics/summary")}){s,_->
                        ElevatedCard{Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){SectionTitle("전체 현황");listOf("users" to "가입자","suspendedUsers" to "정지 계정","publishedCards" to "게시 카드","readyCards" to "발송 대기","totalViews" to "전체 조회","uniqueViewers" to "고유 조회자").forEach{(k,t)->Text("$t   ${s.number(k)}")}}}
                    }}
                    items(data.rows()){row->
                        val article=if(kind=="articles")row.obj()["news"]?:row else row
                        val id=article.number("id")
                        ElevatedCard(onClick={open(if(kind=="analytics")"articles" else kind,id)},modifier=Modifier.fillMaxWidth()){
                            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                                Text(article.text("title",article.text("nickname",article.text("name",article.text("task","기록 #$id")))) ,style=MaterialTheme.typography.titleMedium)
                                if(kind=="users")Text("${statusLabel(row.text("role"))} · ${if(row.flag("suspended"))"이용 정지" else "정상"}")
                                else Text(statusLabel(row.text("status",row.text("publicationStatus"))),style=MaterialTheme.typography.labelMedium)
                                if(kind in listOf("articles","analytics"))Text("조회 ${row.number("totalViews")} · 고유 ${row.number("uniqueViewers")}",style=MaterialTheme.typography.bodySmall)
                                if(row.text("createdAt").isNotEmpty())Text(row.text("createdAt").replace("T"," ").take(16),style=MaterialTheme.typography.bodySmall)
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
@Composable fun AdminDetail(model:AppModel,kind:String,id:Long,back:()->Unit) {
    var revision by remember{mutableIntStateOf(0)}
    Page(titles[kind]?:"상세",back,actions={Refresh{revision++}}) { padding->Box(Modifier.padding(padding)){
        LoadPane("$kind:$id:$revision",{
            if(id==0L)payload("title" to "","content" to "","published" to false,"name" to "","imgUrl" to "")
            else if(kind=="profiles") model.api.request(endpoint(kind)).rows().first{it.number("id")==id}
            else model.api.request("${endpoint(kind)}/$id")
        }) { data,_ ->
            val reload={revision++;Unit}
            when(kind){
                "submissions","articles" -> EditorialDetail(model,kind,id,data,reload)
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
@Composable private fun EditorialDetail(model:AppModel,kind:String,id:Long,data:JsonElement,reload:()->Unit) {
    val initial=if(kind=="articles")articleDraft(data) else data.obj()["draft"]?:JsonNull
    var editing by remember(data){mutableStateOf(false)}
    var draft by remember(data){mutableStateOf(initial)}
    var reason by remember(data){mutableStateOf("")}
    var type by remember(data){mutableStateOf(data.text("contentType",if(initial.text("sourceUrl").contains("theory"))"AI_THEORY" else "NEWS"))}
    var confirming by remember{mutableStateOf<String?>(null)}
    var categories by remember{mutableStateOf<List<JsonElement>>(emptyList())}
    var selected by remember{mutableStateOf<Set<Long>>(emptySet())}
    LaunchedEffect(Unit){try{categories=model.api.request("api/v1/cardNews/categories").rows()}catch(_:Exception){}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        StatusChip(data.text("status",data.text("publicationStatus")))
        listOf("createdAt" to "등록","reviewedAt" to "검수","approvedAt" to "승인","firstUsedAt" to "첫 발송","lastUsedAt" to "최근 발송").forEach{(k,t)->if(data.text(k).isNotEmpty())Text("$t  ${data.text(k).replace("T"," ").take(16)}",style=MaterialTheme.typography.bodySmall)}
        if(data.text("reviewNote").isNotEmpty()) Text("검수 의견: ${data.text("reviewNote")}")
        if(initial!=JsonNull) {
            if(editing) DraftFields(draft){draft=it}
            else {
                Text(initial.text("title"),style=MaterialTheme.typography.headlineSmall)
                Text(initial.text("summary"))
                initial.obj()["titleImage"]?.takeIf{it!=JsonNull}?.let{RemoteImage(it.text("url"),null,Modifier.fillMaxWidth().height(200.dp));Text(it.text("credit"),style=MaterialTheme.typography.bodySmall)}
                initial.array("slides").forEach{s->SectionTitle("${s.number("sequence")}. ${s.text("title")}");s.obj()["image"]?.takeIf{it!=JsonNull}?.let{i->RemoteImage(i.text("url"),null,Modifier.fillMaxWidth().height(200.dp));Text(i.text("credit"),style=MaterialTheme.typography.bodySmall);SourceLink("이미지 출처",i.text("sourceUrl"))};Text(s.text("content"))}
                SourceLink("원문",initial.text("sourceUrl"))
            }
            if(kind=="articles"||data.text("status")=="PENDING")OutlinedButton(onClick={editing=!editing;draft=initial},enabled=!model.busy){Text(if(editing)"편집 취소" else "내용 수정")}
        }
        OutlinedTextField(reason,{reason=it.take(2000)},Modifier.fillMaxWidth(),label={Text("변경 / 검수 사유")},minLines=2)
        if(editing)Button(onClick={model.action{require(draft.text("title").isNotBlank()&&draft.text("summary").isNotBlank()){ "제목과 요약을 입력해 주세요." };model.api.request("${endpoint(kind)}/$id","PUT",payload("version" to data.number("version"),"reason" to reason.trim(),"draft" to draft));reload()}},enabled=!model.busy&&reason.isNotBlank()){Text("수정 저장")}
        if(!editing&&kind=="submissions"){
            if(data.text("status")=="PENDING"){
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("NEWS","AI_THEORY").forEach{v->FilterChip(type==v,{type=v},label={Text(statusLabel(v))})}}
                Text(if(type=="AI_THEORY")"승인하면 아카이브에 바로 공개됩니다." else "승인 후 발송이 시작되면 공개됩니다.",style=MaterialTheme.typography.bodySmall)
                if(categories.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){categories.forEach{c->val cid=c.number("id");FilterChip(cid in selected,{selected=if(cid in selected)selected-cid else (selected+cid).take(10).toSet()},label={Text(c.text("name"))})}}
                Button(onClick={confirming="approve"},enabled=!model.busy){Text("승인")}
                OutlinedButton(onClick={confirming="reject"},enabled=!model.busy&&reason.isNotBlank()){Text("반려")}
            } else if(data.text("status")=="DENIED")OutlinedButton(onClick={confirming="reopen"},enabled=!model.busy&&reason.isNotBlank()){Text("재검수로 되돌리기")}
        }
        if(!editing&&kind=="articles"&&data.text("publicationStatus")!="READY")OutlinedButton(onClick={confirming=if(data.text("publicationStatus")=="HIDDEN")"restore" else "hide"},enabled=!model.busy&&reason.isNotBlank()){Text(if(data.text("publicationStatus")=="HIDDEN")"공개 복원" else "숨기기")}
        History(model,if(kind=="articles")"ARTICLE" else "SUBMISSION",id)
    }
    confirming?.let{action->Confirm("${mapOf("approve" to "승인","reject" to "반려","reopen" to "재검수","hide" to "숨김","restore" to "공개 복원")[action]}할까요?","서버에 저장된 콘텐츠에 적용합니다.",{confirming=null}){model.action{
        val body=when(action){"approve"->payload("contentType" to type,"categoryIds" to JsonArray(selected.map{JsonPrimitive(it)}));"reject"->payload("reason" to reason.trim());else->payload("version" to data.number("version"),"reason" to reason.trim())}
        model.api.request("${endpoint(kind)}/$id/$action","POST",body);reload()
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
        ImageFields("이미지",s.obj()["image"]){update(s.changed("image",it))}
    }
}
@Composable private fun ImageFields(title:String, image:JsonElement?,change:(JsonElement)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick={expanded=!expanded}){Text("$title / 출처 편집")}
    if(expanded){val value=image?.takeIf{it!=JsonNull}?:payload("url" to "","origin" to "GENERATED","credit" to "");listOf("url" to "이미지 URL","credit" to "출처 표기","sourceUrl" to "원본 출처 URL").forEach{(k,t)->OutlinedTextField(value.text(k),{change(value.changed(k,JsonPrimitive(it.take(if(k=="credit")500 else 2048))))},Modifier.fillMaxWidth(),label={Text(t)})};Row{listOf("SOURCE" to "출처 이미지","GENERATED" to "AI 생성").forEach{(v,t)->FilterChip(value.text("origin")==v,{change(value.changed("origin",JsonPrimitive(v)))},label={Text(t)})}}}
}
@Composable private fun AccountDetail(model:AppModel,id:Long,data:JsonElement,reload:()->Unit) {
    var role by remember(data){mutableStateOf(data.text("role"))};var suspended by remember(data){mutableStateOf(data.flag("suspended"))};var reason by remember(data){mutableStateOf("")};var confirming by remember{mutableStateOf(false)}
    val self=data.text("userId")==model.user?.text("userId")
    Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Text(data.text("nickname"),style=MaterialTheme.typography.headlineSmall);Text(data.text("userId"),style=MaterialTheme.typography.bodySmall)
        Row{listOf("USER","ADMIN").forEach{v->FilterChip(role==v,{role=v},enabled=!self&&!model.busy,label={Text(statusLabel(v))})}}
        ListItem(headlineContent={Text("이용 정지")},trailingContent={Switch(suspended,{suspended=it},enabled=!self&&!model.busy)})
        if(self)Text("본인의 관리자 권한과 이용 상태는 변경할 수 없습니다.",style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(reason,{reason=it.take(2000)},Modifier.fillMaxWidth(),label={Text("변경 사유 (필수)")})
        Button(onClick={confirming=true},enabled=!self&&!model.busy&&reason.isNotBlank()){Text("변경 저장")};History(model,"USER",id)
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
    Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Text("개발자용 생성 기록",style=MaterialTheme.typography.headlineSmall);Text("저장된 모델 응답을 조사하고 복구합니다. 새 유료 생성이나 콘텐츠 승인을 실행하지 않습니다.")
        Text(data.text("task"));SourceLink("원문",data.text("sourceUrl"))
        listOf("requestJson" to "요청","contextJson" to "이미지 선택 참고 자료","responseJson" to "원본 응답").forEach{(k,t)->var show by remember{mutableStateOf(false)};TextButton(onClick={show=!show}){Text(t)};if(show)Text(data.text(k),style=MaterialTheme.typography.bodySmall)}
        OutlinedTextField(response,{response=it.take(1_000_000)},Modifier.fillMaxWidth(),label={Text("복구 응답 JSON")},minLines=5)
        OutlinedTextField(reason,{reason=it.take(2000)},Modifier.fillMaxWidth(),label={Text("복구 사유")})
        val valid=runCatching{json.parseToJsonElement(response) is JsonObject}.getOrDefault(false)
        Button(onClick={confirming=true},enabled=!model.busy&&valid&&reason.isNotBlank()){Text("응답 복구")};History(model,"GENERATION",id)
    }
    if(confirming)Confirm("저장된 응답을 복구할까요?","다음 워크플로 실행에서 재사용될 수 있습니다.",{confirming=false}){model.action{model.api.request("api/v1/admin/generations/$id/recover","POST",payload("version" to data.number("version"),"reason" to reason,"responseJson" to response));reload()}}
}
@Composable private fun History(model:AppModel,type:String,id:Long) {
    var show by remember{mutableStateOf(false)};var page by remember{mutableIntStateOf(0)}
    TextButton(onClick={show=!show}){Text("변경 이력 ${if(show)"접기" else "보기"}")}
    if(show)LoadPane("history:$type:$id:$page",{model.api.request("api/v1/admin/editorial/history",query=mapOf("resourceType" to type,"resourceId" to "$id","page" to "$page"))}){history,_->
        history.rows().forEach{h->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(h.text("action"));Text(h.text("reason"));Text("${h.text("occurredAt")} · 관리자 ${h.number("actorId")}",style=MaterialTheme.typography.bodySmall)}}}
        Row{TextButton(onClick={page--},enabled=page>0){Text("이전")};TextButton(onClick={page++},enabled=!history.flag("last")){Text("다음")}}
    }
}
