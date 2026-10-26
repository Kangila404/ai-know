package org.aiknow.android
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import android.graphics.Bitmap
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.aiknow.android.data.flag
import org.aiknow.android.data.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

/** Run against demoDebug only: the test never signs into or edits the production service. */
class NavigationTest {
    @get:Rule(order=0) val firstLaunch = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            check(context.packageName == "org.aiknow.android.demo")
            context.getSharedPreferences("intro",0).edit().clear().commit()
        }
    }
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private fun waitFor(text:String) {
        compose.waitUntil(15_000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        compose.waitUntil(15_000) { compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).fetchSemanticsNodes().isEmpty() }
        compose.onAllNodesWithContentDescription("이미지를 불러오지 못했습니다").assertCountEquals(0)
        val file = File(compose.activity.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun readerAndEditorialJourneys() {
        try {
        check(Platform.demo)
        waitFor("맞아요, 너무 많아요")
        screenshot("00-intro-first")
        compose.onNodeWithText("맞아요, 너무 많아요").performClick()
        compose.waitUntil(5_000){compose.onAllNodes(hasText("어떻게 읽나요?") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        screenshot("00-intro-conversation")
        listOf("어떻게 읽나요?","좋아요, 그다음은요?","함께 시작해 볼게요","AI KNOW 시작하기").forEach { response ->
            compose.waitUntil(5_000){compose.onAllNodes(hasText(response) and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText(response).performClick()
        }
        waitFor("데모 둘러보기")
        compose.onNodeWithText("소개 다시 보기").performClick()
        waitFor("맞아요, 너무 많아요");compose.onNodeWithText("건너뛰기").performClick()
        compose.onNodeWithText("데모 둘러보기").performClick()
        waitFor("어떻게 불러드릴까요?")
        compose.onNodeWithContentDescription("온보딩 닉네임").performTextReplacement("안드로이드 독자")
        screenshot("00-onboarding-name")
        compose.onNodeWithText("다음",substring=false).performScrollTo().performClick()
        waitFor("언제 만나면 좋을까요?")
        compose.onNodeWithText("저녁 21:00").performScrollTo().performClick()
        screenshot("00-onboarding-notifications")
        compose.onNodeWithText("알림 없이 시작하기").performScrollTo().performClick()
        waitFor("오늘의 AI")
        runBlocking {
            val api=(compose.activity.application as AiKnowApplication).api
            assertEquals("안드로이드 독자",api.request("api/v1/users").text("nickname"))
            assertEquals("21:00:00",api.request("api/v1/notification-setting").text("settingTime"))
            assertFalse(api.request("api/v1/notification-setting").flag("isAllowed"))
        }
        screenshot("01-today")
        compose.onNodeWithText("오늘의 핵심 포인트").performScrollTo()
        screenshot("01-today-points")
        compose.onNodeWithText("아카이브",useUnmergedTree=true).performClick()
        waitFor("[AI 이론] AI는 하나의 기술 이름이 아니다")
        screenshot("02-archive")
        compose.onNodeWithText("[AI 이론] AI는 하나의 기술 이름이 아니다").performClick()
        waitFor("왼쪽으로 밀어 다음 이야기")
        compose.onNodeWithText("다음 카드").assertDoesNotExist()
        compose.onNodeWithText("다 읽었어요").assertDoesNotExist()
        screenshot("03-reader")
        compose.onNodeWithContentDescription("출처 및 이미지 정보").performClick()
        waitFor("표지 이미지");compose.onNodeWithText("표지 이미지").assertExists()
        compose.onNodeWithText("닫기").performScrollTo().performClick()
        compose.onNodeWithContentDescription("이미지 확대").performClick()
        waitFor("원래 크기");compose.onNodeWithText("원래 크기").assertExists()
        compose.onNode(hasContentDescription("뒤로") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithContentDescription("좋아요").performClick()
        repeat(4){index->compose.onNodeWithTag("card-pager").performTouchInput{swipeLeft()};compose.waitForIdle();waitFor("${index+2} / 5")}
        compose.waitUntil(10_000){runBlocking{(compose.activity.application as AiKnowApplication).api.request("api/v1/cardNews/1").flag("read")}}
        waitFor("읽음으로 저장했어요")
        screenshot("03-reader-completed")
        compose.onNodeWithContentDescription("뒤로").performClick()
        waitFor("읽음");compose.onNodeWithText("읽음").assertExists()
        waitFor("마이");compose.onNodeWithText("마이",useUnmergedTree=true).performClick()
        waitFor("알림 설정");compose.onNodeWithText("화면 모드").assertDoesNotExist()
        screenshot("04-profile")
        compose.onNodeWithText("관리자",useUnmergedTree=true).performClick()
        waitFor("콘텐츠 검수");compose.onNodeWithText("콘텐츠 검수").performClick()
        waitFor("[AI 이론] AI는 하나의 기술 이름이 아니다")
        compose.onNodeWithText("[AI 이론] AI는 하나의 기술 이름이 아니다").performClick()
        waitFor("내용 수정");compose.onNodeWithText("내용 수정").performScrollTo().performClick()
        compose.onAllNodesWithText("제목",substring=false).onFirst().assertExists()
        compose.onNodeWithText("슬라이드 추가").performScrollTo().performClick()
        compose.onNodeWithText("슬라이드 6 삭제").performScrollTo().performClick()
        screenshot("05-editor")
        compose.onNodeWithText("편집 취소").performScrollTo().performClick()
        compose.onNodeWithText("승인",substring=false).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("AI 이론",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("승인",substring=false).performScrollTo().performClick()
        waitFor("승인할까요?");compose.onNodeWithText("확인").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("반려",substring=false).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("승인",substring=false).assertExists()
        screenshot("06-approved")
        compose.onNodeWithText("승인된 카드 관리").performScrollTo().performClick()
        waitFor("조회 42회 · 고유 9");compose.onNodeWithText("아직 발송을 시작하지 않았습니다.").assertExists()
        screenshot("07-article-metadata")
        } catch(error:Throwable) {
            android.util.Log.e("AiKnowJourney", "Reader/editorial journey failed", error)
            throw error
        }
    }
}
