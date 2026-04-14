package com.keenetic.guest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                GuestScreen()
            }
        }
    }
}

val client = OkHttpClient.Builder()
    .connectTimeout(6, TimeUnit.SECONDS)
    .readTimeout(6, TimeUnit.SECONDS)
    .cookieJar(JavaNetCookieJar(java.net.CookieManager()))
    .build()

val JSON_MT = "application/json".toMediaType()
const val BASE = "http://192.168.2.1"
const val USER = "admin"
const val PASS = "a4lllvl966a1982xi"
const val I0   = "WifiMaster0/AccessPoint1"
const val I1   = "WifiMaster1/AccessPoint1"

fun md5(s: String): String {
    val b = MessageDigest.getInstance("MD5").digest(s.toByteArray())
    return b.joinToString("") { "%02x".format(it) }
}

fun sha256(s: String): String {
    val b = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
    return b.joinToString("") { "%02x".format(it) }
}

suspend fun auth() = withContext(Dispatchers.IO) {
    val r1 = client.newCall(
        Request.Builder().url("$BASE/auth").get().build()
    ).execute()
    if (r1.code == 200) { r1.close(); return@withContext }
    val realm = r1.headers["X-NDM-Realm"] ?: run { r1.close(); return@withContext }
    val challenge = r1.headers["X-NDM-Challenge"] ?: run { r1.close(); return@withContext }
    r1.close()
    val hash = sha256(challenge + md5("$USER:$realm:$PASS"))
    val body = """{"login":"$USER","password":"$hash"}""".toRequestBody(JSON_MT)
    val r2 = client.newCall(
        Request.Builder().url("$BASE/auth").post(body).build()
    ).execute()
    r2.close()
}

suspend fun getStatus(): Boolean? = withContext(Dispatchers.IO) {
    try {
        auth()
        val r = client.newCall(
            Request.Builder()
                .url("$BASE/rci/show/interface/$I0")
                .get().build()
        ).execute()
        val body = r.body?.string() ?: return@withContext null
        r.close()
        val j = JSONObject(body)
        j.optString("state") == "up" || j.optBoolean("up")
    } catch (e: Exception) { null }
}

suspend fun setEnabled(on: Boolean) = withContext(Dispatchers.IO) {
    try {
        auth()
        val body = """{"interface":{"$I0":{"up":$on},"$I1":{"up":$on}}}"""
            .toRequestBody(JSON_MT)
        val r = client.newCall(
            Request.Builder().url("$BASE/rci/").post(body).build()
        ).execute()
        r.close()
    } catch (e: Exception) { throw e }
}

@Composable
fun GuestScreen() {
    var state by remember { mutableStateOf<Boolean?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val bg by animateColorAsState(
        when (state) {
            true  -> Color(0xFF0d2b1d)
            false -> Color(0xFF1a0d0d)
            else  -> Color(0xFF0d1117)
        },
        animationSpec = tween(600), label = ""
    )

    LaunchedEffect(Unit) {
        try { state = getStatus() } catch (e: Exception) { error = e.message ?: "Ошибка" }
        loading = false
    }

    Box(
        Modifier.fillMaxSize().background(bg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text("Гостевая сеть", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("Keenetic Extra KN-1714", fontSize = 13.sp, color = Color.White.copy(0.4f))

            val (stText, stColor) = when {
                loading      -> "Загрузка…"   to Color(0xFFFFD600)
                state == true  -> "● ВКЛЮЧЕНА"  to Color(0xFF00E676)
                state == false -> "● ВЫКЛЮЧЕНА" to Color(0xFFFF5252)
                else         -> "● НЕИЗВЕСТНО" to Color(0xFF90A4AE)
            }
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(stColor.copy(0.15f))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(stText, color = stColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            if (loading) {
                CircularProgressIndicator(color = Color(0xFF00E676), modifier = Modifier.size(80.dp))
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            loading = true; error = ""
                            try {
                                setEnabled(!(state ?: false))
                                delay(1200)
                                state = getStatus()
                            } catch (e: Exception) {
                                error = e.message ?: "Ошибка"
                            }
                            loading = false
                        }
                    },
                    modifier = Modifier.size(160.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state == true) Color(0xFF00C853) else Color(0xFFD32F2F)
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (state == true) "ВЫКЛ" else "ВКЛ",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            if (state == true) "нажмите\nчтобы выключить" else "нажмите\nчтобы включить",
                            fontSize = 10.sp,
                            color = Color.White.copy(0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (error.isNotEmpty()) {
                Text(error, color = Color(0xFFFF5252), textAlign = TextAlign.Center, fontSize = 13.sp)
            }

            TextButton(onClick = {
                scope.launch {
                    loading = true; error = ""
                    try { state = getStatus() } catch (e: Exception) { error = e.message ?: "Ошибка" }
                    loading = false
                }
            }) {
                Text("Обновить статус", color = Color.White.copy(0.4f))
            }
        }
    }
}
