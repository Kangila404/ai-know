@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package org.aiknow.android.ui

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.aiknow.android.data.*
import org.junit.*
import org.junit.Assert.*

class AdminAlertsModelTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private class Fake : Gateway {
        override var onUnauthorized: () -> Unit = {}
        override fun clearSession() {}
        val pages=mutableListOf<Int>()
        var response: suspend (Int) -> JsonElement={page -> payload("content" to JsonArray(listOf(payload("id" to page+1))),"last" to false)}
        override suspend fun request(path:String,method:String,body:JsonElement?,query:Map<String,String>):JsonElement {
            assertEquals("api/v1/admin/alerts",path)
            val page=query.getValue("page").toInt(); pages+=page
            return response(page)
        }
    }
    @Test fun appendDeduplicatesAndFailedRefreshRetriesFirstPage()=runTest(dispatcher) {
        val api=Fake(); val model=AdminAlertsModel()
        api.response={page->payload("content" to JsonArray((1..page+1).map{payload("id" to it)}),"last" to false)}
        model.load(api,isCurrent={true},forbidden={}); advanceUntilIdle()
        model.load(api,isCurrent={true},forbidden={}); advanceUntilIdle()
        assertEquals(listOf(1L,2L),model.rows.map{it.number("id")})
        api.response={throw ApiException(503)}
        model.load(api,true,{true},{}); advanceUntilIdle()
        assertNotNull(model.error); assertTrue(model.retryFromStart)
        api.response={payload("content" to JsonArray(listOf(payload("id" to 9))),"last" to true)}
        model.load(api,model.retryFromStart,{true},{}); advanceUntilIdle()
        assertEquals(listOf(0,1,0,0),api.pages)
        assertEquals(9L,model.rows.single().number("id")); assertTrue(model.last)
    }
    @Test fun lateCancelledResponseCannotReplaceRefresh()=runTest(dispatcher) {
        val api=Fake(); val model=AdminAlertsModel(); val gate=CompletableDeferred<Unit>()
        api.response={withContext(NonCancellable){gate.await()};payload("content" to JsonArray(listOf(payload("id" to 1))),"last" to true)}
        model.load(api,isCurrent={true},forbidden={}); runCurrent()
        api.response={payload("content" to JsonArray(listOf(payload("id" to 2))),"last" to true)}
        model.load(api,true,{true},{}); runCurrent()
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(2L,model.rows.single().number("id"))
    }
    @Test fun roleLossDiscardsResponseAndForbiddenClearsPrivateRows()=runTest(dispatcher) {
        val api=Fake(); val model=AdminAlertsModel(); var current=true; var rechecked=false
        api.response={current=false;payload("content" to JsonArray(listOf(payload("id" to 1))))}
        model.load(api,isCurrent={current},forbidden={}); advanceUntilIdle()
        assertTrue(model.rows.isEmpty())
        current=true
        api.response={payload("content" to JsonArray(listOf(payload("id" to 1))),"last" to false)}
        model.load(api,true,{current},{}); advanceUntilIdle(); assertEquals(1,model.rows.size)
        api.response={throw ApiException(403)}
        model.load(api,true,{current},{rechecked=true}); advanceUntilIdle()
        assertTrue(model.rows.isEmpty()); assertTrue(rechecked)
    }
}
