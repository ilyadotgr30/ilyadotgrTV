package ru.ilyadotgr.tvhome

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.StatFs
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import kotlin.math.roundToInt

private val SnappySpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 850f)
internal val LocalDarkTheme = staticCompositionLocalOf { true }
private val LocalPrefsRevision = staticCompositionLocalOf { 0 }

private enum class Screen { HOME, APPS, WEATHER, SYSTEM, YANDEX, DOCK, INPUTS }

private data class TvApp(
    val label: String,
    val component: ComponentName,
    val icon: Drawable,
    val system: Boolean,
    val yandex: Boolean,
)

private data class CityLocation(val name: String, val latitude: Double, val longitude: Double)
private data class ForecastSlot(val hour: Int, val temperature: Int, val condition: String,
                                val humidity: Int = 0, val wind: Int = 0, val precipitation: String = "none")
private data class ForecastDay(val date: Date, val min: Int, val max: Int, val condition: String, val slots: List<ForecastSlot>)
private data class SourceOption(val inputId:String,val type:String,val port:Int=0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        hideSystemUi()
        setContent { MaterialTheme { TvHomeApp(this) } }
    }

    override fun onResume() { super.onResume(); hideSystemUi() }

    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}

@Composable
private fun TvHomeApp(activity: Activity) {
    val context = activity as Context
    val prefs = remember { context.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    var apps by remember { mutableStateOf<List<TvApp>>(emptyList()) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var controlOpen by remember { mutableStateOf(false) }
    var homeDockFocus by rememberSaveable { mutableIntStateOf(0) }
    var language by remember { mutableStateOf(prefs.getString("language", "ru") ?: "ru") }
    var dark by remember { mutableStateOf(prefs.getString("theme", "dark") != "light") }
    var prefsRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "language") language = prefs.getString("language", "ru") ?: "ru"
            if (key == "theme") dark = prefs.getString("theme", "dark") != "light"
            prefsRevision++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    // RenderScript blur blocks this TV's API 30 GPU for several seconds. Keep Haze in
    // compatibility mode and render the material with compositor-only gradients.
    val haze = Unit

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadApps(context) }
    }

    BackHandler(screen != Screen.HOME || controlOpen) {
        if (controlOpen) controlOpen = false else screen = Screen.HOME
    }

    CompositionLocalProvider(LocalDarkTheme provides dark, LocalPrefsRevision provides prefsRevision) { when (screen) {
            Screen.HOME -> HomeScreen(context, apps, haze, controlOpen, language,
                onControl = { controlOpen = !controlOpen },
                onAllApps = { screen = Screen.APPS },
                onLaunch = { launch(context, it) },
                onInputs = { screen = Screen.INPUTS },
                onDock = { screen = Screen.DOCK },
                onSettings = { openLauncherSettings(context) },
                rememberedDockFocus = homeDockFocus,
                onDockFocusChanged = { homeDockFocus = it },
            )
            Screen.APPS -> AppGridScreen(context, haze, apps, language,
                onBack = { screen = Screen.HOME },
                onWeather = { screen = Screen.WEATHER },
                onSystem = { screen = Screen.SYSTEM },
                onYandex = { screen = Screen.YANDEX },
                onSettings = { openLauncherSettings(context) },
                onLaunch = { launch(context, it) })
            Screen.WEATHER -> WeatherScreen(context, haze, language) { screen = Screen.HOME }
            Screen.SYSTEM -> FolderScreen(context, haze, apps.filter { it.system && !it.yandex },
                tr(language, "Системные", "System"), { screen = Screen.APPS }) { launch(context, it) }
            Screen.YANDEX -> FolderScreen(context, haze, apps.filter { it.yandex },
                tr(language, "Сервисы Яндекса", "Yandex Services"), { screen = Screen.APPS }) { launch(context, it) }
            Screen.DOCK -> DockEditor(context, haze, apps, language) { screen = Screen.HOME }
            Screen.INPUTS -> InputScreen(context, haze, language) { screen = Screen.HOME }
    } }
}

@Composable
private fun HomeScreen(
    context: Context,
    apps: List<TvApp>,
    haze: Unit,
    controlOpen: Boolean,
    language: String,
    onControl: () -> Unit,
    onAllApps: () -> Unit,
    onLaunch: (TvApp) -> Unit,
    onInputs: () -> Unit,
    onDock: () -> Unit,
    onSettings: () -> Unit,
    rememberedDockFocus: Int,
    onDockFocusChanged: (Int) -> Unit,
) {
    val prefs = remember { context.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    val prefsRevision=LocalPrefsRevision.current
    val storedCityName = prefs.getString("weather_city", "Москва, Россия") ?: "Москва, Россия"
    val latitude = prefs.getString("weather_lat", "55.7558")?.toDoubleOrNull() ?: 55.7558
    val longitude = prefs.getString("weather_lon", "37.6173")?.toDoubleOrNull() ?: 37.6173
    var cityName by remember(latitude, longitude, language, prefsRevision) { mutableStateOf(placeAndCountry(storedCityName, language)) }
    val clockStyle = prefs.getString("clock_style", "glass") ?: "glass"
    val widgetEffect = prefs.getString("widget_effect", "glass") ?: "glass"
    var weather by remember { mutableStateOf<ForecastDay?>(null) }
    LaunchedEffect(latitude, longitude) { weather = fetchForecast(latitude, longitude)?.firstOrNull() }
    val dockKeys = prefs.getString("dock_csv", "")?.split("|")?.filter { it.isNotBlank() }.orEmpty()
    val favorites = remember(apps, dockKeys) {
        dockKeys.mapNotNull { key -> apps.firstOrNull { it.component.flattenToShortString() == key } }
            .ifEmpty { apps.filter { !it.system }.take(4) }
            .take(7)
    }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) { now = Date(); delay(1_000) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize()) {
            AsyncWallpaper(wallpaperResource(prefs.getString("wallpaper", "night") ?: "night"), Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(if (!LocalDarkTheme.current)
                listOf(Color.White.copy(.10f), Color.Transparent, Color.Black.copy(.25f)) else
                listOf(Color.Black.copy(.52f), Color.Black.copy(.10f), Color.Black.copy(.58f)))))
        }

        Column(Modifier.align(Alignment.TopCenter).padding(top = 16.dp).width(760.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val locale = if (language == "en") Locale.ENGLISH else Locale("ru", "RU")
                WidgetText(SimpleDateFormat("EEEE, d MMMM", locale).format(now).replaceFirstChar { it.uppercase(locale) },
                    if (clockStyle == "compact") 16.sp else 20.sp, FontWeight.Medium, widgetEffect)
                val effectiveStyle = if (clockStyle == "wide" || clockStyle == "glass") "bold" else clockStyle
                val clockSize = when (effectiveStyle) { "compact" -> 54.sp; "thin" -> 104.sp; "tall" -> 112.sp; else -> 128.sp }
                val clockWeight = when (effectiveStyle) { "thin" -> FontWeight.ExtraLight; "compact" -> FontWeight.Medium; else -> FontWeight.Black }
                Box(Modifier.height(when(effectiveStyle){"tall"->172.dp;"compact"->64.dp;else->126.dp}), contentAlignment = Alignment.Center) {
                    WidgetText(SimpleDateFormat(if (clockStyle == "compact") "H:mm" else "HH:mm", locale).format(now),
                        clockSize, clockWeight, widgetEffect, Modifier.graphicsLayer {
                            scaleY = if (effectiveStyle == "tall") 1.68f else 1f
                            scaleX = if (effectiveStyle == "tall") 1.10f else if (effectiveStyle == "thin") .90f else 1f
                        }, (-6).sp)
                }
                val current = weather?.slots?.firstOrNull()?.temperature
                WidgetText("$cityName  •  ${current?.let { signedTemp(it) } ?: "—°"}  ${weather?.condition?.let { weatherName(it,language) } ?: ""}",
                    if(clockStyle=="compact") 14.sp else 17.sp, FontWeight.SemiBold, widgetEffect)
        }

        ControlCenterButton(Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 62.dp), !controlOpen, onControl)

        val dockCount = favorites.size + 1
        val dockFocusRequesters = remember(dockCount) { List(dockCount) { FocusRequester() } }
        LaunchedEffect(dockCount, controlOpen) {
            if (!controlOpen && dockCount > 0) {
                withFrameNanos { }
                dockFocusRequesters[rememberedDockFocus.coerceIn(0, dockCount - 1)].requestFocus()
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
            .width((dockCount * 82 + 24).dp).height(120.dp), contentAlignment = Alignment.BottomCenter) {
            LiquidGlass(haze, Modifier.fillMaxWidth().height(68.dp), 28.dp) { }
            Row(Modifier.align(Alignment.BottomCenter).width((dockCount * 82).dp).height(120.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                favorites.forEachIndexed { index, app ->
                    DockIcon(app.label, drawableBitmap(app.icon), null, null, 48.dp, !controlOpen,
                        dockFocusRequesters[index],
                        { onDockFocusChanged(index) }) { onLaunch(app) }
                }
                val appsIndex = dockCount - 1
                DockIcon(tr(language, "Все приложения", "All Applications"), null, null, Icons.Rounded.MoreHoriz,
                    48.dp, !controlOpen, dockFocusRequesters[appsIndex],
                    { onDockFocusChanged(appsIndex) }, onAllApps)
            }
        }

        if(controlOpen) ControlCenter(context,haze,language,onInputs,onDock,onSettings,onControl)
    }
}

@Composable
private fun WidgetText(text: String, size: androidx.compose.ui.unit.TextUnit, weight: FontWeight, effect: String,
                       modifier: Modifier = Modifier, spacing: androidx.compose.ui.unit.TextUnit = 0.sp) {
    val style = when(effect) {
        "glow" -> TextStyle(color=Color.White, shadow=Shadow(Color(0xD08ACBFF),Offset(0f,2f),18f))
        "normal" -> TextStyle(color=Color.White)
        else -> TextStyle(brush=Brush.linearGradient(listOf(Color.White.copy(.88f),Color.White.copy(.55f),Color(0xFFDDF3FF).copy(.78f))),
            shadow=Shadow(Color.Black.copy(.20f),Offset(0f,1f),3f))
    }
    Text(text, modifier=modifier, fontSize=size, fontWeight=weight, letterSpacing=spacing, style=style)
}

@Composable
private fun BoxScope.ControlCenter(
    context: Context,
    haze: Unit,
    language: String,
    onInputs: () -> Unit,
    onDock: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val wifi = remember { (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true }
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVolume.toFloat()) }
    val firstControl = remember { FocusRequester() }
    val tvInputs=context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
    var connectedCount by remember { mutableIntStateOf(tvInputs?.let(::connectedTvSources)?.count { it.type=="hdmi" } ?: 0) }
    LaunchedEffect(Unit) {
        while(true) {
            volume=audio.getStreamVolume(AudioManager.STREAM_MUSIC)/maxVolume.toFloat()
            connectedCount=tvInputs?.let(::connectedTvSources)?.count { it.type=="hdmi" } ?: 0
            delay(500)
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; firstControl.requestFocus() }

    LiquidGlass(haze, Modifier.align(Alignment.CenterEnd).padding(end = 28.dp).width(430.dp), 34.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr(language, "Пункт управления", "Control Center"), color = Color.White,
                    fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(34.dp).appleClickable(onClose))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlTile(Icons.Rounded.Wifi, "Wi‑Fi", if (wifi) tr(language,"Вкл.","On") else tr(language,"Выкл.","Off"), wifi,
                    Modifier.weight(1f), firstControl) { openSettings(context, "android.settings.WIFI_SETTINGS") }
                ControlTile(Icons.Rounded.Input, tr(language,"Источники","Inputs"),
                    tr(language,"Подключено: $connectedCount","Connected: $connectedCount"), false, Modifier.weight(1f), onClick = onInputs)
            }
            TvVolumeRow(tr(language, "Звук", "Sound"), volume, maxVolume, audio) { volume = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlTile(Icons.Rounded.Apps, "Dock", tr(language,"Настроить","Configure"), false, Modifier.weight(1f), onClick = onDock)
            }
            ControlTile(Icons.Rounded.Settings, tr(language,"Настройки","Settings"),
                tr(language,"Открыть приложение","Open app"), false, Modifier.fillMaxWidth(), onClick = onSettings)
        }
    }
}

@Composable
private fun ControlCenterButton(modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(modifier.size(52.dp).shadow(16.dp, CircleShape, ambientColor = Color.Black.copy(.22f), spotColor = Color.Black.copy(.16f))
        .clip(CircleShape).background(Brush.linearGradient(listOf(Color.White.copy(.30f), Color.White.copy(.08f))))
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(.7f), Color.White.copy(.12f))), CircleShape)
        .appleClickable(onClick, enabled), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ToggleGlyph(knobLeft = true)
            ToggleGlyph(knobLeft = false)
        }
    }
}

@Composable private fun ToggleGlyph(knobLeft: Boolean) {
    Box(Modifier.width(25.dp).height(8.dp).border(1.5.dp, Color.White, RoundedCornerShape(50)),
        contentAlignment = if (knobLeft) Alignment.CenterStart else Alignment.CenterEnd) {
        Box(Modifier.padding(2.dp).size(4.dp).background(Color.White, CircleShape))
    }
}

@Composable
internal fun LiquidGlass(haze: Unit, modifier: Modifier = Modifier, radius: Dp = 26.dp,
                        content: @Composable BoxScope.() -> Unit) {
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    val shape = RoundedCornerShape(radius)
    Box(modifier.shadow(12.dp, shape, ambientColor = Color.Black.copy(.16f), spotColor = Color.Black.copy(.11f))
        .clip(shape)
        .background(Brush.linearGradient(if (dark) listOf(Color(0xD0222730), Color(0xC00B0E13), Color(0xCA171D25))
            else listOf(Color(0xC582929E), Color(0xB85B6974), Color(0xC471828E))))
        .background(Brush.linearGradient(listOf(Color.White.copy(.16f), Color.Transparent, Color.White.copy(.025f))))
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(.68f), Color.White.copy(.23f), Color.White.copy(.08f))), shape),
        content = content)
}

@Composable
private fun ControlTile(icon: ImageVector, title: String, subtitle: String, active: Boolean,
                        modifier: Modifier = Modifier, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val highlight by animateFloatAsState(if (focused) 1f else 0f, SnappySpring, label = "control-highlight")
    Box(modifier.height(68.dp).clip(RoundedCornerShape(20.dp))
        .background(if (active) Color(0xFF0A84FF).copy(.78f) else Color.White.copy(.07f + .16f * highlight))
        .border(1.5.dp, Color.White.copy(.16f + .50f * highlight), RoundedCornerShape(20.dp))
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .controlClickable({ focused = it }, onClick)) {
        Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(Color.White.copy(.92f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if (active) Color(0xFF0A84FF) else Color(0xFF30343B), modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column { Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, color = Color.White.copy(.72f), fontSize = 13.sp) }
        }
    }
}

@Composable
private fun TvVolumeRow(title:String,value:Float,maxVolume:Int,audio:AudioManager,onValueChange:(Float)->Unit) {
    var focused by remember { mutableStateOf(false) }
    fun setVolume(newValue:Float) {
        val normalized=newValue.coerceIn(0f,1f)
        val level=(normalized*maxVolume).roundToInt().coerceIn(0,maxVolume)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC,level,0)
        onValueChange(audio.getStreamVolume(AudioManager.STREAM_MUSIC)/maxVolume.toFloat())
    }
    Column(Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(20.dp))
        .background(Color.White.copy(if(focused) .16f else .09f))
        .border(if(focused) 1.5.dp else 1.dp,Color.White.copy(if(focused) .62f else .20f),RoundedCornerShape(20.dp))
        .onFocusChanged { focused=it.isFocused }
        .onPreviewKeyEvent { event ->
            if(event.type!=KeyEventType.KeyDown) false else when(event.key) {
                Key.DirectionLeft -> { setVolume(value-1f/maxVolume); true }
                Key.DirectionRight -> { setVolume(value+1f/maxVolume); true }
                else -> false
            }
        }.focusable().padding(horizontal=14.dp,vertical=6.dp)) {
        Text(title,color=Color.White,fontSize=14.sp,fontWeight=FontWeight.Bold)
        Row(Modifier.fillMaxWidth().height(18.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.VolumeMute,null,tint=Color.White,modifier=Modifier.size(18.dp))
            BoxWithConstraints(Modifier.weight(1f).padding(horizontal=10.dp).height(12.dp),contentAlignment=Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(.20f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0f,1f)).background(Color.White,RoundedCornerShape(50)))
                }
                Box(Modifier.offset(x=(maxWidth-10.dp)*value.coerceIn(0f,1f)).size(10.dp).shadow(3.dp,CircleShape).background(Color.White,CircleShape))
            }
            Icon(Icons.Rounded.VolumeUp,null,tint=Color.White,modifier=Modifier.size(20.dp))
        }
    }
}

@Composable
private fun MeterTile(icon: ImageVector, title: String, value: Int, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val highlight by animateFloatAsState(if (focused) 1f else 0f, SnappySpring, label = "meter-highlight")
    Box(modifier.height(84.dp).clip(RoundedCornerShape(23.dp)).background(Color.White.copy(.07f + .16f * highlight))
        .border(1.5.dp, Color.White.copy(.16f + .50f * highlight), RoundedCornerShape(23.dp))
        .controlClickable({ focused = it }, onClick)) {
        Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text("$title  $value%", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp)); Box(Modifier.fillMaxWidth().height(5.dp).background(Color.White.copy(.18f), CircleShape)) {
                    Box(Modifier.fillMaxWidth(value / 100f).fillMaxHeight().background(Color.White, CircleShape))
                } }
        }
    }
}

@Composable
private fun AppGridScreen(context: Context, haze: Unit, apps: List<TvApp>, language: String,
                          onBack: () -> Unit, onWeather: () -> Unit, onSystem: () -> Unit,
                          onYandex: () -> Unit, onSettings: () -> Unit, onLaunch: (TvApp) -> Unit) {
    val userApps = apps.filter { !it.system && !it.yandex }
    val firstAppFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; firstAppFocus.requestFocus() }
    GlassPage(haze, tr(language,"Все приложения","All Apps"), onBack) {
        LazyVerticalGrid(GridCells.Fixed(6), contentPadding = PaddingValues(22.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item { IosResourceIcon(tr(language,"Погода","Weather"), R.drawable.icon_weather, 82.dp, firstAppFocus, onClick = onWeather) }
            item { IosResourceIcon(tr(language,"Настройки","Settings"), R.drawable.icon_settings, 82.dp, onClick = onSettings) }
            item { IosResourceIcon(tr(language,"Системные","System"), R.drawable.icon_folder_9, 82.dp, onClick = onSystem) }
            item { IosResourceIcon(tr(language,"Сервисы Яндекса","Yandex Services"), R.drawable.icon_folder_9, 82.dp, onClick = onYandex) }
            items(userApps, key = { it.component.packageName }) { app -> IosAppIcon(app.label, drawableBitmap(app.icon), 82.dp) { onLaunch(app) } }
        }
    }
}

@Composable
private fun FolderScreen(context: Context, haze: Unit, apps: List<TvApp>, title: String,
                         onBack: () -> Unit, onLaunch: (TvApp) -> Unit) {
    val first=remember { FocusRequester() }
    LaunchedEffect(apps.size) { if(apps.isNotEmpty()) { withFrameNanos { }; first.requestFocus() } }
    GlassPage(haze, title, onBack) {
        LazyVerticalGrid(GridCells.Fixed(6), contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            itemsIndexed(apps, key = { _,app -> app.component.packageName }) { index,app ->
                IosAppIcon(app.label, drawableBitmap(app.icon), 82.dp, if(index==0) first else null) { onLaunch(app) }
            }
        }
    }
}

@Composable
private fun GlassPage(haze: Unit, title: String, onBack: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val context = LocalContext.current
    val wallpaper = context.getSharedPreferences("ui", Context.MODE_PRIVATE).getString("wallpaper", "night") ?: "night"
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) { AsyncWallpaper(wallpaperResource(wallpaper), Modifier.fillMaxSize()) }
        LiquidGlass(haze, Modifier.fillMaxSize().padding(28.dp), 34.dp) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ArrowBack, null, tint = Color.White, modifier = Modifier.size(38.dp).appleClickable(onBack))
                    Spacer(Modifier.width(18.dp)); Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.weight(1f).fillMaxWidth(), content = content)
            }
        }
    }
}

@Composable
private fun WeatherScreen(context: Context, haze: Unit, language: String, onBack: () -> Unit) {
    val prefs = remember { context.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    val dark = LocalDarkTheme.current
    var city by remember { mutableStateOf(CityLocation(
        prefs.getString("weather_city", "Москва") ?: "Москва",
        prefs.getString("weather_lat", "55.7558")?.toDoubleOrNull() ?: 55.7558,
        prefs.getString("weather_lon", "37.6173")?.toDoubleOrNull() ?: 37.6173)) }
    var query by remember { mutableStateOf("") }
    var forecast by remember { mutableStateOf<List<ForecastDay>>(emptyList()) }
    var selectedDay by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<CityLocation>>(emptyList()) }
    var recentCities by remember { mutableStateOf(readRecentCities(prefs)) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val backFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val firstDayFocus = remember { FocusRequester() }
    val suggestionFocus = remember(suggestions.size) { List(suggestions.size.coerceAtMost(3)) { FocusRequester() } }
    var fieldFocused by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    var localizedCity by remember(city.latitude, city.longitude, language) {
        mutableStateOf(placeAndCountry(city.name.ifBlank { tr(language,"Москва, Россия","Moscow, Russia") }, language))
    }

    val selectCity: (CityLocation) -> Unit = { found ->
        city = found
        localizedCity = placeAndCountry(found.name,language).ifBlank { found.name }
        prefs.edit().putString("weather_city", found.name)
            .putString("weather_lat", found.latitude.toString())
            .putString("weather_lon", found.longitude.toString()).apply()
        recentCities=(listOf(found)+recentCities.filterNot { kotlin.math.abs(it.latitude-found.latitude)<0.001 && kotlin.math.abs(it.longitude-found.longitude)<0.001 }).take(3)
        writeRecentCities(prefs,recentCities)
        query = ""
        suggestions = emptyList()
        focusManager.clearFocus()
        keyboard?.hide()
    }

    LaunchedEffect(query) {
        if (query.trim().length >= 2) {
            delay(320)
            suggestions = searchCities(query,language)
        } else suggestions = emptyList()
    }

    LaunchedEffect(city) {
        status = tr(language, "Обновление…", "Updating…")
        forecast = fetchForecast(city.latitude, city.longitude).orEmpty()
        selectedDay = 0
        status = if (forecast.isEmpty()) tr(language, "Нет соединения с погодным сервисом", "Weather service unavailable") else ""
    }
    LaunchedEffect(forecast.size) { if(forecast.isNotEmpty()) { withFrameNanos { }; firstDayFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(if(dark)
        listOf(Color(0xFF081C32),Color(0xFF153E60),Color(0xFF285A78)) else
        listOf(Color(0xFF2E78B3),Color(0xFF69A7CF),Color(0xFF9CCBE3))))) {
        Row(Modifier.fillMaxWidth().padding(24.dp).zIndex(10f), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ArrowBack, null, tint = Color.White,
                modifier = Modifier.size(40.dp).focusRequester(backFocus).flatClickable(onBack))
            Spacer(Modifier.width(18.dp))
            Box(Modifier.width(360.dp).height(48.dp)) {
                Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(if (fieldFocused) .28f else .18f))
                    .border(if (fieldFocused) 2.dp else 1.dp, Color.White.copy(if (fieldFocused) .85f else .35f), RoundedCornerShape(18.dp))
                    .padding(start = 16.dp, end = 8.dp), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(tr(language,"Поиск места","Search places"), color = Color.White.copy(.58f), fontSize = 16.sp)
                    BasicTextField(query, { query = it }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            suggestions.firstOrNull()?.let(selectCity) ?: run { focusManager.clearFocus(); keyboard?.hide() }
                        }), textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                        modifier = Modifier.fillMaxWidth().padding(end = if (query.isEmpty()) 0.dp else 34.dp)
                            .focusRequester(fieldFocus).onFocusChanged { fieldFocused = it.isFocused }.onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown || imeVisible) false else when (event.key) {
                                    Key.DirectionDown -> {
                                        if (suggestionFocus.isNotEmpty()) suggestionFocus.first().requestFocus()
                                        else firstDayFocus.requestFocus(); true
                                    }
                                    Key.DirectionLeft, Key.DirectionUp -> { backFocus.requestFocus(); true }
                                    Key.DirectionRight -> { searchFocus.requestFocus(); true }
                                    else -> false
                                }
                            })
                    if (query.isNotEmpty()) Icon(Icons.Rounded.Close, null, tint = Color.White.copy(.82f),
                        modifier = Modifier.align(Alignment.CenterEnd).size(26.dp).flatClickable {
                            query = ""; suggestions = emptyList(); focusManager.clearFocus(); keyboard?.hide()
                        })
                }
                if (suggestions.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().offset(y = 54.dp).zIndex(10f).clip(RoundedCornerShape(16.dp))
                        .background(Color(0xF02A3948)).border(1.dp, Color.White.copy(.28f), RoundedCornerShape(16.dp))) {
                        suggestions.take(3).forEachIndexed { index, suggestion ->
                            var suggestionFocused by remember { mutableStateOf(false) }
                            Text(suggestion.name, color = Color.White, fontSize = 15.sp,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(Color.White.copy(if (suggestionFocused) .24f else .04f))
                                    .border(if (suggestionFocused) 1.5.dp else 0.dp,
                                        Color.White.copy(if (suggestionFocused) .72f else 0f), RoundedCornerShape(11.dp))
                                    .focusRequester(suggestionFocus[index]).onFocusChanged { suggestionFocused = it.isFocused }
                                    .onPreviewKeyEvent { event ->
                                        if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                                            Key.DirectionUp -> {
                                                if (index == 0) fieldFocus.requestFocus() else suggestionFocus[index - 1].requestFocus(); true
                                            }
                                            Key.DirectionDown -> {
                                                if (index < suggestionFocus.lastIndex) suggestionFocus[index + 1].requestFocus()
                                                else firstDayFocus.requestFocus(); true
                                            }
                                            else -> false
                                        }
                                    }.flatClickable { selectCity(suggestion) }
                                    .padding(horizontal = 15.dp, vertical = 10.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(48.dp).focusRequester(searchFocus).onFocusChanged { searchFocused = it.isFocused }
                .clip(CircleShape).background(Color.White.copy(if (searchFocused) .35f else .20f))
                .border(if (searchFocused) 2.dp else 0.dp, Color.White.copy(.85f), CircleShape).flatClickable {
                if (query.isNotBlank()) {
                    status = tr(language, "Поиск…", "Searching…")
                    scope.launch {
                        val found = suggestions.firstOrNull() ?: searchCities(query,language).firstOrNull()
                        if (found != null) selectCity(found)
                        else {
                            status = tr(language, "Город не найден", "City not found")
                            focusManager.clearFocus(); keyboard?.hide()
                        }
                    }
                }
            }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Search, null, tint = Color.White) }
            Row(Modifier.padding(start=8.dp).width(330.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                recentCities.forEach { recent ->
                    var recentFocused by remember(recent) { mutableStateOf(false) }
                    Box(Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(15.dp))
                        .background(Color.White.copy(if(recentFocused).30f else .14f))
                        .border(if(recentFocused) 1.5.dp else 1.dp,Color.White.copy(if(recentFocused).82f else .24f),RoundedCornerShape(15.dp))
                        .onFocusChanged { recentFocused=it.isFocused }.flatClickable { selectCity(recent) }
                        .padding(horizontal=8.dp),contentAlignment=Alignment.Center) {
                        Text(recent.name.substringBefore(','),color=Color.White,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Open‑Meteo · OpenStreetMap", color = Color.White.copy(.62f), fontSize = 12.sp)
        }

        val current = forecast.firstOrNull()?.slots?.firstOrNull()
        Column(Modifier.align(Alignment.TopCenter).padding(top = 84.dp).widthIn(max = 620.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(localizedCity.ifBlank { tr(language,"Москва, Россия","Moscow, Russia") }, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(weatherIcon(current?.condition ?: "cloudy"), null, tint = Color.White, modifier = Modifier.size(64.dp))
                Spacer(Modifier.width(12.dp))
                Text(current?.temperature?.let(::signedTemp) ?: "—°", color = Color.White, fontSize = 66.sp, fontWeight = FontWeight.Medium)
            }
            Text(current?.condition?.let { weatherName(it,language) } ?: status, color = Color.White.copy(.88f), fontSize = 18.sp)
            current?.let { slot ->
                Text(weatherDescription(slot,language),color=Color.White.copy(.70f),fontSize=14.sp,maxLines=1,
                    overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=3.dp))
            }
        }

        Row(Modifier.align(Alignment.Center).padding(top = 92.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            forecast.take(7).forEachIndexed { index, day ->
                var focused by remember { mutableStateOf(false) }
                val selected = selectedDay == index
                Column(Modifier.width(104.dp).height(116.dp)
                    .then(if (index == 0) Modifier.focusRequester(firstDayFocus) else Modifier)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(if (selected) .25f else if (focused) .18f else .09f))
                    .border(1.dp, Color.White.copy(if (selected || focused) .55f else .18f), RoundedCornerShape(22.dp))
                    .onFocusChanged { focused = it.isFocused }.clickable(indication = null,
                        interactionSource = remember { MutableInteractionSource() }) { selectedDay = index }.focusable()
                    .padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(SimpleDateFormat("EEE, d", if(language=="en") Locale.ENGLISH else Locale("ru","RU")).format(day.date), color = Color.White, fontSize = 13.sp)
                    Icon(weatherIcon(day.condition), null, tint = Color.White, modifier = Modifier.size(34.dp).padding(3.dp))
                    Text("${signedTemp(day.max)}  ${signedTemp(day.min)}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        forecast.getOrNull(selectedDay)?.let { day ->
            LiquidGlass(haze, Modifier.align(Alignment.BottomCenter).padding(bottom=10.dp).width(788.dp).height(138.dp), 24.dp) {
                Column(Modifier.padding(horizontal=16.dp,vertical=11.dp)) {
                    Text(tr(language, "Подробно на день", "Day details"), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    val representative=day.slots.minByOrNull { slot -> kotlin.math.abs(slot.hour-12) }
                    representative?.let { slot ->
                        Text("${tr(language,"Влажность","Humidity")} ${slot.humidity}%  •  ${tr(language,"Ветер","Wind")} ${slot.wind} ${tr(language,"м/с","m/s")}  •  ${precipitationName(slot.precipitation,language)}",
                            color=Color.White.copy(.72f),fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis,
                            modifier=Modifier.padding(top=3.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        day.slots.filter { it.hour % 3 == 0 }.take(8).forEach { slot ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("%02d:00".format(slot.hour), color = Color.White.copy(.72f), fontSize = 12.sp)
                                Icon(weatherIcon(slot.condition), null, tint = Color.White, modifier = Modifier.size(27.dp).padding(3.dp))
                                Text(signedTemp(slot.temperature), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockEditor(context: Context, haze: Unit, apps: List<TvApp>, language: String, onBack: () -> Unit) {
    val prefs = remember { context.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    var selected by remember { mutableStateOf(prefs.getString("dock_csv", "")?.split("|")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()) }
    val first=remember { FocusRequester() }
    LaunchedEffect(apps.size) { if(apps.isNotEmpty()) { withFrameNanos { }; first.requestFocus() } }
    GlassPage(haze, tr(language,"Настройка Dock","Configure Dock"), onBack) {
        LazyVerticalGrid(GridCells.Fixed(6), contentPadding = PaddingValues(24.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            itemsIndexed(apps, key = { _,app -> app.component.flattenToShortString() }) { index,app ->
                Box { IosAppIcon(app.label, drawableBitmap(app.icon), 82.dp, if(index==0) first else null) {
                    val key=app.component.flattenToShortString(); selected = if (key in selected) selected-key else if(selected.size<7) selected+key else selected
                    prefs.edit().putString("dock_csv",selected.joinToString("|")).apply()
                }; if(app.component.flattenToShortString() in selected) Icon(Icons.Rounded.CheckCircle,null,tint=Color(0xFF30D158),modifier=Modifier.align(Alignment.TopEnd).size(23.dp)) }
            }
        }
    }
}

@Composable
private fun InputScreen(context: Context, haze: Unit, language: String, onBack: () -> Unit) {
    val firstInput=remember { FocusRequester() }
    val manager=remember { context.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager }
    var inputs by remember { mutableStateOf(connectedTvSources(manager)) }
    LaunchedEffect(Unit) {
        while(true) { inputs=connectedTvSources(manager); delay(750) }
    }
    LaunchedEffect(inputs) { if(inputs.isNotEmpty()) { withFrameNanos { }; firstInput.requestFocus() } }
    GlassPage(haze,tr(language,"Источники","Inputs"),onBack) {
        if (inputs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(tr(language, "Нет подключённых источников", "No connected inputs"), color = Color.White.copy(.72f), fontSize = 20.sp)
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(3),contentPadding=PaddingValues(30.dp),horizontalArrangement=Arrangement.spacedBy(18.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                itemsIndexed(inputs,key={_, input -> input.inputId}) { index, input ->
                    ControlTile(if(input.type=="hdmi") Icons.Rounded.Tv else Icons.Rounded.LiveTv, sourceLabel(input.inputId,language),
                        tr(language,"Подключён","Connected"), false,Modifier.fillMaxWidth(),if(index==0) firstInput else null) {
                        switchTvSource(context,input.inputId)
                    }
                }
            }
        }
    }
}

@Composable
private fun IosResourceIcon(label: String, resource: Int, size: Dp, focusRequester: FocusRequester? = null,
                            enabled: Boolean = true, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(size + 34.dp)) {
        Image(painterResource(resource), null, Modifier.size(size).clip(RoundedCornerShape(size * .225f))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .appleClickable(onClick, enabled), contentScale = ContentScale.Fit)
        Spacer(Modifier.height(7.dp)); Text(label, color=Color.White,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

@Composable
private fun IosAppIcon(label: String, bitmap: ImageBitmap, size: Dp, focusRequester: FocusRequester? = null,
                       enabled: Boolean = true, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(size + 34.dp)) {
        Image(bitmap,null,Modifier.size(size).clip(RoundedCornerShape(size * .225f))
            .then(if(focusRequester!=null) Modifier.focusRequester(focusRequester) else Modifier)
            .appleClickable(onClick, enabled),contentScale=ContentScale.Fit)
        Spacer(Modifier.height(7.dp)); Text(label,color=Color.White,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

@Composable
private fun DockIcon(label: String, bitmap: ImageBitmap?, resource: Int?, vector: ImageVector?, size: Dp,
                     enabled: Boolean = true, focusRequester: FocusRequester,
                     onFocused: () -> Unit,
                     onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    val popoverColor = if(dark) Color(0xF024272D) else Color(0xF0DCE4EA)
    val popoverText = if(dark) Color.White else Color(0xFF20252A)
    val scale by animateFloatAsState(if (focused) 1.11f else 1f, SnappySpring, label = "dock-scale")
    Box(Modifier.width(82.dp).height(120.dp), contentAlignment = Alignment.BottomCenter) {
        if (focused) {
            Column(Modifier.align(Alignment.TopCenter).offset(y = 10.dp).wrapContentWidth(unbounded = true),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.wrapContentWidth(unbounded = true).clip(RoundedCornerShape(10.dp)).background(popoverColor)
                    .padding(horizontal = 9.dp, vertical = 2.dp)) {
                    Text(label, color = popoverText, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
                androidx.compose.foundation.Canvas(Modifier.offset(y = (-2).dp).width(10.dp).height(5.dp)) {
                    val canvasSize = this.size
                    val triangle = Path().apply { moveTo(0f, 0f); lineTo(canvasSize.width / 2f, canvasSize.height); lineTo(canvasSize.width, 0f); close() }
                    drawPath(triangle, popoverColor)
                }
            }
        }
        val imageModifier = Modifier.offset(y = (-10).dp).size(size).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(size * .225f)).focusRequester(focusRequester).onFocusChanged {
                focused = it.isFocused; if (it.isFocused) onFocused()
            }
            .clickable(enabled = enabled, indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .focusable(enabled)
        if (resource != null) Image(painterResource(resource), null, imageModifier, contentScale = ContentScale.Fit)
        else if (bitmap != null) Image(bitmap, null, imageModifier, contentScale = ContentScale.Fit)
        else if (vector != null) Box(imageModifier.background(Color.White.copy(.13f)), contentAlignment = Alignment.Center) {
            Icon(vector, null, tint = Color.White.copy(.82f), modifier = Modifier.size(size * .62f))
        }
    }
}

private fun Modifier.appleClickable(onClick: () -> Unit): Modifier = appleClickable(onClick, true)

private fun Modifier.appleClickable(onClick: () -> Unit, enabled: Boolean): Modifier = composed {
    val interaction=remember { MutableInteractionSource() }; val pressed by interaction.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if(pressed) .96f else if(focused) 1.10f else 1f, SnappySpring,label="press")
    graphicsLayer { scaleX=scale;scaleY=scale }
        .onFocusChanged { focused=it.isFocused }
        .clickable(enabled=enabled,interactionSource=interaction,indication=null,onClick=onClick)
        .focusable(enabled)
}

private fun Modifier.controlClickable(onFocus: (Boolean) -> Unit, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    onFocusChanged { onFocus(it.isFocused) }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .focusable()
}

private fun Modifier.flatClickable(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick).focusable()
}

private object IconBitmapCache { val bitmaps=java.util.concurrent.ConcurrentHashMap<Int,ImageBitmap>() }
private fun cachedDrawableBitmap(drawable:Drawable):ImageBitmap {
    val key=System.identityHashCode(drawable)
    return IconBitmapCache.bitmaps[key] ?: run {
        val w=drawable.intrinsicWidth.coerceAtLeast(1).coerceAtMost(256)
        val h=drawable.intrinsicHeight.coerceAtLeast(1).coerceAtMost(256)
        Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas=Canvas(bitmap);drawable.setBounds(0,0,w,h);drawable.draw(canvas)
        }.asImageBitmap().also { IconBitmapCache.bitmaps[key]=it }
    }
}
@Composable private fun drawableBitmap(drawable: Drawable): ImageBitmap = remember(drawable) {
    cachedDrawableBitmap(drawable)
}

@Composable
internal fun AsyncWallpaper(resource: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(resource) { mutableStateOf(WallpaperCache.bitmaps[resource]) }
    LaunchedEffect(resource) {
        if (bitmap == null) bitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeResource(context.resources, resource)?.asImageBitmap()?.also { WallpaperCache.bitmaps[resource] = it }
        }
    }
    bitmap?.let { Image(it, null, modifier, contentScale = ContentScale.Crop) }
}

private object WallpaperCache { val bitmaps = mutableMapOf<Int, ImageBitmap>() }

internal fun wallpaperResource(key: String) = when (key) {
    "mountain" -> R.drawable.wallpaper_mountain
    "abstract" -> R.drawable.wallpaper_abstract
    "glass_blue" -> R.drawable.wallpaper_glass_blue
    "glass_plum" -> R.drawable.wallpaper_glass_plum
    "ocean" -> R.drawable.wallpaper_ocean
    "dunes_mono" -> R.drawable.wallpaper_dunes_mono
    "dunes_warm" -> R.drawable.wallpaper_dunes_warm
    "forest_blue" -> R.drawable.wallpaper_forest_blue
    "forest_mist" -> R.drawable.wallpaper_forest_mist
    "forest_road" -> R.drawable.wallpaper_forest_road
    "naruto" -> R.drawable.wallpaper_naruto
    "color_burst" -> R.drawable.wallpaper_color_burst
    "sunset_gradient" -> R.drawable.wallpaper_sunset_gradient
    "blue_wave" -> R.drawable.wallpaper_blue_wave
    "peach_gradient" -> R.drawable.wallpaper_peach_gradient
    "diagonal" -> R.drawable.wallpaper_diagonal
    "blue_purple" -> R.drawable.wallpaper_blue_purple
    "rainbow_matte" -> R.drawable.wallpaper_rainbow_matte
    "color_angel" -> R.drawable.wallpaper_color_angel
    "dark_swirl" -> R.drawable.wallpaper_dark_swirl
    "soft_blend" -> R.drawable.wallpaper_soft_blend
    else -> R.drawable.wallpaper_night
}

private fun loadApps(context: Context): List<TvApp> {
    val pm=context.packageManager; val found=linkedMapOf<String,TvApp>()
    listOf(Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER).forEach { category ->
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category),0).forEach { r ->
            val pkg=r.activityInfo.packageName
            if(pkg==context.packageName || found.containsKey(pkg)) return@forEach
            val component=ComponentName(pkg,r.activityInfo.name)
            val system=r.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
            found[pkg]=TvApp(r.loadLabel(pm)?.toString() ?: pkg,component,r.loadIcon(pm),system,
                pkg.startsWith("com.yandex.")||pkg.startsWith("ru.yandex.")||pkg.startsWith("ru.kinopoisk."))
        }
    }
    return found.values.sortedBy { it.label.lowercase() }.also { apps -> apps.forEach { cachedDrawableBitmap(it.icon) } }
}

private fun launch(context: Context, app: TvApp) = runCatching {
    context.startActivity(Intent(Intent.ACTION_MAIN).setComponent(app.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_NO_ANIMATION))
    (context as? Activity)?.overridePendingTransition(0,0)
}.getOrNull()

private fun readRecentCities(prefs:android.content.SharedPreferences):List<CityLocation> =
    prefs.getString("recent_cities","").orEmpty().split(";;").mapNotNull { entry ->
        val parts=entry.split("~")
        if(parts.size!=3) null else CityLocation(Uri.decode(parts[0]),parts[1].toDoubleOrNull() ?: return@mapNotNull null,
            parts[2].toDoubleOrNull() ?: return@mapNotNull null)
    }.take(3)

private fun writeRecentCities(prefs:android.content.SharedPreferences,cities:List<CityLocation>) {
    prefs.edit().putString("recent_cities",cities.take(3).joinToString(";;") {
        "${Uri.encode(it.name)}~${it.latitude}~${it.longitude}"
    }).apply()
}

private fun openSettings(context: Context, action: String) = runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.getOrNull()
private fun openLauncherSettings(context:Context) {
    context.startActivity(Intent(context,SettingsActivity::class.java))
}
private fun tr(language:String,ru:String,en:String)=if(language=="en") en else ru
private fun sourceLabel(inputId:String,language:String):String = when {
    inputId.contains("TunerInputService") -> tr(language,"Телевидение","Television")
    inputId.contains("CompositeInputService") -> "AV"
    inputId.contains("HW4") -> "HDMI 1"
    inputId.contains("HW5") -> "HDMI 2"
    inputId.contains("HW6") -> "HDMI 3"
    else -> ""
}

private fun connectedTvSources(manager:TvInputManager):List<SourceOption> {
    val current=actualTvSource()
    return manager.tvInputList.mapNotNull { info ->
    if(!info.id.contains("com.mediatek.tvinput") ||
        (manager.getInputState(info.id)!=TvInputManager.INPUT_STATE_CONNECTED && info.id!=current)) return@mapNotNull null
    when {
        info.id.contains("TunerInputService/HW0") -> SourceOption(info.id,"tv")
        info.id.contains("HDMIInputService/HW4") -> SourceOption(info.id,"hdmi",1)
        info.id.contains("HDMIInputService/HW5") -> SourceOption(info.id,"hdmi",2)
        info.id.contains("HDMIInputService/HW6") -> SourceOption(info.id,"hdmi",3)
        info.id.contains("CompositeInputService") -> SourceOption(info.id,"av")
        else -> null
    }
}.distinctBy { it.inputId }.sortedWith(compareBy<SourceOption> { if(it.type=="tv") 0 else if(it.type=="av") 1 else 2 }.thenBy { it.port })
}

private fun actualTvSource():String = runCatching {
    val clazz=Class.forName("android.os.SystemProperties")
    clazz.getMethod("get",String::class.java,String::class.java)
        .invoke(null,"persist.sys.lastsource","") as String
}.recoverCatching {
    ProcessBuilder("/system/bin/getprop","persist.sys.lastsource").start()
        .inputStream.bufferedReader().use { it.readLine().orEmpty() }
}.getOrDefault("")

private fun switchTvSource(context:Context,inputId:String) {
    val uri=Uri.Builder().scheme("live-tv").authority("android.media.tv")
        .appendPath("passthrough").appendPath(inputId).build()
    val intent=Intent(Intent.ACTION_VIEW,uri).setComponent(
        ComponentName("com.yandex.tv.live","com.yandex.tv.live.playback.SourcePlaybackActivity"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private suspend fun searchCities(query: String,language:String): List<CityLocation> = withContext(Dispatchers.IO) {
    runCatching {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val connection = URL("https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=5&accept-language=${if(language=="en")"en" else "ru"}&q=$encoded")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 3_500
        connection.readTimeout = 5_000
        connection.setRequestProperty("User-Agent", "YaOS-TV/0.10 (personal Android TV launcher)")
        val array = org.json.JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
        connection.disconnect()
        buildList {
            for (index in 0 until array.length()) array.getJSONObject(index).let {
                val address = it.optJSONObject("address")
                val place = listOf("city", "town", "village", "municipality", "hamlet", "settlement")
                    .firstNotNullOfOrNull { key -> address?.optString(key)?.takeIf(String::isNotBlank) }
                    ?: it.optString("name").ifBlank { query }
                val country = address?.optString("country").orEmpty()
                add(CityLocation(listOf(place, country).filter(String::isNotBlank).joinToString(", "),
                    it.getString("lat").toDouble(), it.getString("lon").toDouble()))
            }
        }
    }.onFailure { Log.e("TVHomeWeather", "City search failed", it) }.getOrDefault(emptyList())
}

private suspend fun reversePlace(latitude:Double,longitude:Double,language:String):String? = withContext(Dispatchers.IO) {
    runCatching {
        val lang=if(language=="en") "en" else "ru"
        val connection=URL("https://nominatim.openstreetmap.org/reverse?format=jsonv2&addressdetails=1&accept-language=$lang&lat=$latitude&lon=$longitude")
            .openConnection() as HttpURLConnection
        connection.connectTimeout=3_500
        connection.readTimeout=5_000
        connection.setRequestProperty("User-Agent","ilyadotgrTV/0.24 (personal Android TV launcher)")
        val root=JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        connection.disconnect()
        val address=root.optJSONObject("address")
        val place=listOf("city","town","village","municipality","hamlet","settlement","county")
            .firstNotNullOfOrNull { key -> address?.optString(key)?.takeIf(String::isNotBlank) }
        val country=address?.optString("country").orEmpty()
        listOfNotNull(place,country.takeIf(String::isNotBlank)).joinToString(", ").takeIf(String::isNotBlank)
    }.onFailure { Log.w("TVHomeWeather","Reverse geocoding failed",it) }.getOrNull()
}

private suspend fun fetchForecast(latitude: Double, longitude: Double): List<ForecastDay>? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL("https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&hourly=temperature_2m,relative_humidity_2m,precipitation,weather_code,wind_speed_10m&wind_speed_unit=ms&timezone=auto&forecast_days=7")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 3_500
        connection.readTimeout = 6_000
        connection.setRequestProperty("User-Agent", "ilyadotgrTV/0.26")
        val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        connection.disconnect()
        val hourly=root.getJSONObject("hourly")
        val times=hourly.getJSONArray("time")
        val temperatures=hourly.getJSONArray("temperature_2m")
        val humidity=hourly.getJSONArray("relative_humidity_2m")
        val precipitation=hourly.getJSONArray("precipitation")
        val codes=hourly.getJSONArray("weather_code")
        val winds=hourly.getJSONArray("wind_speed_10m")
        val parser=SimpleDateFormat("yyyy-MM-dd'T'HH:mm",Locale.US)
        val grouped = linkedMapOf<String, MutableList<Pair<Date, ForecastSlot>>>()
        for(index in 0 until times.length()) {
            val date=parser.parse(times.getString(index)) ?: continue
            val calendar=Calendar.getInstance().apply { time=date }
            val amount=precipitation.optDouble(index,0.0)
            val condition=wmoCondition(codes.optInt(index,3))
            val precipType=when { amount<=0.0->"none"; condition.contains("snow")->"snow"; else->"rain" }
            val slot=ForecastSlot(calendar.get(Calendar.HOUR_OF_DAY),temperatures.optDouble(index).roundToInt(),condition,
                humidity.optInt(index),winds.optDouble(index).roundToInt(),precipType)
            val key=SimpleDateFormat("yyyyMMdd",Locale.US).format(date)
            grouped.getOrPut(key){ mutableListOf() }.add(date to slot)
        }
        grouped.values.take(7).map { entries ->
            val slots = entries.map { it.second }
            val representative = slots.minByOrNull { kotlin.math.abs(it.hour - 12) }?.condition ?: "cloudy"
            ForecastDay(entries.first().first, slots.minOf { it.temperature }, slots.maxOf { it.temperature }, representative, slots)
        }
    }.onFailure { Log.e("TVHomeWeather", "Forecast request failed", it) }.getOrNull()
}

private fun wmoCondition(code:Int)=when(code) {
    0->"clear";1,2->"pcloudy";3->"cloudy";45,48->"fog"
    51,53,55,56,57,61,63,65,66,67,80,81,82->"rain"
    71,73,75,77,85,86->"snow";95,96,99->"storm";else->"cloudy"
}

private fun signedTemp(value: Int) = "${if (value > 0) "+" else ""}$value°"

private fun placeAndCountry(value: String,language:String): String {
    val parts = value.split(',').map(String::trim).filter(String::isNotBlank)
    var place=parts.firstOrNull() ?: value
    var country=parts.lastOrNull().orEmpty()
    if(language=="en") {
        if(place=="Москва") place="Moscow"
        if(place=="Санкт-Петербург") place="Saint Petersburg"
        country=when(country){"Россия","Российская Федерация"->"Russia";"Беларусь"->"Belarus";"Казахстан"->"Kazakhstan";else->country}
    }
    return if (parts.size > 1) "$place, $country" else place
}

private fun weatherName(condition: String,language:String) = when {
    condition.contains("clear") -> tr(language,"Ясно","Clear")
    condition.contains("pcloudy") || condition.contains("cloudy") -> tr(language,"Облачно","Cloudy")
    condition.contains("fog") -> tr(language,"Туман","Fog")
    condition.contains("snow") -> tr(language,"Снег","Snow")
    condition.contains("rain") || condition.contains("shower") -> tr(language,"Дождь","Rain")
    condition.contains("storm") -> tr(language,"Гроза","Thunderstorm")
    else -> tr(language,"Переменная облачность","Partly cloudy")
}

private fun precipitationName(value:String,language:String)=when(value){
    "rain"->tr(language,"Дождь","Rain");"snow"->tr(language,"Снег","Snow");
    else->tr(language,"Без осадков","No precipitation")
}
private fun weatherDescription(slot:ForecastSlot,language:String):String =
    "${weatherName(slot.condition,language)} · ${tr(language,"влажность","humidity")} ${slot.humidity}% · ${tr(language,"ветер","wind")} ${slot.wind} ${tr(language,"м/с","m/s")}" 

private fun weatherIcon(condition: String): ImageVector = when {
    condition.contains("clear") -> Icons.Rounded.WbSunny
    condition.contains("snow") -> Icons.Rounded.AcUnit
    condition.contains("rain") || condition.contains("shower") -> Icons.Rounded.WaterDrop
    condition.contains("storm") -> Icons.Rounded.Thunderstorm
    condition.contains("fog") -> Icons.Rounded.Cloud
    else -> Icons.Rounded.Cloud
}
