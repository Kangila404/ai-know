package org.aiknow.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.aiknow.android.data.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource

/** Isolated demo data. No production role changes, credentials, or real FCM sends. */
class AdminParityTest {
    private lateinit var api: RoleGateway
    private class RoleGateway(val delegate: DemoGateway) : Gateway by delegate {
        var role="ADMIN"
        var failUser=false
        var userGate: CompletableDeferred<Unit>?=null
        var userLookupStarted=false
        val alertPages=java.util.concurrent.CopyOnWriteArrayList<String>()
        override suspend fun request(path:String,method:String,body:JsonElement?,query:Map<String,String>):JsonElement {
            if(path=="api/v1/users" && failUser) throw ApiException(503)
            if(path.startsWith("api/v1/admin/") && role!="ADMIN") throw ApiException(403)
            if(path=="api/v1/admin/alerts") alertPages+=query.getValue("page")
            val result=delegate.request(path,method,body,query)
            if(path=="api/v1/users") userGate?.let { gate -> userLookupStarted=true; withContext(NonCancellable) { gate.await() } }
            return if(path=="api/v1/users") JsonObject(result.obj()+("userRole" to JsonPrimitive(role))) else result
        }
    }
    @get:Rule(order=0) val readyAccount=object: ExternalResource() {
        override fun before() {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            check(context.packageName=="org.aiknow.android.demo")
            api=RoleGateway(DemoGateway(context))
            runBlocking { api.request("api/v1/auth/demo","POST"); api.request("api/v1/users/onboarding","PATCH") }
            (context.applicationContext as AiKnowApplication).api=api
            context.getSharedPreferences("intro",0).edit().putBoolean("seen",true).commit()
        }
    }
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private fun waitFor(text:String) { compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun push() { compose.runOnIdle { ViewModelProvider(compose.activity)[AppModel::class.java].acceptPush("3",null) } }
    @Test fun pagesAndDatesSurviveOpeningDetail() {
        waitFor("관리자"); push(); waitFor("새 초안이 도착했어요")
        compose.onNodeWithText("대상 날짜 2026-11-04").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("이전 알림 더 보기").performScrollTo().performClick()
        compose.waitUntil(15_000) { api.alertPages.size==2 }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("자동화 실행을 확인해 주세요"))
        compose.onNodeWithText("자동화 실행을 확인해 주세요").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("콘텐츠 검수 열기"))
        compose.onNodeWithText("콘텐츠 검수 열기").performClick()
        waitFor("내용 수정")
        compose.onNodeWithContentDescription("뒤로").performClick()
        waitFor("자동화·발송 알림")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("자동화 실행을 확인해 주세요"))
        compose.onNodeWithText("자동화 실행을 확인해 주세요").assertIsDisplayed()
        assertEquals(listOf("0","1"),api.alertPages.toList())
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(compose.activity.getExternalFilesDir(null),"08-admin-alerts.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
    @Test fun cachedAdminRoleCannotOpenAlertAfterDowngrade() {
        waitFor("관리자")
        compose.runOnIdle { api.role="USER" }
        push(); waitFor("관리자 계정으로만 볼 수 있는 알림입니다.")
        assertTrue(api.alertPages.isEmpty())
        compose.onNodeWithText("확인").performClick()
        compose.onNodeWithText("관리자").assertDoesNotExist()
    }
    @Test fun permissionCheckNetworkFailureDoesNotNavigate() {
        waitFor("관리자")
        compose.runOnIdle { api.failUser=true }
        push(); waitFor("관리자 권한을 확인하지 못했습니다. 연결 상태를 확인하고 다시 시도해 주세요.")
        assertTrue(api.alertPages.isEmpty())
    }
    @Test fun delayedPermissionResponseCannotRestoreLoggedOutAccount() {
        waitFor("관리자")
        val gate=CompletableDeferred<Unit>()
        compose.runOnIdle { api.userGate=gate }
        push()
        compose.waitUntil(15_000) { api.userLookupStarted }
        compose.runOnIdle { api.onUnauthorized() }
        waitFor("데모 둘러보기")
        compose.runOnIdle { gate.complete(Unit) }
        compose.waitForIdle()
        compose.onNodeWithText("데모 둘러보기").assertIsDisplayed()
        assertTrue(api.alertPages.isEmpty())
        compose.runOnIdle { assertNull(ViewModelProvider(compose.activity)[AppModel::class.java].user) }
    }
}
