package org.aiknow.android.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.*
import org.junit.Test

class OnboardingTest {
    private class RecordingGateway(var failSettings:Boolean=false):Gateway {
        val calls=mutableListOf<Pair<String,JsonElement?>>()
        override var onUnauthorized:()->Unit={}
        override fun clearSession(){}
        override suspend fun request(path:String,method:String,body:JsonElement?,query:Map<String,String>):JsonElement {
            calls+=path to body
            if(path.endsWith("notification-setting")) {
                assertEquals("PUT",method)
                if(failSettings)throw ApiException(503)
            } else assertEquals("PATCH",method)
            return payload("onboardingCompleted" to path.endsWith("onboarding"))
        }
    }
    @Test fun savesOptOutAndSelectedTimeBeforeCompletion()=runBlocking {
        val api=RecordingGateway()
        assertTrue(completeOnboarding(api,"21:00:00",false).flag("onboardingCompleted"))
        assertEquals(listOf("api/v1/notification-setting","api/v1/users/onboarding"),api.calls.map{it.first})
        assertEquals("21:00:00",api.calls.first().second!!.text("settingTime"))
        assertFalse(api.calls.first().second!!.flag("isAllowed"))
    }
    @Test fun failedPreferenceSaveDoesNotCompleteAndCanBeRetried()=runBlocking {
        val api=RecordingGateway(true)
        try{completeOnboarding(api,"08:00:00",true);fail("Expected failed save")}catch(_:ApiException){}
        assertEquals(1,api.calls.size)
        api.failSettings=false
        assertTrue(completeOnboarding(api,"08:00:00",true).flag("onboardingCompleted"))
        assertTrue(api.calls[1].second!!.flag("isAllowed"))
    }
    @Test fun malformedTimeNeverReachesServer()=runBlocking {
        val api=RecordingGateway()
        for(time in listOf("24:00:00","12:60:00","abc")) {
            try{completeOnboarding(api,time,true);fail("Expected invalid time")}catch(_:IllegalArgumentException){}
        }
        assertTrue(api.calls.isEmpty())
    }
}
