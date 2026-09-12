package com.jooshin.diary.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.jooshin.diary.R
import com.jooshin.diary.util.DateUtil
import com.jooshin.diary.util.KoreanHolidays
import com.jooshin.diary.util.LunarCalendar
import com.jooshin.diary.util.Palette

class DayWidgetProvider : BaseCalendarWidget() {

    override val collectionViewId = R.id.day_list

    override fun defaultAnchor(): Long = DateUtil.today()

    override fun step(anchor: Long, dir: Int): Long = anchor + dir

    override fun render(c: Context, mgr: AppWidgetManager, id: Int) {
        val anchor = WidgetState.effectiveAnchor(c, id, defaultAnchor())
        val p = Palette.of(c)
        val views = RemoteViews(c.packageName, R.layout.widget_day)
        views.setInt(R.id.widget_root, "setBackgroundResource", p.widgetBgRes)
        views.setTextColor(R.id.day_empty, p.textMuted)
        // 상단바(날짜 표시부)를 특징색으로 진하게, 글씨/아이콘은 반대색으로
        views.setInt(R.id.header_bar, "setBackgroundColor", p.accent)
        views.setInt(R.id.btn_add, "setColorFilter", p.onAccent)
        for (b in intArrayOf(R.id.btn_today, R.id.btn_prev, R.id.btn_next)) {
            views.setInt(b, "setColorFilter", p.onAccent)
        }
        views.setTextViewText(R.id.day_title, DateUtil.formatFullDate(anchor))

        val info = KoreanHolidays.info(anchor)
        // 특징색 바탕 위라 요일색(빨강/파랑) 대신 잘 보이는 반대색으로
        views.setTextColor(R.id.day_title, p.onAccent)
        val sub = listOf(LunarCalendar.shortLabel(anchor), info.full)
            .filter { it.isNotEmpty() }.joinToString("  ·  ")
        views.setTextViewText(R.id.day_sub, sub)
        views.setTextColor(R.id.day_sub, p.onAccentDim)

        views.setOnClickPendingIntent(R.id.btn_prev, WidgetCommon.navPendingIntent(c, javaClass, WidgetCommon.ACTION_PREV, id))
        views.setOnClickPendingIntent(R.id.btn_next, WidgetCommon.navPendingIntent(c, javaClass, WidgetCommon.ACTION_NEXT, id))
        views.setOnClickPendingIntent(R.id.btn_today, WidgetCommon.navPendingIntent(c, javaClass, WidgetCommon.ACTION_TODAY, id))
        views.setOnClickPendingIntent(R.id.btn_add, WidgetCommon.openForDate(c, anchor, true, id))
        views.setOnClickPendingIntent(R.id.day_title, WidgetCommon.openForDate(c, anchor, false, id))

        val svc = Intent(c, DayWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            data = Uri.parse("diary://widget/day/$id")
        }
        views.setRemoteAdapter(R.id.day_list, svc)
        views.setEmptyView(R.id.day_list, R.id.day_empty)
        views.setPendingIntentTemplate(R.id.day_list, WidgetCommon.clickTemplate(c, "day", id))

        mgr.updateAppWidget(id, views)
        mgr.notifyAppWidgetViewDataChanged(id, R.id.day_list)
    }
}
