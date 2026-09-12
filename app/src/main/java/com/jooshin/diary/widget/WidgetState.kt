package com.jooshin.diary.widget

import android.content.Context

/**
 * 위젯별 기준 날짜(anchor) 저장. 월=그 달 1일, 주=그 주 일요일, 일=해당 날짜 (epochDay).
 *
 * V3.6: 마지막으로 위젯을 만진 시각(lastTouch)도 저장한다. 3분 넘게 안 만지면
 * 저장된 anchor 를 무시하고 '현재(오늘/이번주/이번달)'로 되돌린다(effectiveAnchor).
 * 이러면 화면을 새로 그릴 때마다 오늘 기준으로 계산되므로 날짜가 바뀌면 위젯도 따라 이동한다.
 */
object WidgetState {
    private const val FILE = "widget_state"

    /** 이 시간 넘게 안 만지면 현재로 복귀 (밀리초) */
    const val IDLE_RESET_MS = 3 * 60 * 1000L

    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getAnchor(c: Context, id: Int, default: Long): Long =
        p(c).getLong("anchor_$id", default)

    fun setAnchor(c: Context, id: Int, value: Long) {
        p(c).edit()
            .putLong("anchor_$id", value)
            .putLong("touch_$id", System.currentTimeMillis())
            .apply()
    }

    fun lastTouch(c: Context, id: Int): Long = p(c).getLong("touch_$id", 0L)

    /**
     * 실제로 그릴 때 쓸 기준 날짜.
     * - 마지막 조작 후 3분이 지났으면(또는 만진 적 없으면) → default(현재)
     * - 3분 안이면 → 사용자가 이동해 둔 anchor
     */
    fun effectiveAnchor(c: Context, id: Int, default: Long): Long {
        val touched = lastTouch(c, id)
        val idle = touched <= 0L || (System.currentTimeMillis() - touched) > IDLE_RESET_MS
        return if (idle) default else getAnchor(c, id, default)
    }

    fun clear(c: Context, id: Int) {
        p(c).edit().remove("anchor_$id").remove("touch_$id").apply()
    }
}
