package org.aiknow.android.ui
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.aiknow.android.data.payload

/** Standalone design preview. Contains no account, networking, or production write path. */
@Preview(name="오늘 · 라이트",showBackground=true,showSystemUi=true,locale="ko")
@Preview(name="오늘 · 다크",showBackground=true,showSystemUi=true,locale="ko",uiMode=Configuration.UI_MODE_NIGHT_YES)
@Preview(name="큰 글씨",showBackground=true,locale="ko",fontScale=1.5f)
@Composable fun TodayPreview() {
    AiKnowTheme {
        Scaffold(bottomBar={NavigationBar{NavigationBarItem(true,{},icon={Icon(Icons.Default.AutoAwesome,null)},label={Text("오늘")});NavigationBarItem(false,{},icon={Icon(Icons.Default.CollectionsBookmark,null)},label={Text("아카이브")});NavigationBarItem(false,{},icon={Icon(Icons.Default.Person,null)},label={Text("마이")})}}) { padding->
            Page("오늘의 AI") { top->Column(Modifier.padding(top).padding(bottom=padding.calculateBottomPadding()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                TodayGreeting("AI 독자")
                KeyPointPanel(listOf("AI는 여러 기술을 아우르는 말이에요.","답변의 근거와 원문을 함께 확인해요.","내가 쓰려는 목적에 맞는지 살펴보세요."))
            } }
        }
    }
}
