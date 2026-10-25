package org.aiknow.android
import android.content.Context
import kotlinx.serialization.json.*
import org.aiknow.android.data.*
object Platform {
    const val demo = true
    fun gateway(context: Context): Gateway = DemoGateway(context)
}
/** No HTTP client, OAuth session, production keys, or real push in the demo source set. */
class DemoGateway(context:Context):Gateway {
    private val fixture=json.parseToJsonElement(context.assets.open("demo.json").bufferedReader().use{it.readText()}.let { source ->
        // Coil 3 uses numeric resource IDs; resource-name URIs are unsupported.
        mapOf("demo_card_1" to R.drawable.demo_card_1, "demo_card_2" to R.drawable.demo_card_2, "demo_card_3" to R.drawable.demo_card_3)
            .entries.fold(source) { text, (name, id) -> text.replace("android.resource://org.aiknow.android.demo/drawable/$name", "android.resource://${context.packageName}/$id") }
    })
    private var signedIn=false
    private var user=payload("userId" to "demo-admin","nickname" to "AI 탐험가","userRole" to "ADMIN","onboardingCompleted" to false,"profile" to payload("id" to 1,"name" to "루미","imgUrl" to "android.resource://${context.packageName}/${R.drawable.demo_avatar}"))
    private var cards=fixture.array("cards")
    private var setting=payload("settingTime" to "09:00:00","isAllowed" to false)
    private var submissions=fixture.array("submissions")
    private var notices=listOf(payload("id" to 1,"title" to "AI KNOW에 오신 것을 환영해요","content" to "이 화면은 데모 데이터입니다. 운영 데이터는 변경되지 않습니다.","published" to true,"publishedAt" to "2026-10-25T00:00:00Z"))
    override var onUnauthorized:()->Unit={}
    override fun clearSession(){signedIn=false}
    private fun changed(value:JsonElement,vararg fields:Pair<String,Any?>)=JsonObject(value.obj()+payload(*fields))
    private fun page(rows:List<JsonElement>,query:Map<String,String>)=payload("content" to JsonArray(if(query["page"].orEmpty().toIntOrNull()?.let{it>0}==true)emptyList()else rows),"last" to true)
    override suspend fun request(path:String,method:String,body:JsonElement?,query:Map<String,String>):JsonElement {
        if(path=="api/v1/auth/social/providers")return JsonArray(emptyList())
        if(path=="api/v1/auth/demo"){signedIn=true;return JsonNull}
        if(!signedIn)throw ApiException(401)
        val b=body?:JsonNull
        val id=path.substringAfterLast('/').toLongOrNull()
        when(path){
            "api/v1/users"->return user
            "api/v1/users/onboarding"->{user=changed(user,"onboardingCompleted" to true);return user}
            "api/v1/users/nickname"->{user=changed(user,"nickname" to b.text("nickname"));return user}
            "api/v1/auth/logout","api/v1/users/me"->{signedIn=false;return JsonNull}
            "api/v1/cardNews/categories"->return JsonArray(listOf(payload("id" to 1,"name" to "AI 기초"),payload("id" to 2,"name" to "생성형 AI")))
            "api/v1/cardNews/today"->return cards.first()
            "api/v1/cardNews"->return JsonArray(if(query["page"]?.toIntOrNull()?.let{it>0}==true)emptyList()else cards.filter{it.text("title").contains(query["query"].orEmpty(),true)&& (query["isOnlyLiked"]!="true"||it.flag("liked"))&&(query["categoryId"]==null||it.array("categories").any{c->c.text("id")==query["categoryId"]})})
            "api/v1/cardNews/cardSlides"->return fixture.obj()["slides"]!!.obj()[query["cardNewsId"]]?:JsonArray(emptyList())
            "api/v1/notification-setting"->{if(method=="PUT")setting=b.obj();return setting}
            "api/v1/profile"->return JsonArray(listOf(user.obj()["profile"]!!))
            "api/v1/notices","api/v1/admin/notices"->{if(method=="POST"){val n=changed(b,"id" to notices.size+1);notices=notices+n;return n};return page(notices,query)}
            "api/v1/admin/news-submissions"->return page(submissions.filter{it.text("status")==query["status"]},query)
            "api/v1/admin/card-news"->return page(cards.map{article(it)}.filter{query["status"].isNullOrEmpty()||it.text("publicationStatus")==query["status"]},query)
            "api/v1/admin/users"->return page(listOf(payload("id" to 2,"version" to 0,"userId" to "demo-reader","nickname" to "새로운 독자","role" to "USER","suspended" to false)),query)
            "api/v1/admin/analytics/summary"->return payload("users" to 12,"suspendedUsers" to 0,"publishedCards" to cards.size,"readyCards" to 0,"totalViews" to 128,"uniqueViewers" to 9)
            "api/v1/admin/analytics/cards"->return page(cards.map{changed(it,"totalViews" to 42,"uniqueViewers" to 9,"publicationStatus" to "PUBLISHED")},query)
            "api/v1/admin/generations","api/v1/admin/editorial/history"->return page(emptyList(),query)
        }
        if(path.startsWith("api/v1/users/profile/"))return user
        if(path.startsWith("api/v1/notices/")||path.startsWith("api/v1/admin/notices/")){
            val n=notices.firstOrNull{it.number("id")==id}?:throw ApiException(404)
            if(method=="PUT"){val next=changed(b,"id" to id);notices=notices.map{if(it==n)next else it};return next}
            if(method=="DELETE"){notices=notices-n;return JsonNull}
            return n
        }
        if(path.startsWith("api/v1/cardNews/")){
            val cardId=path.split('/')[3].toLong()
            val card=cards.first{it.number("id")==cardId}
            if(path.endsWith("/like")||path.endsWith("/read")){
                val next=if(path.endsWith("/like"))changed(card,"liked" to b.flag("liked"))else changed(card,"read" to true)
                cards=cards.map{if(it==card)next else it};return next
            }
            return card
        }
        if(path.startsWith("api/v1/admin/news-submissions/")){
            val sid=path.split('/')[4].toLong()
            val current=submissions.first{it.number("id")==sid}
            val next=when{
                method=="PUT"->changed(current,"draft" to b.obj()["draft"],"title" to b.obj()["draft"]?.text("title"),"version" to current.number("version")+1)
                path.endsWith("approve")->changed(current,"status" to "APPROVED","publicationStatus" to if(b.text("contentType")=="AI_THEORY")"PUBLISHED" else "READY")
                path.endsWith("reject")->changed(current,"status" to "DENIED","reviewNote" to b.text("reason"))
                path.endsWith("reopen")->changed(current,"status" to "PENDING")
                else->current
            }
            submissions=submissions.map{if(it==current)next else it};return next
        }
        if(path.startsWith("api/v1/admin/card-news/")&&method=="GET")return article(cards.first{it.number("id")==id})
        if(path.startsWith("api/v1/admin/users/")&&method=="GET")return payload("id" to id,"version" to 0,"userId" to "demo-reader","nickname" to "새로운 독자","role" to "USER","suspended" to false)
        throw ApiException(422,"DEMO_UNSUPPORTED","이 작업은 실제 연결 빌드에서 사용할 수 있습니다.")
    }
    private fun article(c:JsonElement)=payload("version" to 0,"publicationStatus" to "PUBLISHED","news" to c,"slides" to fixture.obj()["slides"]?.obj()?.get(c.text("id")),"totalViews" to 42,"uniqueViewers" to 9,"approvedAt" to "2026-10-25T00:00:00Z")
}
