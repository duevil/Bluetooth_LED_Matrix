package de.mk.ledmatrixbtapp.data

import android.Manifest.permission
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.InputStream

val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

fun Color.value(part: ColorPart): Float = when (part) {
    ColorPart.R -> red
    ColorPart.G -> green
    ColorPart.B -> blue
}

fun Color.color(part: ColorPart): Color = when (part) {
    ColorPart.R -> copy(green = 0f, blue = 0f)
    ColorPart.G -> copy(red = 0f, blue = 0f)
    ColorPart.B -> copy(red = 0f, green = 0f)
}

val Color.hexString: String
    get() = "%02X%02X%02X".format(
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )

val Int.rgb: Color
    get() = Color(
        red = ((this shr 16) and 0xFF) / 255f,
        green = ((this shr 8) and 0xFF) / 255f,
        blue = (this and 0xFF) / 255f
    )
