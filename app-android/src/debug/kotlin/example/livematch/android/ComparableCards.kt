package example.livematch.android

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.remote.player.view.RemoteComposePlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import example.livematch.contract.MatchPresentation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant

/*
 * Four implementations of the same Live card, for the comparison the article asks for. Each activity runs in its own
 * process (see the debug manifest) so that memory and CPU are not mixed. They show the same text, tick the same match
 * clock locally, pulse the LIVE label and offer the same two actions.
 */

internal object ComparableCard {
    const val START_SECONDS = 67 * 60 + 12
    const val DETAILS = "Show local match details"
    const val MATCH_ID = "Match ID: live-2027-01"

    fun clockText(seconds: Int) = "2nd Half %d:%02d".format(seconds / 60, seconds % 60)

    /** What an SDUI backend would send: a tree of components the client already knows how to draw. */
    val SDUI_JSON =
        """{"type":"column","children":[{"type":"pulse_text","text":"LIVE"},{"type":"text","text":"Orcas 2"},""" +
            """{"type":"text","text":"Tigers 1"},{"type":"clock","prefix":"2nd Half","startSeconds":$START_SECONDS},""" +
            """{"type":"details","text":"Show local match details","detail":"Match ID: live-2027-01"},""" +
            """{"type":"button","text":"Open match","action":"open_match"},""" +
            """{"type":"button","text":"View highlight","action":"open_highlight"}]}"""

    val HTML =
        """<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><style>""" +
            """@keyframes p{from{opacity:.4}to{opacity:1}}#l{animation:p 1s linear infinite}""" +
            """body{font:16px sans-serif;margin:16px}a{display:block;min-height:48px}</style>""" +
            """<div id="l">LIVE</div><div>Orcas 2</div><div>Tigers 1</div><div id="c"></div>""" +
            """<a href="#" id="d" onclick="document.getElementById('m').style.opacity=1;return false">Show local match details</a>""" +
            """<div id="m" style="opacity:0">Match ID: live-2027-01</div>""" +
            """<a href="#open_match">Open match</a><a href="#open_highlight">View highlight</a>""" +
            """<script>let s=$START_SECONDS;function t(){document.getElementById('c').textContent=""" +
            """'2nd Half '+Math.floor(s/60)+':'+String(s%60).padStart(2,'0')}t();setInterval(function(){s++;t()},1000)</script>"""
}

/** The card as ordinary Compose UI shipped in the app. */
class ComposeLocalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { LocalCard() } } }
    }
}

@Composable
private fun LocalCard() {
    var seconds by remember { mutableIntStateOf(ComparableCard.START_SECONDS) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            seconds += 1
        }
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(16.dp)) {
        PulsingText("LIVE")
        Text("Orcas 2")
        Text("Tigers 1")
        Text(ComparableCard.clockText(seconds))
        LocalDetails(ComparableCard.DETAILS, ComparableCard.MATCH_ID)
        Text("Open match", Modifier.defaultMinSize(minHeight = 48.dp).clickable { })
        Text("View highlight", Modifier.defaultMinSize(minHeight = 48.dp).clickable { })
    }
}

@Composable
private fun LocalDetails(label: String, detail: String) {
    var shown by remember { mutableStateOf(false) }
    Text(label, Modifier.defaultMinSize(minHeight = 48.dp).clickable { shown = true })
    Text(detail, Modifier.alpha(if (shown) 1f else 0f))
}

@Composable
private fun PulsingText(text: String) {
    val alpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_000, easing = LinearEasing), RepeatMode.Restart),
        label = "alpha",
    )
    Text(text, Modifier.alpha(alpha))
}

/** The card as a JSON tree rendered by a small client-side renderer that knows each component type. */
class SduiActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = JSONObject(ComparableCard.SDUI_JSON)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(16.dp)) {
                        val children = root.getJSONArray("children")
                        for (i in 0 until children.length()) SduiNode(children.getJSONObject(i))
                    }
                }
            }
        }
    }
}

@Composable
private fun SduiNode(node: JSONObject) {
    when (node.getString("type")) {
        "text" -> Text(node.getString("text"))
        "pulse_text" -> PulsingText(node.getString("text"))
        "button" -> Text(node.getString("text"), Modifier.defaultMinSize(minHeight = 48.dp).clickable { })
        "details" -> LocalDetails(node.getString("text"), node.getString("detail"))
        "clock" -> {
            var seconds by remember { mutableIntStateOf(node.getInt("startSeconds")) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(1_000)
                    seconds += 1
                }
            }
            Text("${node.getString("prefix")} %d:%02d".format(seconds / 60, seconds % 60))
        }
        else -> Unit
    }
}

/** The card as HTML, CSS and JavaScript in a WebView. */
class WebViewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        setContentView(web)
        web.loadDataWithBaseURL(null, ComparableCard.HTML, "text/html", "utf-8", null)
    }
}

private fun Int.dpPx() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

/** The card as a signed Remote Compose document, validated and captured on the device like the launcher does. */
class RemoteCardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val player = RemoteComposePlayer(this)
        player.setBackgroundColor(android.graphics.Color.WHITE)
        setContentView(player)
        ViewCompat.setOnApplyWindowInsetsListener(player) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left + 16.dpPx(), bars.top + 16.dpPx(), bars.right, bars.bottom)
            insets
        }
        lifecycleScope.launch {
            val envelope = assets.open("live-match.rce").use { it.readBytes() }
            val loaded = EnvelopeMatchLoader().load(envelope, Instant.now().epochSecond) as MatchEnvelopeLoadResult.Loaded
            val live = (loaded.presentation as MatchPresentation.Live).match
            val document = AndroidMatchDocumentCompiler(this@RemoteCardActivity).compile(live)
            val deflated = java.io.ByteArrayOutputStream().also { out -> java.util.zip.DeflaterOutputStream(out).use { it.write(document.bytes) } }
            android.util.Log.i("CardSize", "envelope=${envelope.size} document=${document.bytes.size} documentDeflate=${deflated.size()}")
            RemoteComposePlayerAdapter().load(player, document)
        }
    }
}
