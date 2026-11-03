package org.aiknow.android

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.aiknow.android.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SystemThemeTest {
    @get:Rule val compose=createComposeRule()
    @Test fun followsConfigurationEvenWithLegacyPreference() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("appearance",0).edit().putString("theme","light").commit()
        var night by mutableStateOf(false)
        var observed=Color.Unspecified
        compose.setContent {
            val configuration=Configuration(LocalConfiguration.current).apply {
                uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if(night)Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                fontScale=1.3f
            }
            CompositionLocalProvider(LocalConfiguration provides configuration,LocalDensity provides Density(LocalDensity.current.density,1.3f)) {
                AiKnowTheme {
                    val surface=MaterialTheme.colorScheme.surface
                    SideEffect{observed=surface}
                    Surface { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
                        TodayGreeting("AI 독자")
                        KeyPointPanel(listOf("AI는 여러 기술을 아우르는 말이에요.","모델의 답변은 원문과 출처를 함께 확인해요.","나에게 필요한 기능인지 먼저 살펴보세요."))
                    } }
                }
            }
        }
        compose.runOnIdle{assertTrue(observed.luminance()>.5f);night=true}
        compose.waitForIdle()
        compose.runOnIdle{assertTrue(observed.luminance()<.5f)}
        val file=File(context.getExternalFilesDir(null),"08-system-dark-points.png")
        file.outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        compose.onAllNodesWithContentDescription("핵심 포인트 펼치기").onFirst().performClick()
        compose.onNodeWithContentDescription("핵심 포인트 접기").assertExists()
    }
}
