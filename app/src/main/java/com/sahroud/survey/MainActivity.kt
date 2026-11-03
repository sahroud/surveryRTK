package com.sahroud.survey

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import java.text.SimpleDateFormat
import java.util.*

 data class SurveyPoint(val number:Int, val lat:Double, val lon:Double, val altitude:Double, val accuracy:Float, val time:String, val quality:String)

class MainActivity : ComponentActivity() {
    private lateinit var fused: FusedLocationProviderClient
    private var latest: Location? = null
    private var locationCallback: LocationCallback? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startGpsIfAllowed() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fused = LocationServices.getFusedLocationProviderClient(this)
        setContent { SurveyScreen(onPermission = { requestGps() }, onStart = { startGpsIfAllowed() }, getLocation = { latest }) }
        requestGps()
    }
    private fun requestGps() {
        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    private fun startGpsIfAllowed() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).setMinUpdateIntervalMillis(500L).build()
        locationCallback?.let { fused.removeLocationUpdates(it) }
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) { latest = result.lastLocation }
        }
        try { fused.requestLocationUpdates(req, locationCallback!!, mainLooper) } catch (_: SecurityException) { }
    }
}

@Composable
private fun SurveyScreen(onPermission:()->Unit, onStart:()->Unit, getLocation:()->Location?) {
    var points by remember { mutableStateOf(listOf<SurveyPoint>()) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while(true) { kotlinx.coroutines.delay(1000); tick++ } }
    val loc = getLocation()
    val accuracy = loc?.accuracy ?: Float.MAX_VALUE
    val rtKFixed = false // RTK Bluetooth/NMEA integration is a separate implementation phase.
    val quality = if (rtKFixed && accuracy <= .05f) "RTK FIXED — هدف ۵ سانتی‌متر تأیید شد" else "GPS گوشی — دقت ۵ سانتی‌متر تأیید نشده"
    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("نقشه‌برداری میدانی", style = MaterialTheme.typography.headlineSmall)
            Text("وضعیت: $quality")
            Text(if(loc == null) "در انتظار دریافت موقعیت…" else "خطای افقی تخمینی: ${"%.2f".format(accuracy)} متر")
            Text(if(loc == null) "مختصات در دسترس نیست" else "عرض: ${loc.latitude}\nطول: ${loc.longitude}\nارتفاع GPS: ${"%.2f".format(loc.altitude)} متر")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPermission) { Text("مجوز موقعیت") }
                Button(onClick = onStart) { Text("شروع GPS") }
                Button(enabled = loc != null, onClick = {
                    val l = getLocation() ?: return@Button
                    val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(l.time))
                    points = points + SurveyPoint(points.size+1,l.latitude,l.longitude,l.altitude,l.accuracy,stamp,quality)
                }) { Text("ثبت نقطه") }
            }
            Text("نقاط ثبت‌شده: ${points.size}", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(points.reversed()) { p ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) {
                        Text("نقطه ${p.number} • ${p.time}")
                        Text("${p.lat}, ${p.lon}")
                        Text("ارتفاع GPS: ${"%.2f".format(p.altitude)} m • دقت: ${"%.2f".format(p.accuracy)} m")
                        Text(p.quality)
                    } }
                }
            }
            Text("نسخه آغازین؛ ذخیره دائمی، نقشه، خروجی و اتصال RTK هنوز تکمیل نشده‌اند.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(2.dp)); Text("sahroud game", style = MaterialTheme.typography.labelSmall)
        }
    }
}
