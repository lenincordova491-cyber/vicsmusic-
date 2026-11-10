package app.vicsmusic.feature.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val YTM_LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true&continue=https%3A%2F%2Fmusic.youtube.com%2F"
private const val YTM_BASE_URL = "https://music.youtube.com"
private const val YT_BASE_URL = "https://www.youtube.com"

// Resuelve el User-Agent legítimo del dispositivo evitando WebView headers que causan 403
// y evitando modelos falsos como "Pixel 8" que provocan alertas de seguridad de Google.
internal fun resolveLegitimateUserAgent(context: Context): String {
    val defaultUa = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
    if (!defaultUa.isNullOrBlank()) {
        val cleaned = defaultUa
            .replace("; wv", "")
            .replace(Regex("Version/\\d+(\\.\\d+)*\\s*"), "")
        if (cleaned.contains("Chrome", ignoreCase = true)) {
            return cleaned
        }
    }

    val androidVersion = Build.VERSION.RELEASE.takeIf { !it.isNullOrBlank() } ?: "14"
    val model = Build.MODEL.takeIf { !it.isNullOrBlank() } ?: "Android Device"
    val buildId = Build.ID.takeIf { !it.isNullOrBlank() } ?: "UQ1A.240205.004"
    return "Mozilla/5.0 (Linux; Android $androidVersion; $model Build/$buildId) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginDialog(
    onLoginSuccess: (cookieHeader: String, visitorData: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var loadProgress by remember { mutableIntStateOf(10) }
    var hasCompleted by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun hasAuthCookies(cookieStr: String): Boolean {
        return cookieStr.contains("SAPISID=") ||
            cookieStr.contains("__Secure-3PAPISID=") ||
            cookieStr.contains("__Secure-1PAPISID=") ||
            cookieStr.contains("PAPISID=") ||
            cookieStr.contains("__Secure-3PSID=") ||
            cookieStr.contains("SID=") ||
            cookieStr.contains("LOGIN_INFO=")
    }

    fun tryCaptureSession(currentUrl: String?, forceCapture: Boolean = false): Boolean {
        if (hasCompleted) return true
        val cookieManager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return false
        val ytmCookies = runCatching { cookieManager.getCookie(YTM_BASE_URL) }.getOrNull().orEmpty()
        val ytCookies = runCatching { cookieManager.getCookie(YT_BASE_URL) }.getOrNull().orEmpty()

        val combinedCookieMap = linkedMapOf<String, String>()
        listOf(ytCookies, ytmCookies).forEach { raw ->
            raw.split(';').forEach { pair ->
                val trimmed = pair.trim()
                val eq = trimmed.indexOf('=')
                if (eq > 0) {
                    combinedCookieMap[trimmed.substring(0, eq).trim()] = trimmed.substring(eq + 1).trim()
                }
            }
        }
        val mergedCookies = combinedCookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }

        val isOnYouTubeDomain = currentUrl?.contains("music.youtube.com", ignoreCase = true) == true ||
            currentUrl?.contains("youtube.com", ignoreCase = true) == true

        if (hasAuthCookies(mergedCookies) && (isOnYouTubeDomain || forceCapture)) {
            hasCompleted = true
            runCatching { cookieManager.flush() }
            val visitorData = combinedCookieMap["VISITOR_INFO1_LIVE"]
            runCatching {
                context.getSharedPreferences("vicsmusic_ytm_session", Context.MODE_PRIVATE)
                    .edit()
                    .putString("cookie_header", mergedCookies)
                    .putString("visitor_data", visitorData)
                    .putBoolean("is_logged_in", true)
                    .commit()
            }
            onLoginSuccess(mergedCookies, visitorData)
            return true
        }
        return false
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                webViewRef?.stopLoading()
                webViewRef?.destroy()
                webViewRef = null
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF121212)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E))
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = {
                                if (!tryCaptureSession(webViewRef?.url, forceCapture = true)) {
                                    onDismiss()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Color.White
                            )
                        }
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(
                                text = "Conectar YouTube Music",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Inicia sesión para sincronizar tus gustos y mixes",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB3B3B3),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (!tryCaptureSession(webViewRef?.url, forceCapture = true)) {
                                webViewRef?.loadUrl(YTM_BASE_URL)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1DB954),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "Listo",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (loadProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = Color(0xFF1DB954),
                        trackColor = Color(0xFF2A2A2A)
                    )
                }

                // WebView Container
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                webViewRef = this

                                runCatching {
                                    val cm = CookieManager.getInstance()
                                    cm.setAcceptCookie(true)
                                    cm.setAcceptThirdPartyCookies(this, true)
                                }

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    loadsImagesAutomatically = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    userAgentString = resolveLegitimateUserAgent(ctx)
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        tryCaptureSession(url)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        tryCaptureSession(url)
                                    }

                                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                        super.doUpdateVisitedHistory(view, url, isReload)
                                        tryCaptureSession(url)
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val targetUrl = request?.url?.toString() ?: return false
                                        if (targetUrl.startsWith("intent://") || targetUrl.startsWith("vnd.youtube")) {
                                            view?.loadUrl(YTM_BASE_URL)
                                            return true
                                        }
                                        return false
                                    }
                                }

                                loadUrl(YTM_LOGIN_URL)
                            }
                        }
                    )
                }
            }
        }
    }
}
