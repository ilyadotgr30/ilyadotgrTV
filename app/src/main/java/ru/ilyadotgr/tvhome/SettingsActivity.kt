package ru.ilyadotgr.tvhome

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setContent { MaterialTheme { SettingsScreen() } }
    }

    @Composable private fun SettingsScreen() {
        val prefs=remember { getSharedPreferences("ui",MODE_PRIVATE) }
        var section by remember { mutableIntStateOf(0) }
        var dark by remember { mutableStateOf(prefs.getString("theme","dark")!="light") }
        var language by remember { mutableStateOf(prefs.getString("language","ru") ?: "ru") }
        var wallpaper by remember { mutableStateOf(prefs.getString("wallpaper", "night") ?: "night") }
        var clockStyle by remember { mutableStateOf(prefs.getString("clock_style", "glass") ?: "glass") }
        var widgetEffect by remember { mutableStateOf(prefs.getString("widget_effect", "glass") ?: "glass") }
        val haze=Unit
        val firstSection=remember { FocusRequester() }
        LaunchedEffect(Unit) { withFrameNanos { }; firstSection.requestFocus() }
        val sections=if(language=="en") listOf("General","Appearance","Apps & Dock","System")
            else listOf("Основные","Оформление","Приложения и Dock","Система")
        CompositionLocalProvider(LocalDarkTheme provides dark) { Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) { AsyncWallpaper(wallpaperResource(wallpaper), Modifier.fillMaxSize()) }
            LiquidGlass(haze,Modifier.fillMaxSize().padding(28.dp),34.dp) {
                Row(Modifier.fillMaxSize().padding(22.dp)) {
                    Column(Modifier.width(265.dp).fillMaxHeight()) {
                        Text(if(language=="en") "Settings" else "Настройки",color=Color.White,fontSize=30.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(14.dp))
                        Spacer(Modifier.height(12.dp))
                        sections.forEachIndexed { index,title ->
                            var focused by remember { mutableStateOf(false) }
                            Box(Modifier.fillMaxWidth().padding(vertical=4.dp).clip(RoundedCornerShape(16.dp))
                                .background(when { focused -> Color.White.copy(.18f); section==index -> Color.White.copy(.10f); else -> Color.Transparent })
                                .border(if(focused) 1.dp else 0.dp, Color.White.copy(.48f), RoundedCornerShape(16.dp))
                                .then(if(index==0) Modifier.focusRequester(firstSection) else Modifier)
                                .onFocusChanged { focused=it.isFocused; if(it.isFocused) section=index }
                                .clickable { section=index }.focusable().padding(horizontal=16.dp,vertical=11.dp)) {
                                Text(title,color=Color.White,fontSize=17.sp,fontWeight=if(section==index || focused) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp))
                        .background(if(dark) Color.Black.copy(.26f) else Color.White.copy(.12f)).padding(28.dp)) {
                        Text(sections[section],color=Color.White,fontSize=27.sp,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(26.dp))
                        when(section) {
                            1 -> {
                                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.weight(1f)) { SettingRow(if(language=="en")"Theme" else "Тема",if(language=="en"){if(dark)"Dark" else "Light"}else{if(dark)"Тёмная" else "Светлая"}) { dark=!dark;prefs.edit().putString("theme",if(dark)"dark" else "light").apply() } }
                                    Box(Modifier.weight(1f)) { SettingRow(if(language=="en")"Language" else "Язык",if(language=="ru")"Русский" else "English") { language=if(language=="ru")"en" else "ru";prefs.edit().putString("language",language).apply() } }
                                }
                                Text(if(language=="en") "Widget" else "Виджет",color=Color.White.copy(.82f),fontSize=16.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=10.dp,bottom=7.dp))
                                ClockStylePicker(clockStyle,language) { selected -> clockStyle=selected; prefs.edit().putString("clock_style",selected).apply() }
                                WidgetEffectPicker(widgetEffect,language) { selected -> widgetEffect=selected; prefs.edit().putString("widget_effect",selected).apply() }
                                Text(if(language=="en") "Wallpaper" else "Обои",color=Color.White.copy(.82f),fontSize=16.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=8.dp,bottom=6.dp))
                                WallpaperPicker(wallpaper,language) { selected -> wallpaper=selected; prefs.edit().putString("wallpaper",selected).apply() }
                            }
                            2 -> Text(if(language=="en")"Dock contents, order and application folders" else "Состав Dock, порядок и папки приложений",color=Color.White.copy(.75f),fontSize=17.sp)
                            3 -> SettingRow(if(language=="en")"System settings" else "Системные настройки",if(language=="en")"Open" else "Открыть") { runCatching { startActivity(android.content.Intent("android.settings.SETTINGS")) } }
                            else -> Column {
                                Text("ilyadotgrTV",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold)
                                Text(if(language=="en")"A lightweight personal launcher for Android TV" else "Лёгкий персональный лаунчер для Android TV",color=Color.White.copy(.72f),fontSize=16.sp,modifier=Modifier.padding(top=8.dp))
                                Text(if(language=="en")"Developed by @ilyadotgr" else "Разработчик — @ilyadotgr",color=Color.White.copy(.88f),fontSize=17.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=20.dp))
                                Text("Instagram · Telegram: @ilyadotgr",color=Color.White.copy(.65f),fontSize=15.sp,modifier=Modifier.padding(top=5.dp))
                                Text(if(language=="en")"Version 0.30.0" else "Версия 0.30.0",color=Color.White.copy(.48f),fontSize=14.sp,modifier=Modifier.padding(top=24.dp))
                            }
                        }
                    }
                }
            }
        } }
    }

    @Composable private fun SettingRow(title:String,value:String,onClick:()->Unit) {
        var focused by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().padding(vertical=6.dp).clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(if(focused) .17f else .07f))
            .border(1.dp,Color.White.copy(if(focused) .48f else .12f),RoundedCornerShape(18.dp))
            .onFocusChanged { focused=it.isFocused }.clickable(onClick=onClick).focusable().padding(horizontal=15.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(title,color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f));Text(value,color=Color.White.copy(.68f),fontSize=14.sp)
        }
    }

    @Composable private fun WallpaperPicker(selected:String,language:String,onSelect:(String)->Unit) {
        val options=if(language=="en") listOf(
            Triple("night","Night mountain",R.drawable.wallpaper_thumb_night),Triple("mountain","Mountains",R.drawable.wallpaper_thumb_mountain),
            Triple("abstract","Abstract",R.drawable.wallpaper_thumb_abstract),Triple("glass_blue","Blue glass",R.drawable.wallpaper_thumb_glass_blue),
            Triple("glass_plum","Plum glass",R.drawable.wallpaper_thumb_glass_plum),Triple("ocean","Dark ocean",R.drawable.wallpaper_thumb_ocean),
            Triple("dunes_mono","Mono dunes",R.drawable.wallpaper_thumb_dunes_mono),Triple("dunes_warm","Warm dunes",R.drawable.wallpaper_thumb_dunes_warm),
            Triple("forest_blue","Blue forest",R.drawable.wallpaper_thumb_forest_blue),Triple("forest_mist","Misty forest",R.drawable.wallpaper_thumb_forest_mist),
            Triple("forest_road","Forest road",R.drawable.wallpaper_thumb_forest_road),Triple("naruto","Naruto",R.drawable.wallpaper_thumb_naruto),
            Triple("color_burst","Color burst",R.drawable.wallpaper_thumb_color_burst),Triple("sunset_gradient","Sunset",R.drawable.wallpaper_thumb_sunset_gradient),
            Triple("blue_wave","Blue wave",R.drawable.wallpaper_thumb_blue_wave),Triple("peach_gradient","Peach",R.drawable.wallpaper_thumb_peach_gradient),
            Triple("diagonal","Diagonal",R.drawable.wallpaper_thumb_diagonal),Triple("blue_purple","Blue purple",R.drawable.wallpaper_thumb_blue_purple),
            Triple("rainbow_matte","Rainbow",R.drawable.wallpaper_thumb_rainbow_matte),Triple("color_angel","Color angel",R.drawable.wallpaper_thumb_color_angel),
            Triple("dark_swirl","Dark swirl",R.drawable.wallpaper_thumb_dark_swirl),Triple("soft_blend","Soft blend",R.drawable.wallpaper_thumb_soft_blend)
        ) else listOf(
            Triple("night","Ночная гора",R.drawable.wallpaper_thumb_night),
            Triple("mountain","Горы",R.drawable.wallpaper_thumb_mountain),
            Triple("abstract","Абстракция",R.drawable.wallpaper_thumb_abstract),
            Triple("glass_blue","Синее стекло",R.drawable.wallpaper_thumb_glass_blue),
            Triple("glass_plum","Сливовое стекло",R.drawable.wallpaper_thumb_glass_plum),
            Triple("ocean","Тёмный океан",R.drawable.wallpaper_thumb_ocean),
            Triple("dunes_mono","Чёрные дюны",R.drawable.wallpaper_thumb_dunes_mono),
            Triple("dunes_warm","Тёплые дюны",R.drawable.wallpaper_thumb_dunes_warm),
            Triple("forest_blue","Синий лес",R.drawable.wallpaper_thumb_forest_blue),
            Triple("forest_mist","Туманный лес",R.drawable.wallpaper_thumb_forest_mist),
            Triple("forest_road","Лесная дорога",R.drawable.wallpaper_thumb_forest_road),
            Triple("naruto","Наруто",R.drawable.wallpaper_thumb_naruto),
            Triple("color_burst","Всплеск цвета",R.drawable.wallpaper_thumb_color_burst),
            Triple("sunset_gradient","Закат",R.drawable.wallpaper_thumb_sunset_gradient),
            Triple("blue_wave","Синяя волна",R.drawable.wallpaper_thumb_blue_wave),
            Triple("peach_gradient","Персиковый",R.drawable.wallpaper_thumb_peach_gradient),
            Triple("diagonal","Диагонали",R.drawable.wallpaper_thumb_diagonal),
            Triple("blue_purple","Сине-фиолетовый",R.drawable.wallpaper_thumb_blue_purple),
            Triple("rainbow_matte","Радуга",R.drawable.wallpaper_thumb_rainbow_matte),
            Triple("color_angel","Цветное сияние",R.drawable.wallpaper_thumb_color_angel),
            Triple("dark_swirl","Тёмный вихрь",R.drawable.wallpaper_thumb_dark_swirl),
            Triple("soft_blend","Мягкие цвета",R.drawable.wallpaper_thumb_soft_blend),
        )
        LazyRow(horizontalArrangement=Arrangement.spacedBy(9.dp),modifier=Modifier.fillMaxWidth()) {
                    items(options,key={it.first}) { (key,title,preview) ->
                        var focused by remember { mutableStateOf(false) }
                        Column(Modifier.width(96.dp).clip(RoundedCornerShape(14.dp))
                            .background(if(focused) Color.White.copy(.16f) else Color.Black.copy(.16f))
                            .border(if(selected==key || focused) 1.5.dp else 1.dp,
                                if(focused) Color.White.copy(.65f) else if(selected==key) Color.White.copy(.48f) else Color.White.copy(.14f),RoundedCornerShape(14.dp))
                            .onFocusChanged { focused=it.isFocused }
                            .clickable { onSelect(key) }.focusable().padding(5.dp)) {
                            Image(painterResource(preview),null,Modifier.fillMaxWidth().height(50.dp)
                                .clip(RoundedCornerShape(10.dp)),contentScale=ContentScale.Crop)
                            Text(title,color=Color.White,fontSize=10.sp,maxLines=1,modifier=Modifier.padding(horizontal=4.dp,vertical=3.dp))
                        }
                    }
        }
    }

    @Composable private fun ClockStylePicker(selected:String,language:String,onSelect:(String)->Unit) {
        val options=if(language=="en") listOf("bold" to "Bold","tall" to "Tall","thin" to "Thin","compact" to "Small")
            else listOf("bold" to "Жирные","tall" to "Высокие","thin" to "Тонкие","compact" to "Маленькие")
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            options.forEach { (key,title) ->
                var focused by remember { mutableStateOf(false) }
                Column(Modifier.width(112.dp).height(104.dp).clip(RoundedCornerShape(15.dp))
                    .background(Color.White.copy(if(focused) .17f else if(selected==key) .10f else .05f))
                    .border(if(focused || selected==key) 1.5.dp else 1.dp,Color.White.copy(if(focused) .65f else if(selected==key) .40f else .12f),RoundedCornerShape(15.dp))
                    .onFocusChanged { focused=it.isFocused }.clickable { onSelect(key) }.focusable()
                    .padding(7.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(if(language=="en")"Saturday, November 8" else "Суббота, 8 ноября",color=Color.White.copy(.65f),fontSize=6.sp,maxLines=1)
                    Box(Modifier.height(46.dp),contentAlignment=Alignment.Center) { Text("12:48",color=Color.White,
                        modifier=Modifier.graphicsLayer { scaleY=if(key=="tall") 1.60f else 1f; scaleX=if(key=="tall") 1.06f else if(key=="thin") .90f else 1f },
                        fontSize=when(key){"tall"->31.sp;"thin"->27.sp;"compact"->17.sp;else->34.sp},
                        fontWeight=when(key){"thin"->FontWeight.ExtraLight;"tall"->FontWeight.Black;"compact"->FontWeight.Medium;else->FontWeight.Black},
                        letterSpacing=(-2).sp,maxLines=1) }
                    Text(if(language=="en")"Moscow, Russia  •  +4°" else "Москва, Россия  •  +4°",color=Color.White.copy(.72f),fontSize=6.sp,maxLines=1)
                    Spacer(Modifier.weight(1f)); Text(title,color=Color.White.copy(.78f),fontSize=10.sp,maxLines=1)
                }
            }
        }
    }

    @Composable private fun WidgetEffectPicker(selected:String,language:String,onSelect:(String)->Unit) {
        val options=if(language=="en") listOf("glass" to "Glass","glow" to "Glow","normal" to "Plain")
            else listOf("glass" to "Стекло","glow" to "Свечение","normal" to "Обычный")
        Row(Modifier.padding(top=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            options.forEach { (key,title) ->
                var focused by remember { mutableStateOf(false) }
                Box(Modifier.width(110.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(if(focused) .17f else if(selected==key) .10f else .04f))
                    .border(1.dp,Color.White.copy(if(focused) .60f else if(selected==key) .35f else .10f),RoundedCornerShape(12.dp))
                    .onFocusChanged { focused=it.isFocused }.clickable { onSelect(key) }.focusable()
                    .padding(vertical=7.dp),contentAlignment=Alignment.Center) {
                    Text(title,color=when(key){"glass"->Color.White.copy(.68f);else->Color.White},fontSize=12.sp,
                        style=if(key=="glow") androidx.compose.ui.text.TextStyle(shadow=androidx.compose.ui.graphics.Shadow(Color(0xFF80C8FF),androidx.compose.ui.geometry.Offset.Zero,8f)) else androidx.compose.ui.text.TextStyle.Default)
                }
            }
        }
    }
}
