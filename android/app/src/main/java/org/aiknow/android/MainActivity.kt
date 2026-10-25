package org.aiknow.android
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import org.aiknow.android.ui.AiKnowApp
class MainActivity : ComponentActivity() {
    private val model: AppModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        if(savedInstanceState == null) accept(intent)
        setContent { AiKnowApp(model) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); accept(intent) }
    override fun onResume() { super.onResume(); model.refreshAccount() }
    private fun accept(intent: Intent) { intent.getStringExtra("cardNewsId")?.toLongOrNull()?.takeIf { it > 0 }?.let { model.pendingCard=it } }
}
