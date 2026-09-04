package com.jooshin.diary.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.chip.Chip
import com.jooshin.diary.R
import com.jooshin.diary.data.AppDatabase
import com.jooshin.diary.data.DiaryEntry
import com.jooshin.diary.databinding.ActivityDetailBinding
import com.jooshin.diary.util.DateUtil
import com.jooshin.diary.util.ImageStore
import com.jooshin.diary.util.Prefs
import com.jooshin.diary.util.Stickers
import kotlinx.coroutines.launch

/**
 * 일기 '보기'(읽기) 전체화면.
 * - 제목/내용/기분/중요도를 이모티콘 그림으로 보여준다. (내용의 이모티콘은 큼직하게)
 * - 우측 상단 연필 아이콘을 누르면 수정 화면으로 들어간다.
 */
class EntryDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private val dao by lazy { AppDatabase.get(this).diaryDao() }
    private var entryId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Prefs.appTheme(this).styleRes)
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarDetail)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarDetail.setNavigationOnClickListener { finish() }

        entryId = intent.getLongExtra(MainActivity.EXTRA_ENTRY_ID, 0L)
        if (entryId <= 0L) finish()
    }

    override fun onResume() {
        super.onResume()
        reload()   // 수정 후 돌아오면 다시 읽어 새로고침
    }

    private fun reload() {
        lifecycleScope.launch {
            val e = dao.getById(entryId)
            if (e == null || e.deletedAt != 0L) { finish(); return@launch }
            bind(e)
        }
    }

    private fun bind(e: DiaryEntry) {
        val end = if (e.endDateEpochDay > e.dateEpochDay) e.endDateEpochDay else e.dateEpochDay
        binding.tvDetailMeta.text =
            DateUtil.formatRangeLong(e.dateEpochDay, e.timeMinutes, end, e.endTimeMinutes)

        val bmp = if (e.sticker.isNotEmpty()) Stickers.bitmap(this, e.sticker) else null
        if (bmp != null) {
            binding.detailSticker.setImageBitmap(bmp)
            binding.detailSticker.visibility = View.VISIBLE
        } else {
            binding.detailSticker.visibility = View.GONE
        }

        binding.tvDetailMood.text = e.mood
        binding.tvDetailMood.visibility = if (e.mood.isBlank()) View.GONE else View.VISIBLE

        binding.tvDetailTitle.text = Stickers.applyInline(
            this, e.title.ifBlank { "(제목 없음)" }, binding.tvDetailTitle.textSize
        )

        val content = e.content.trim()
        binding.tvDetailContent.text =
            if (content.isEmpty()) "(내용 없음)"
            else Stickers.applyInline(this, content, binding.tvDetailContent.textSize, 2.6f)

        binding.chipDetailTags.removeAllViews()
        for (t in e.tags) {
            binding.chipDetailTags.addView(Chip(this).apply {
                text = "#$t"; isClickable = false; isCheckable = false
            })
        }
        binding.chipDetailTags.visibility = if (e.tags.isEmpty()) View.GONE else View.VISIBLE

        binding.detailImportance.progress = e.importance.coerceIn(1, 100)
        binding.tvDetailImpPct.text = "${e.importance}%"

        binding.detailPhotos.removeAllViews()
        val ph = (200 * resources.displayMetrics.density).toInt()
        val gap = (8 * resources.displayMetrics.density).toInt()
        for (name in e.photos) {
            val iv = ImageView(this).apply {
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ph)
                lp.topMargin = gap
                layoutParams = lp
                scaleType = ImageView.ScaleType.CENTER_CROP
                load(ImageStore.file(this@EntryDetailActivity, name)) { crossfade(true) }
            }
            binding.detailPhotos.addView(iv)
        }
        binding.detailPhotos.visibility = if (e.photos.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_detail, menu)
        val white = ContextCompat.getColor(this, R.color.white)
        for (i in 0 until menu.size()) {
            menu.getItem(i).icon = menu.getItem(i).icon?.mutate()?.also { it.setTint(white) }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_edit -> {
                startActivity(
                    Intent(this, EntryEditorActivity::class.java)
                        .putExtra(MainActivity.EXTRA_ENTRY_ID, entryId)
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
