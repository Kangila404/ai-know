package org.aiknow.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import kotlinx.coroutines.runBlocking
import org.aiknow.android.data.*
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File

/** Guest gates must prevent requests, not merely hide errors returned by the server. */
class GuestJourneyTest {
    @get:Rule(order=0) val guestSession=object:ExternalResource(){
        override fun before(){
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            check(context.packageName=="org.aiknow.android.demo")
            context.getSharedPreferences("intro",0).edit().putBoolean("seen",true).commit()
            (context.applicationContext as AiKnowApplication).api=DemoGateway(context)
        }
    }
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private fun waitFor(text:String){compose.waitUntil(15_000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun cancelGate(){waitFor("로그인이 필요한 기능이에요");compose.onNodeWithText("계속 둘러보기").performClick()}
    private fun screenshot(name:String){
        compose.waitForIdle()
        File(compose.activity.getExternalFilesDir(null),"$name.png").outputStream().use{
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }

    @Test fun guestCanReadButPersonalActionsRequireExplicitLogin(){
        waitFor("로그인 없이 둘러보기")
        compose.onNodeWithText("로그인 없이 둘러보기").performScrollTo().performClick()
        waitFor("둘러보는 중")
        val gateway=(compose.activity.application as AiKnowApplication).api as DemoGateway
        gateway.requests.clear()
        compose.onNodeWithText("관리자",useUnmergedTree=true).assertDoesNotExist()
        compose.onNodeWithContentDescription("알림 설정").performClick();cancelGate()
        screenshot("09-guest-home")
        compose.onNodeWithText("마이",useUnmergedTree=true).performClick();cancelGate()
        compose.onNodeWithText("공지사항").performClick()
        waitFor("AI KNOW에 오신 것을 환영해요")
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("아카이브",useUnmergedTree=true).performClick()
        waitFor("[AI 이론] AI는 하나의 기술 이름이 아니다")
        compose.onNodeWithText("좋아요").performClick();cancelGate()
        compose.onNodeWithText("[AI 이론] AI는 하나의 기술 이름이 아니다").performClick()
        waitFor("왼쪽으로 밀어 다음 이야기")
        compose.onNodeWithContentDescription("좋아요").performClick();cancelGate()
        compose.onNodeWithContentDescription("출처 및 이미지 정보").performClick()
        waitFor("표지 이미지");compose.onNodeWithText("닫기").performScrollTo().performClick()
        repeat(4){i->compose.onNodeWithTag("card-pager").performTouchInput{swipeLeft()};waitFor("${i+2} / 5")}
        // Leave enough real time for the member-only auto-read timer to fire if the gate regresses.
        Thread.sleep(2400)
        compose.onNodeWithText("마지막 카드예요").assertExists()
        screenshot("09-guest-reader")
        compose.onNodeWithText("로그인이 필요한 기능이에요").assertDoesNotExist()
        assertTrue(gateway.requests.all{it.startsWith("GET ") && (it.contains("api/v1/cardNews")||it.contains("api/v1/notices"))})
        assertFalse(runBlocking{gateway.request("api/v1/cardNews/1").flag("read")})

        compose.onNodeWithContentDescription("좋아요").performClick()
        waitFor("로그인이 필요한 기능이에요");compose.onNodeWithText("로그인").performClick()
        waitFor("데모 둘러보기");compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("5 / 5").assertExists()
        // Signing in switches to member onboarding/home and creates a new personal UI state.
        compose.onNodeWithContentDescription("좋아요").performClick()
        waitFor("로그인이 필요한 기능이에요");compose.onNodeWithText("로그인").performClick()
        waitFor("데모 둘러보기");compose.onNodeWithText("데모 둘러보기").performClick()
        compose.waitUntil(15_000){compose.onAllNodesWithText("어떻게 불러드릴까요?").fetchSemanticsNodes().isNotEmpty()||compose.onAllNodesWithText("오늘의 AI").fetchSemanticsNodes().isNotEmpty()}
        if(compose.onAllNodesWithText("어떻게 불러드릴까요?").fetchSemanticsNodes().isNotEmpty()){
            compose.onNodeWithContentDescription("온보딩 닉네임").performTextReplacement("비회원에서 가입")
            compose.onNodeWithText("다음").performScrollTo().performClick()
            waitFor("언제 만나면 좋을까요?");compose.onNodeWithText("알림 없이 시작하기").performScrollTo().performClick()
        }
        waitFor("오늘의 AI");compose.onNodeWithText("둘러보는 중").assertDoesNotExist()
        compose.onNodeWithText("관리자",useUnmergedTree=true).assertExists()
    }
}
