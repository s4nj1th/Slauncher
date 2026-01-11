package app.slauncher

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.io.File

object FontManager {
    private var typeface: Typeface? = null

    fun load(context: Context): Typeface? {
        if (typeface != null) return typeface
        val prefs = app.slauncher.data.Prefs(context)
        val path = prefs.customFontPath
        if (path.isNullOrEmpty()) return null
        val file = File(path)
        return if (file.exists()) {
            try {
                Typeface.createFromFile(file).also { typeface = it }
            } catch (e: Exception) {
                null
            }
        } else null
    }

    fun applyToView(root: View, context: Context) {
        val tf = load(context) ?: return
        applyRecursively(root, tf)
    }

    private fun applyRecursively(v: View, tf: Typeface) {
        if (v is TextView) {
            v.typeface = tf
        } else if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                applyRecursively(v.getChildAt(i), tf)
            }
        }
    }

    fun clear() {
        typeface = null
    }
}
