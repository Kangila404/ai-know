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
    AiKnowTheme("system") {
        Scaffold(bottomBar={NavigationBar{NavigationBarItem(true,{},icon={Icon(Icons.Default.AutoAwesome,null)},label={Text("오늘")});NavigationBarItem(false,{},icon={Icon(Icons.Default.CollectionsBookmark,null)},label={Text("아카이브")});NavigationBarItem(false,{},icon={Icon(Icons.Default.Person,null)},label={Text("마이")})}}) { padding->
            Page("오늘의 AI") { top->Column(Modifier.padding(top).padding(bottom=padding.calculateBottomPadding()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Text("AI를 알아가는 작은 습관",color=MaterialTheme.colorScheme.primary)
                NewsTile(payload("title" to "AI는 하나의 기술 이름이 아니다","summary" to "머신러닝과 생성형 AI의 관계를 알면 제품 설명을 더 정확히 읽을 수 있어요."),false){}
            } }
        }
    }
}
