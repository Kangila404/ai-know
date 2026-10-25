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

/** Run against demoDebug only: the test never signs into or edits the production service. */
class NavigationTest {
    @get:Rule(order=0) val appearance = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            check(context.packageName == "org.aiknow.android.demo")
            context.getSharedPreferences("appearance", 0).edit().putString("theme", "light").commit()
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
        check(Platform.demo)
        waitFor("데모 둘러보기")
        compose.onNodeWithText("데모 둘러보기").performClick()
        waitFor("시작하기");compose.onNodeWithText("시작하기").performScrollTo().performClick()
        waitFor("오늘의 AI")
        screenshot("01-today")
        compose.onNodeWithText("아카이브",useUnmergedTree=true).performClick()
        waitFor("[AI 이론] AI는 하나의 기술 이름이 아니다")
        screenshot("02-archive")
        compose.onNodeWithText("[AI 이론] AI는 하나의 기술 이름이 아니다").performClick()
        waitFor("다음 카드")
        screenshot("03-reader")
        compose.onNodeWithContentDescription("좋아요").performClick()
        repeat(4){compose.onNodeWithText("다음 카드").performClick();compose.waitForIdle()}
        waitFor("다 읽었어요");compose.onNodeWithText("다 읽었어요").performClick()
        waitFor("마이");compose.onNodeWithText("마이",useUnmergedTree=true).performClick()
        waitFor("화면 모드");compose.onNodeWithText("화면 모드").performClick()
        waitFor("다크");compose.onAllNodesWithText("다크").onLast().performClick()
        screenshot("04-profile-dark")
        compose.onNodeWithText("관리자",useUnmergedTree=true).performClick()
        waitFor("콘텐츠 검수");compose.onNodeWithText("콘텐츠 검수").performClick()
        waitFor("[AI 이론] AI는 하나의 기술 이름이 아니다")
        compose.onNodeWithText("[AI 이론] AI는 하나의 기술 이름이 아니다").performClick()
        waitFor("내용 수정");compose.onNodeWithText("내용 수정").performScrollTo().performClick()
        compose.onAllNodesWithText("제목",substring=false).onFirst().assertExists()
        screenshot("05-editor-dark")
        compose.onNodeWithText("편집 취소").performScrollTo().performClick()
        compose.onNodeWithText("승인",substring=false).performScrollTo().performClick()
        waitFor("승인할까요?");compose.onNodeWithText("확인").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("반려",substring=false).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("승인",substring=false).assertExists()
        screenshot("06-approved-dark")
    }
}
