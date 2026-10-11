package com.textfilereader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.textfilereader.databinding.ActivityMainBinding
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentFileName: String = ""

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { loadFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        // Handle file opened from file manager
        handleIncomingIntent(intent)

        binding.btnOpenFile.setOnClickListener {
            openDocumentLauncher.launch(
                arrayOf(
                    "text/*",
                    "application/json",
                    "application/xml",
                    "application/javascript",
                    "*/*" // fallback so user can pick any file
                )
            )
        }

        binding.btnClear.setOnClickListener {
            clearContent()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    loadFile(uri)
                }
            }
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("text/") == true) {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!sharedText.isNullOrBlank()) {
                        displayText(sharedText, "Shared text")
                    }
                }
            }
        }
    }

    private fun loadFile(uri: Uri) {
        binding.progressBar.isVisible = true
        binding.tvContent.text = ""
        binding.tvFileName.text = "Loading..."

        try {
            // Try to get a display name
            currentFileName = getFileName(uri) ?: "Unknown file"
            binding.tvFileName.text = currentFileName

            contentResolver.openInputStream(uri)?.use { inputStream ->
                // Detect encoding (simple UTF-8 / fallback)
                val charset = detectCharset(uri) ?: Charsets.UTF_8
                val reader = BufferedReader(InputStreamReader(inputStream, charset))
                val content = reader.readText()

                displayText(content, currentFileName)
            } ?: run {
                showError("Could not open the file.")
            }
        } catch (e: Exception) {
            showError("Error reading file: ${e.localizedMessage}")
        } finally {
            binding.progressBar.isVisible = false
        }
    }

    private fun detectCharset(uri: Uri): Charset? {
        // Very simple detection – try UTF-8 first; most modern text files use it
        // For production apps you could use juniversalchardet or similar
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(4096)
                val read = stream.read(buffer)
                if (read > 0) {
                    // Check for BOM
                    if (buffer[0] == 0xEF.toByte() && buffer[1] == 0xBB.toByte() && buffer[2] == 0xBF.toByte()) {
                        return Charsets.UTF_8
                    }
                }
            }
            Charsets.UTF_8
        } catch (e: Exception) {
            Charsets.UTF_8
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    name = cursor.getString(index)
                }
            }
        }
        return name ?: uri.lastPathSegment
    }

    private fun displayText(text: String, title: String) {
        binding.tvFileName.text = title
        binding.tvContent.text = text
        binding.tvEmpty.visibility = View.GONE
        binding.scrollView.visibility = View.VISIBLE
        binding.btnClear.isVisible = true

        // Show line count and size info
        val lines = text.lines().size
        val chars = text.length
        binding.tvInfo.text = "$lines lines • $chars characters"
        binding.tvInfo.isVisible = true
    }

    private fun clearContent() {
        binding.tvContent.text = ""
        binding.tvFileName.text = getString(R.string.app_name)
        binding.tvEmpty.visibility = View.VISIBLE
        binding.scrollView.visibility = View.GONE
        binding.btnClear.isVisible = false
        binding.tvInfo.isVisible = false
        currentFileName = ""
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        binding.tvFileName.text = "Error"
        binding.tvEmpty.visibility = View.VISIBLE
        binding.scrollView.visibility = View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_open -> {
                openDocumentLauncher.launch(
                    arrayOf("text/*", "application/json", "application/xml", "*/*")
                )
                true
            }
            R.id.action_share -> {
                val text = binding.tvContent.text.toString()
                if (text.isNotBlank()) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                        putExtra(Intent.EXTRA_SUBJECT, currentFileName.ifBlank { "Text content" })
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share text"))
                } else {
                    Toast.makeText(this, "Nothing to share", Toast.LENGTH_SHORT).show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
