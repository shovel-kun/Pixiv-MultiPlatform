package top.kagg886.pmf

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import top.kagg886.filepicker.FilePicker

class MainActivity : ComponentActivity() {
    private val flow = MutableSharedFlow<KeyEvent>()
    private val scope = CoroutineScope(Dispatchers.Main)
    private var backgroundCover: View? = null

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        scope.launch {
            flow.emit(KeyEvent(event))
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FilePicker.init(this)
        setContent {
            CompositionLocalProvider(
                LocalKeyStateFlow provides flow,
            ) {
                App()
            }
        }
    }

    override fun onPause() {
        showBackgroundCover()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        hideBackgroundCover()
    }

    private fun showBackgroundCover() {
        if (backgroundCover != null) {
            return
        }

        val cover = View(this).apply {
            setBackgroundColor(resolveWindowBackgroundColor())
        }
        addContentView(
            cover,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        backgroundCover = cover
    }

    private fun hideBackgroundCover() {
        val cover = backgroundCover ?: return
        (cover.parent as? FrameLayout)?.removeView(cover)
        backgroundCover = null
    }

    private fun resolveWindowBackgroundColor(): Int {
        val outValue = TypedValue()
        return if (theme.resolveAttribute(android.R.attr.windowBackground, outValue, true)) {
            (outValue.resourceId.takeIf { it != 0 }?.let { getDrawable(it) } ?: ColorDrawable(outValue.data))
                .let { (it as? ColorDrawable)?.color ?: Color.BLACK }
        } else {
            Color.BLACK
        }
    }
}
