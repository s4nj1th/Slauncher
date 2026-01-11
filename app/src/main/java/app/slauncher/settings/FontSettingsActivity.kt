package app.slauncher.settings

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import app.slauncher.FontManager
import app.slauncher.R
import app.slauncher.data.Prefs
import java.io.File
import java.io.FileOutputStream

class FontSettingsActivity : AppCompatActivity() {
    private lateinit var btnPick: Button
    private lateinit var tvPreview: TextView
    private val pickFont = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { applyFontFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_font_settings)
        btnPick = findViewById(R.id.btn_pick_font)
        tvPreview = findViewById(R.id.tv_font_preview)

        btnPick.setOnClickListener {
            pickFont.launch(arrayOf("*/*"))
        }

        FontManager.load(this)?.let { FontManager.applyToView(tvPreview, this) }
    }

    private fun applyFontFromUri(uri: Uri) {
        try {
            val input = contentResolver.openInputStream(uri) ?: run {
                Toast.makeText(this, R.string.unable_to_open_app, Toast.LENGTH_SHORT).show()
                return
            }
            val fontsDir = File(filesDir, "fonts").apply { if (!exists()) mkdirs() }
            val outFile = File(fontsDir, "custom_font.ttf")
            FileOutputStream(outFile).use { out -> input.copyTo(out) }
            val prefs = Prefs(this)
            prefs.customFontPath = outFile.absolutePath
            FontManager.clear()
            FontManager.load(this)
            FontManager.applyToView(window.decorView.rootView, this)
            Toast.makeText(this, R.string.font_applied, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.unable_to_open_app, Toast.LENGTH_SHORT).show()
        }
    }
}
