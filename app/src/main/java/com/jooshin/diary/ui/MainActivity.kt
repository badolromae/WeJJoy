package com.jooshin.diary.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jooshin.diary.R
import com.jooshin.diary.data.AppDatabase
import com.jooshin.diary.data.countsByDay
import com.jooshin.diary.databinding.ActivityMainBinding
import com.jooshin.diary.util.AppLock
import com.jooshin.diary.util.DateUtil
import com.jooshin.diary.util.KoreanHolidays
import com.jooshin.diary.util.LunarCalendar
import com.jooshin.diary.sync.SyncManager
import com.jooshin.diary.util.Palette
import com.jooshin.diary.util.Prefs
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: EntryAdapter
    private val dao by lazy { AppDatabase.get(this).diaryDao() }

    private val palette by lazy { Palette.of(this) }
    private var appliedTheme: String = ""

    private var currentMonthFirst = DateUtil.firstOfMonthOf(DateUtil.today())
    private var selectedDay = DateUtil.today()

    private val requestNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Prefs.appTheme(this).styleRes)
        appliedTheme = Prefs.themeKey(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = EntryAdapter { openEditor(it.id) }
        binding.recyclerEntries.layoutManager = LinearLayoutManager(this)
        binding.recyclerEntries.adapter = adapter

        binding.calendarView.onDaySelected = { handleSelect(it) }
        binding.calendarView.onSwipeMonth = { dir ->
            currentMonthFirst = DateUtil.addMonths(currentMonthFirst, dir)
            loadMonth()
        }

        binding.btnPrevMonth.setOnClickListener {
            currentMonthFirst = DateUtil.addMonths(currentMonthFirst, -1)
            loadMonth()
        }
        binding.btnNextMonth.setOnClickListener {
            currentMonthFirst = DateUtil.addMonths(currentMonthFirst, +1)
            loadMonth()
        }
        binding.tvMonthTitle.setOnClickListener { showMonthPicker() }
        binding.btnToday.setOnClickListener {
            currentMonthFirst = DateUtil.firstOfMonthOf(DateUtil.today())
            selectedDay = DateUtil.today()
            loadMonth()
            loadEntries()
        }
        binding.fabAdd.setOnClickListener { openEditorNew(selectedDay) }

        maybeRequestNotifPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // 설정에서 디자인을 바꿨으면 화면을 다시 만들어 즉시 반영
        if (appliedTheme != Prefs.themeKey(this)) {
            recreate()
            return
        }
        if (AppLock.isLockRequired(this)) {
            startActivity(Intent(this, LockActivity::class.java))
            return
        }
        // 상대가 올린 내용이 도착하면 화면을 새로 그린다
        SyncManager.onRemoteChange = { runOnUiThread { loadMonth(); loadEntries() } }
        SyncManager.start(this)

        loadMonth()
        loadEntries()
        handleIntentExtras()
    }

    override fun onPause() {
        SyncManager.onRemoteChange = null
        super.onPause()
    }

    private fun handleSelect(day: Long) {
        selectedDay = day
        val m = DateUtil.firstOfMonthOf(day)
        if (m != currentMonthFirst) {
            currentMonthFirst = m
            loadMonth()
        } else {
            binding.calendarView.setSelected(day)
        }
        loadEntries()
    }

    private fun loadMonth() {
        val gridStart = DateUtil.monthGridStart(currentMonthFirst)
        val gridEnd = gridStart + 41
        lifecycleScope.launch {
            val counts = dao.getOverlapping(gridStart, gridEnd).countsByDay(gridStart, gridEnd)
            binding.calendarView.bind(currentMonthFirst, selectedDay, counts)
            binding.tvMonthTitle.text = DateUtil.formatMonthTitle(currentMonthFirst)
        }
    }

    /** 좌상단 "2026년 8월" 을 눌렀을 때: 년/월을 바로 골라 이동하는 다이얼로그. */
    private fun showMonthPicker() {
        val cur = DateUtil.toDate(currentMonthFirst)
        val yearPicker = NumberPicker(this).apply {
            minValue = 1900
            maxValue = 2049
            value = cur.year.coerceIn(minValue, maxValue)
            wrapSelectorWheel = false
        }
        val monthPicker = NumberPicker(this).apply {
            minValue = 1
            maxValue = 12
            value = cur.monthValue
            wrapSelectorWheel = true
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), 0)
            addView(yearPicker, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(monthPicker, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        AlertDialog.Builder(this)
            .setTitle("년/월로 이동")
            .setView(row)
            .setNegativeButton("취소", null)
            .setPositiveButton("이동") { _, _ ->
                currentMonthFirst = DateUtil.firstOfMonth(yearPicker.value, monthPicker.value)
                loadMonth()
            }
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun loadEntries() {
        binding.tvSelectedDate.text = DateUtil.formatFullDate(selectedDay)
        val info = KoreanHolidays.info(selectedDay)
        val red = DateUtil.dowIndex(selectedDay) == 0 || info.isHoliday
        binding.tvSelectedDate.setTextColor(
            when {
                red -> palette.sun
                DateUtil.dowIndex(selectedDay) == 6 -> palette.sat
                else -> palette.textPrimary
            }
        )
        // 음력은 양력 날짜 옆에 나란히
        val lunar = LunarCalendar.longLabel(selectedDay)
        binding.tvSelectedLunar.text = lunar
        binding.tvSelectedLunar.visibility =
            if (lunar.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE

        // 공휴일/기념일 이름은 그 아래 줄에
        binding.tvSelectedInfo.text = info.full
        binding.tvSelectedInfo.visibility =
            if (info.full.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        binding.tvSelectedInfo.setTextColor(
            if (info.isHoliday) palette.sun else palette.textMuted
        )

        adapter.refDay = selectedDay
        lifecycleScope.launch {
            val list = dao.getForDay(selectedDay)
            adapter.submitList(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun handleIntentExtras() {
        val i = intent ?: return
        val entryId = i.getLongExtra(EXTRA_ENTRY_ID, 0L)
        val date = i.getLongExtra(EXTRA_DATE, Long.MIN_VALUE)
        val isNew = i.getBooleanExtra(EXTRA_NEW, false)
        val time = i.getIntExtra(EXTRA_TIME, -1)
        i.removeExtra(EXTRA_ENTRY_ID)
        i.removeExtra(EXTRA_DATE)
        i.removeExtra(EXTRA_NEW)
        i.removeExtra(EXTRA_TIME)

        if (entryId > 0L) {
            openEditor(entryId)
            return
        }
        if (date != Long.MIN_VALUE) {
            handleSelect(date)
            if (isNew) openEditorNew(date, time)
        }
    }

    private fun openEditorNew(day: Long, time: Int = -1) {
        startActivity(Intent(this, EntryEditorActivity::class.java).apply {
            putExtra(EXTRA_DATE, day)
            putExtra(EXTRA_NEW, true)
            if (time >= 0) putExtra(EXTRA_TIME, time)
        })
    }

    private fun openEditor(id: Long) {
        startActivity(Intent(this, EntryEditorActivity::class.java).apply {
            putExtra(EXTRA_ENTRY_ID, id)
        })
    }

    private fun maybeRequestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !Prefs.isFirstRunDone(this)) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            Prefs.setFirstRunDone(this)
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        // 어두운 상단바 위에서 잘 보이도록 아이콘을 흰색으로
        // (mutate() 로 복사해서 칠해야 다른 화면의 같은 아이콘까지 흰색이 되지 않는다)
        val white = ContextCompat.getColor(this, R.color.white)
        for (i in 0 until menu.size()) {
            menu.getItem(i).icon = menu.getItem(i).icon?.mutate()?.also { it.setTint(white) }
        }
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                startActivity(Intent(this, SearchActivity::class.java)); true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java)); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        const val EXTRA_DATE = "extra_date"
        const val EXTRA_ENTRY_ID = "extra_entry_id"
        const val EXTRA_NEW = "extra_new"

        /** 새 일기의 시작 시각(자정부터의 분). 일 위젯의 시간대 탭에서 사용. */
        const val EXTRA_TIME = "extra_time"
    }
}
