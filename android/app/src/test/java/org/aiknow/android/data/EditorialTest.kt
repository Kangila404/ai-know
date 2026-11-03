package org.aiknow.android.data
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class EditorialTest {
    @Test fun absentMetadataDoesNotPretendToBeZeroOrSourceDate() {
        assertEquals("미제공",adminDate(""))
        assertEquals("2026.10.25 09:00",adminDate("2026-10-25T00:00:00Z"))
        assertEquals("조회수 미제공",payload("totalViews" to null).viewCounts())
        assertEquals("조회 0회 · 고유 0",payload("totalViews" to 0,"uniqueViewers" to 0).viewCounts())
        assertTrue(approvalGuide("").contains("직접 선택"))
    }
    @Test fun analyticsFallbackMatchesIdRatherThanTitle()=runBlocking {
        val api=object:Gateway {
            override var onUnauthorized:()->Unit={}
            override fun clearSession(){}
            override suspend fun request(path:String,method:String,body:JsonElement?,query:Map<String,String>):JsonElement {
                assertEquals("api/v1/admin/analytics/cards",path)
                return payload("content" to JsonArray(listOf(payload("id" to 2,"totalViews" to 90),payload("id" to 3,"totalViews" to 12,"uniqueViewers" to 5))))
            }
        }
        val article=payload("news" to payload("id" to 3,"title" to "같은 제목"))
        assertEquals(12,articleWithStatistics(api,article).number("totalViews"))
    }
    @Test fun draftRequiresValidSlidesAndSourceAttribution() {
        val image=payload("url" to "https://example.org/image.png","origin" to "SOURCE","sourceUrl" to "https://example.org/article","credit" to "작가 · CC BY 4.0")
        val draft=payload("title" to "AI 이론","summary" to "요약","titleImage" to image,"slides" to JsonArray(listOf(payload("sequence" to 1,"title" to "제목","content" to "본문"))))
        assertNull(draftValidation(draft))
        assertNotNull(draftValidation(JsonObject(draft+payload("slides" to JsonArray(emptyList())))))
        val missingCredit=JsonObject(image+payload("credit" to ""))
        assertNotNull(draftValidation(JsonObject(draft+payload("titleImage" to missingCredit))))
    }
}
