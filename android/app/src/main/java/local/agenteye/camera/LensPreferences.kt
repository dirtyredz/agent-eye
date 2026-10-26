package local.agenteye.camera

import android.content.Context

/** Selection backed by the app-private `camera` preferences file (`lens` key). */
fun LensSelection.Companion.fromPreferences(context: Context): LensSelection {
    val prefs = context.getSharedPreferences(LensSelection.PREFS_NAME, Context.MODE_PRIVATE)
    return LensSelection(prefs.getString(LensSelection.LENS_KEY, null)) {
        prefs.edit().putString(LensSelection.LENS_KEY, it).apply()
    }
}
