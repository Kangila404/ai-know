package org.aiknow.android.data

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun adminDate(raw: String): String = if(raw.isBlank()) "미제공" else runCatching {
    OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))
}.getOrElse { raw.take(16).replace('T',' ').replace('-','.') }

fun approvalGuide(type: String) = when(type) {
    "AI_THEORY" -> "승인 즉시 아카이브에 게시됩니다. 뉴스가 없는 날에는 대신 발송될 수 있습니다."
    "NEWS" -> "승인 후 발송 대기로 이동합니다. 자정 이전 승인된 뉴스 중 하루 한 건을 선정하고, 첫 유효 발송 시작 시 게시합니다. 다음 날 발송이 보장되지는 않습니다."
    else -> "뉴스 또는 AI 이론을 직접 선택해 승인 후 처리 방식을 확인해 주세요."
}

suspend fun articleWithStatistics(api: Gateway, article: JsonElement): JsonElement {
    if(article.obj()["totalViews"] is JsonPrimitive && article.obj()["totalViews"] != JsonNull) return article
    val news=article.obj()["news"] ?: return article
    return try {
        val found=api.request("api/v1/admin/analytics/cards",query=mapOf("query" to news.text("title"),"size" to "100"))
            .rows().firstOrNull{it.number("id")==news.number("id")} ?: return article
        JsonObject(article.obj()+mapOf("totalViews" to (found.obj()["totalViews"] ?: JsonNull),"uniqueViewers" to (found.obj()["uniqueViewers"] ?: JsonNull)))
    } catch(e:CancellationException){throw e} catch(_:Exception){article}
}

fun JsonElement.viewCounts():String {
    val total=obj()["totalViews"]?.takeIf{it!=JsonNull} ?: return "조회수 미제공"
    val unique=obj()["uniqueViewers"]?.takeIf{it!=JsonNull}
    return "조회 ${total}회 · 고유 ${unique ?: "미제공"}"
}

fun draftValidation(draft:JsonElement, demo:Boolean=false):String? {
    fun imageValid(image:JsonElement?):Boolean {
        if(image==null||image==JsonNull)return true
        val url=image.text("url")
        val source=image.text("sourceUrl")
        val origin=image.text("origin")
        if(!(demo&&url.startsWith("android.resource://"))&&url.toHttpUrlOrNull()==null)return false
        if(url.length>2048||image.text("credit").length>500||origin !in listOf("SOURCE","GENERATED"))return false
        if(source.isNotEmpty()&&(source.toHttpUrlOrNull()?.scheme!="https"||source.length>2048))return false
        return origin!="SOURCE" || (source.isNotBlank()&&image.text("credit").isNotBlank())
    }
    if(draft.text("title").isBlank()||draft.text("title").length>200)return "제목을 1~200자로 입력해 주세요."
    if(draft.text("summary").isBlank()||draft.text("summary").length>5000)return "요약을 1~5,000자로 입력해 주세요."
    if(draft.array("keyPoints").size>10||draft.array("keyPoints").any{(it as? JsonPrimitive)?.contentOrNull.let{v->v.isNullOrBlank()||v.length>1000}})return "핵심 포인트는 1,000자 이내, 최대 10개입니다."
    if(!imageValid(draft.obj()["titleImage"]))return "대표 이미지 URL과 출처를 확인해 주세요."
    val slides=draft.array("slides")
    if(slides.size !in 1..10)return "슬라이드는 1~10장이 필요합니다."
    for((index,s) in slides.withIndex()) {
        if(s.text("title").isBlank()||s.text("title").length>200||s.text("content").isBlank()||s.text("content").length>5000)return "슬라이드 ${index+1}의 제목과 본문을 확인해 주세요."
        if(s.text("layout").length>100)return "레이아웃은 100자 이내입니다."
        if(!imageValid(s.obj()["image"]))return "슬라이드 ${index+1}의 이미지 URL과 출처를 확인해 주세요."
    }
    return null
}
