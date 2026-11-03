package org.aiknow.android

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.aiknow.android.ui.SocialLoginButton
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SocialLoginLayoutTest {
    @get:Rule val compose=createComposeRule()
    @Test fun officialIconsAndCenteredLabelsInBothThemes() {
        val dark=mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme=if(dark.value) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                        Column(Modifier.widthIn(max=360.dp).fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            SocialLoginButton("GOOGLE",true) {}
                            SocialLoginButton("KAKAO",true) {}
                        }
                    }
                }
            }
        }
        for(theme in listOf("light","dark")) {
            compose.runOnIdle { dark.value=theme=="dark" }
            compose.waitForIdle()
            val google=compose.onNode(hasClickAction() and hasText("Google로 계속하기")).fetchSemanticsNode().boundsInRoot
            val kakao=compose.onNode(hasClickAction() and hasText("카카오로 계속하기")).fetchSemanticsNode().boundsInRoot
            assertEquals(google.left,kakao.left,1f); assertEquals(google.right,kakao.right,1f)
            assertEquals(google.height,kakao.height,1f)
            for(title in listOf("Google로 계속하기","카카오로 계속하기")) {
                val label=compose.onNodeWithText(title,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                assertEquals(google.center.x,label.center.x,1f)
            }
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            File(context.getExternalFilesDir(null),"09-social-$theme.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
            }
        }
    }
}
