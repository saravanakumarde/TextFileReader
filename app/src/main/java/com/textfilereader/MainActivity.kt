package com.textfilereader

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.format.DateUtils
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.textfilereader.databinding.ActivityMainBinding
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var historyManager: HistoryManager
    private var currentFileName: String = ""
    private var currentUri: Uri? = null
    private var plainContent: String = ""

    // Font size (sp), persisted
    private var fontSizeSp: Float = DEFAULT_FONT_SP

    // Search state
    private val matchStarts = mutableListOf<Int>()
    private var currentMatchIndex: Int = -1
    private var searchQuery: String = ""

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { loadFile(it, addToHistory = true) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        historyManager = HistoryManager(this)
        fontSizeSp = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SP)
        binding.tvContent.textSize = fontSizeSp

        setSupportActionBar(binding.toolbar)

        handleIncomingIntent(intent)

        binding.btnOpenFile.setOnClickListener {
            openDocumentLauncher.launch(
                arrayOf(
                    "text/*",
                    "application/json",
                    "application/xml",
                    "application/javascript",
                    "*/*"
                )
            )
        }

        binding.btnClear.setOnClickListener { clearContent() }
        binding.btnClearHistory.setOnClickListener { confirmClearHistory() }

        binding.btnFontIncrease.setOnClickListener { changeFontSize(+2f) }
        binding.btnFontDecrease.setOnClickListener { changeFontSize(-2f) }

        setupSearchBar()

        if (currentUri == null) {
            refreshHistoryList()
        }
    }

    private fun setupSearchBar() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                performSearch(s?.toString().orEmpty())
            }
        })

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                goToNextMatch()
                true
            } else false
        }

        binding.btnSearchNext.setOnClickListener { goToNextMatch() }
        binding.btnSearchPrev.setOnClickListener { goToPrevMatch() }
        binding.btnSearchClose.setOnClickListener { closeSearch() }
    }

    override fun onResume() {
        super.onResume()
        if (binding.emptyScroll.isVisible) {
            refreshHistoryList()
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
                    tryTakePersistablePermission(uri)
                    loadFile(uri, addToHistory = true)
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

    private fun tryTakePersistablePermission(uri: Uri) {
        if (uri.scheme != "content") return
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun loadFile(uri: Uri, addToHistory: Boolean) {
        binding.progressBar.isVisible = true
        binding.tvContent.text = ""
        binding.tvFileName.text = getString(R.string.loading)
        closeSearch()

        try {
            val displayName = getFileName(uri) ?: "Unknown file"
            currentFileName = displayName
            currentUri = uri
            binding.tvFileName.text = displayName

            contentResolver.openInputStream(uri)?.use { inputStream ->
                val charset = detectCharset(uri) ?: Charsets.UTF_8
                val reader = BufferedReader(InputStreamReader(inputStream, charset))
                val content = reader.readText()

                displayText(content, displayName)

                if (addToHistory) {
                    tryTakePersistablePermission(uri)
                    historyManager.add(uri, displayName)
                }
            } ?: run {
                if (!addToHistory) {
                    historyManager.remove(uri.toString())
                    showError(getString(R.string.file_no_longer_available))
                    refreshHistoryList()
                } else {
                    showError(getString(R.string.could_not_open_file))
                }
            }
        } catch (e: SecurityException) {
            if (!addToHistory) {
                historyManager.remove(uri.toString())
                showError(getString(R.string.file_no_longer_available))
                refreshHistoryList()
            } else {
                showError(getString(R.string.could_not_open_file) + ": ${e.localizedMessage}")
            }
        } catch (e: Exception) {
            if (!addToHistory) {
                historyManager.remove(uri.toString())
                showError(getString(R.string.file_no_longer_available))
                refreshHistoryList()
            } else {
                showError(getString(R.string.error_reading_file, e.localizedMessage ?: ""))
            }
        } finally {
            binding.progressBar.isVisible = false
        }
    }

    private fun detectCharset(uri: Uri): Charset? {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(4096)
                val read = stream.read(buffer)
                if (read > 0) {
                    if (buffer[0] == 0xEF.toByte() &&
                        buffer[1] == 0xBB.toByte() &&
                        buffer[2] == 0xBF.toByte()
                    ) {
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
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) name = cursor.getString(index)
                }
            }
        } catch (_: Exception) {
        }
        return name ?: uri.lastPathSegment
    }

    private fun displayText(text: String, title: String) {
        plainContent = text
        binding.tvFileName.text = title
        binding.tvContent.text = text
        binding.tvContent.textSize = fontSizeSp
        binding.emptyScroll.visibility = View.GONE
        binding.scrollView.visibility = View.VISIBLE
        binding.btnClear.isVisible = true
        binding.btnFontIncrease.isVisible = true
        binding.btnFontDecrease.isVisible = true

        val lines = text.lines().size
        val chars = text.length
        binding.tvInfo.text = getString(R.string.file_stats, lines, chars)
        binding.tvInfo.isVisible = true

        invalidateOptionsMenu()
    }

    private fun clearContent() {
        closeSearch()
        plainContent = ""
        binding.tvContent.text = ""
        binding.tvFileName.text = getString(R.string.app_name)
        binding.emptyScroll.visibility = View.VISIBLE
        binding.scrollView.visibility = View.GONE
        binding.btnClear.isVisible = false
        binding.btnFontIncrease.isVisible = false
        binding.btnFontDecrease.isVisible = false
        binding.tvInfo.isVisible = false
        currentFileName = ""
        currentUri = null
        refreshHistoryList()
        invalidateOptionsMenu()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        binding.tvFileName.text = getString(R.string.error)
        binding.emptyScroll.visibility = View.VISIBLE
        binding.scrollView.visibility = View.GONE
        binding.btnClear.isVisible = false
        binding.btnFontIncrease.isVisible = false
        binding.btnFontDecrease.isVisible = false
        binding.tvInfo.isVisible = false
        currentUri = null
        plainContent = ""
    }

    // ── Font size ──────────────────────────────────────────────

    private fun changeFontSize(delta: Float) {
        val newSize = (fontSizeSp + delta).coerceIn(MIN_FONT_SP, MAX_FONT_SP)
        if (newSize == fontSizeSp) return
        fontSizeSp = newSize
        binding.tvContent.textSize = fontSizeSp
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_FONT_SIZE, fontSizeSp)
            .apply()
        Toast.makeText(
            this,
            getString(R.string.font_size_toast, fontSizeSp.toInt()),
            Toast.LENGTH_SHORT
        ).show()
    }

    // ── Search ─────────────────────────────────────────────────

    private fun openSearch() {
        if (plainContent.isEmpty()) {
            Toast.makeText(this, R.string.open_file_first, Toast.LENGTH_SHORT).show()
            return
        }
        binding.searchBar.isVisible = true
        binding.etSearch.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
        val existing = binding.etSearch.text?.toString().orEmpty()
        if (existing.isNotEmpty()) {
            performSearch(existing)
        }
    }

    private fun closeSearch() {
        binding.searchBar.isVisible = false
        searchQuery = ""
        matchStarts.clear()
        currentMatchIndex = -1
        binding.etSearch.setText("")
        binding.tvSearchCount.text = ""
        // Restore plain text without highlights
        if (plainContent.isNotEmpty()) {
            binding.tvContent.text = plainContent
        }
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    private fun performSearch(query: String) {
        searchQuery = query
        matchStarts.clear()
        currentMatchIndex = -1

        if (query.isEmpty() || plainContent.isEmpty()) {
            binding.tvContent.text = plainContent
            binding.tvSearchCount.text = ""
            return
        }

        val lowerContent = plainContent.lowercase()
        val lowerQuery = query.lowercase()
        var index = lowerContent.indexOf(lowerQuery)
        while (index >= 0) {
            matchStarts.add(index)
            index = lowerContent.indexOf(lowerQuery, index + 1)
        }

        if (matchStarts.isEmpty()) {
            binding.tvContent.text = plainContent
            binding.tvSearchCount.text = getString(R.string.no_matches)
            return
        }

        currentMatchIndex = 0
        applyHighlights()
        updateSearchCount()
        scrollToCurrentMatch()
    }

    private fun applyHighlights() {
        if (matchStarts.isEmpty() || searchQuery.isEmpty()) {
            binding.tvContent.text = plainContent
            return
        }
        val spannable = SpannableString(plainContent)
        val queryLen = searchQuery.length
        val highlightBg = Color.parseColor("#FFF59D") // light yellow
        val currentBg = Color.parseColor("#FF9800")   // orange for current
        val currentFg = Color.BLACK

        matchStarts.forEachIndexed { i, start ->
            val end = (start + queryLen).coerceAtMost(plainContent.length)
            if (i == currentMatchIndex) {
                spannable.setSpan(
                    BackgroundColorSpan(currentBg),
                    start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    ForegroundColorSpan(currentFg),
                    start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                spannable.setSpan(
                    BackgroundColorSpan(highlightBg),
                    start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        binding.tvContent.text = spannable
    }

    private fun updateSearchCount() {
        if (matchStarts.isEmpty()) {
            binding.tvSearchCount.text = getString(R.string.no_matches)
        } else {
            binding.tvSearchCount.text = getString(
                R.string.match_count,
                currentMatchIndex + 1,
                matchStarts.size
            )
        }
    }

    private fun goToNextMatch() {
        if (matchStarts.isEmpty()) return
        currentMatchIndex = (currentMatchIndex + 1) % matchStarts.size
        applyHighlights()
        updateSearchCount()
        scrollToCurrentMatch()
    }

    private fun goToPrevMatch() {
        if (matchStarts.isEmpty()) return
        currentMatchIndex =
            if (currentMatchIndex <= 0) matchStarts.size - 1 else currentMatchIndex - 1
        applyHighlights()
        updateSearchCount()
        scrollToCurrentMatch()
    }

    private fun scrollToCurrentMatch() {
        if (currentMatchIndex < 0 || currentMatchIndex >= matchStarts.size) return
        val start = matchStarts[currentMatchIndex]
        // Approximate line by counting newlines before start
        val layout = binding.tvContent.layout ?: return
        val line = layout.getLineForOffset(start)
        val y = layout.getLineTop(line)
        binding.scrollView.post {
            binding.scrollView.smoothScrollTo(0, y)
        }
    }

    // ── History UI ─────────────────────────────────────────────

    private fun refreshHistoryList() {
        val history = historyManager.getHistory()
        val container = binding.historyContainer
        container.removeAllViews()

        if (history.isEmpty()) {
            binding.tvHistoryTitle.isVisible = false
            binding.historyContainer.isVisible = false
            binding.btnClearHistory.isVisible = false
            return
        }

        binding.tvHistoryTitle.isVisible = true
        binding.historyContainer.isVisible = true
        binding.btnClearHistory.isVisible = true

        val inflater = LayoutInflater.from(this)
        for (item in history) {
            val row = inflater.inflate(R.layout.item_history, container, false)
            val tvName = row.findViewById<TextView>(R.id.tvHistoryName)
            val tvTime = row.findViewById<TextView>(R.id.tvHistoryTime)
            val btnRemove = row.findViewById<ImageButton>(R.id.btnRemoveHistory)

            tvName.text = item.displayName
            tvTime.text = DateUtils.getRelativeTimeSpanString(
                item.openedAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()

            row.setOnClickListener {
                loadFile(item.uri, addToHistory = true)
            }
            btnRemove.setOnClickListener {
                historyManager.remove(item.uriString)
                refreshHistoryList()
            }
            container.addView(row)
        }
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_history)
            .setMessage(R.string.clear_history_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                historyManager.clear()
                refreshHistoryList()
                Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── Menu ───────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val hasContent = plainContent.isNotEmpty()
        menu.findItem(R.id.action_search)?.isEnabled = hasContent
        menu.findItem(R.id.action_font_increase)?.isEnabled = hasContent
        menu.findItem(R.id.action_font_decrease)?.isEnabled = hasContent
        menu.findItem(R.id.action_share)?.isEnabled = hasContent
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                openSearch()
                true
            }
            R.id.action_open -> {
                openDocumentLauncher.launch(
                    arrayOf("text/*", "application/json", "application/xml", "*/*")
                )
                true
            }
            R.id.action_share -> {
                val text = plainContent.ifBlank { binding.tvContent.text.toString() }
                if (text.isNotBlank()) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                        putExtra(Intent.EXTRA_SUBJECT, currentFileName.ifBlank { "Text content" })
                    }
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
                } else {
                    Toast.makeText(this, R.string.nothing_to_share, Toast.LENGTH_SHORT).show()
                }
                true
            }
            R.id.action_font_increase -> {
                changeFontSize(+2f)
                true
            }
            R.id.action_font_decrease -> {
                changeFontSize(-2f)
                true
            }
            R.id.action_clear_history -> {
                confirmClearHistory()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val PREFS = "text_file_reader_prefs"
        private const val KEY_FONT_SIZE = "font_size_sp"
        private const val DEFAULT_FONT_SP = 15f
        private const val MIN_FONT_SP = 10f
        private const val MAX_FONT_SP = 36f
    }
}
