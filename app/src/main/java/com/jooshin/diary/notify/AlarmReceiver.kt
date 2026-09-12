package com.jooshin.diary.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jooshin.diary.data.AppDatabase
import com.jooshin.diary.data.DiaryEntry
import com.jooshin.diary.util.DateUtil
import com.jooshin.diary.util.Stickers
import com.jooshin.diary.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ReminderScheduler.ACTION_DAILY -> {
                NotificationHelper.notifyDaily(context)
                ReminderScheduler.scheduleDaily(context) // 다음 날 재예약
            }

            ReminderScheduler.ACTION_MIDNIGHT -> {
                WidgetUpdater.refreshAll(context)
                ReminderScheduler.scheduleMidnight(context)
            }

            ReminderScheduler.ACTION_WIDGET_RESET -> {
                // 3분 이상 안 만진 위젯을 '현재'로 되돌린다. (effectiveAnchor 가 알아서 판단)
                WidgetUpdater.refreshAll(context)
            }

            ReminderScheduler.ACTION_ENTRY -> {
                val id = intent.getLongExtra(ReminderScheduler.EXTRA_ENTRY_ID, -1L)
                if (id <= 0L) return
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val entry = AppDatabase.get(context).diaryDao().getById(id)
                        if (entry != null) NotificationHelper.notifyEntry(context, entry)
                    } finally {
                        pending.finish()
                    }
                }
            }

            ReminderScheduler.ACTION_BRIEF_DAILY,
            ReminderScheduler.ACTION_BRIEF_WEEKLY,
            ReminderScheduler.ACTION_BRIEF_MONTHLY -> {
                val action = intent.action!!
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        buildAndNotifyBriefing(context, action)
                    } finally {
                        // 다음 회차 재예약 후 종료
                        when (action) {
                            ReminderScheduler.ACTION_BRIEF_DAILY -> ReminderScheduler.scheduleBriefDaily(context)
                            ReminderScheduler.ACTION_BRIEF_WEEKLY -> ReminderScheduler.scheduleBriefWeekly(context)
                            ReminderScheduler.ACTION_BRIEF_MONTHLY -> ReminderScheduler.scheduleBriefMonthly(context)
                        }
                        pending.finish()
                    }
                }
            }
        }
    }

    private fun buildAndNotifyBriefing(c: Context, action: String) {
        val dao = AppDatabase.get(c).diaryDao()
        val today = DateUtil.today()
        val entries: List<DiaryEntry>
        val title: String
        val datePrefix: Boolean
        val notifId: Int
        when (action) {
            ReminderScheduler.ACTION_BRIEF_WEEKLY -> {
                val ws = DateUtil.weekStart(today); val we = ws + 6
                entries = dao.getOverlappingSync(ws, we)
                title = "이번 주 일기 (${DateUtil.formatShortDate(ws)} ~ ${DateUtil.formatShortDate(we)})"
                datePrefix = true; notifId = NotificationHelper.ID_BRIEF_WEEKLY
            }
            ReminderScheduler.ACTION_BRIEF_MONTHLY -> {
                val fm = DateUtil.firstOfMonthOf(today)
                val lm = fm + DateUtil.toDate(fm).lengthOfMonth() - 1
                entries = dao.getOverlappingSync(fm, lm)
                title = "이번 달 일기 (${DateUtil.formatMonthTitle(fm)})"
                datePrefix = true; notifId = NotificationHelper.ID_BRIEF_MONTHLY
            }
            else -> {
                entries = dao.getForDaySync(today)
                title = "오늘 일기 (${DateUtil.formatShortDate(today)})"
                datePrefix = false; notifId = NotificationHelper.ID_BRIEF_DAILY
            }
        }

        val sorted = entries.sortedWith(
            compareBy({ it.dateEpochDay }, { if (it.timeMinutes < 0) 9999 else it.timeMinutes })
        )
        val body: String = if (sorted.isEmpty()) {
            "아직 기록이 없어요."
        } else {
            val max = 20
            val lines = sorted.take(max).map { e ->
                val t = Stickers.strip(e.title).ifBlank {
                    Stickers.strip(e.content).take(18).ifBlank { "(제목 없음)" }
                }
                if (datePrefix) "• ${DateUtil.formatShortDate(e.dateEpochDay)}  $t" else "• $t"
            }.toMutableList()
            if (sorted.size > max) lines.add("… 외 ${sorted.size - max}건")
            lines.joinToString("\n")
        }
        NotificationHelper.notifyBriefing(c, notifId, title, body)
    }
}
