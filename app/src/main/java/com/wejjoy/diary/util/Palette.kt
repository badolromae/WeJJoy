package com.wejjoy.diary.util

import android.content.Context
import androidx.core.content.ContextCompat
import com.wejjoy.diary.R

/** 설정에서 고를 수 있는 디자인(팔레트) 8종. 앱과 위젯이 함께 바뀐다. */
enum class AppTheme(val key: String, val label: String, val styleRes: Int) {
    GREEN("green", "딥그린", R.style.Theme_WeJJoy_Green),
    BLUE("blue", "스카이블루", R.style.Theme_WeJJoy_Blue),
    PINK("pink", "연핑크", R.style.Theme_WeJJoy_Pink),
    MONO("mono", "블랙 + 그레이", R.style.Theme_WeJJoy_Mono),
    PURPLE("purple", "라벤더 퍼플", R.style.Theme_WeJJoy_Purple),
    SUNSET("sunset", "선셋 오렌지", R.style.Theme_WeJJoy_Sunset),
    DARK("dark", "다크 그린 (고정 어두움)", R.style.Theme_WeJJoy_Dark),
    MIDNIGHT("midnight", "미드나잇 (고정 어두움)", R.style.Theme_WeJJoy_Midnight);

    companion object {
        fun of(key: String?): AppTheme = values().firstOrNull { it.key == key } ?: GREEN
    }
}

/**
 * 선택된 팔레트의 실제 색상값.
 *
 * 앱 화면은 테마 속성(?attr/...)으로 자동 적용되지만,
 * 위젯(RemoteViews)과 직접 그리는 달력 뷰는 테마를 쓸 수 없어서 여기서 색을 가져온다.
 */
class Palette(private val ctx: Context, val theme: AppTheme) {

    private class Ids(
        val toolbar: Int, val accent: Int, val bg: Int, val surface: Int, val section: Int,
        val stroke: Int, val textPrimary: Int, val textSecondary: Int, val textMuted: Int,
        val divider: Int, val onSelected: Int, val calNormal: Int, val calOutside: Int,
        val calLunar: Int, val calGrid: Int, val todayRing: Int,
        val widgetBg: Int, val widgetSection: Int, val todayCell: Int
    )

    private val ids: Ids = when (theme) {
        AppTheme.GREEN -> Ids(
            R.color.pg_toolbar, R.color.pg_accent, R.color.pg_bg, R.color.pg_surface, R.color.pg_section,
            R.color.pg_stroke, R.color.pg_text_primary, R.color.pg_text_secondary, R.color.pg_text_muted,
            R.color.pg_divider, R.color.pg_on_selected, R.color.pg_cal_normal, R.color.pg_cal_outside,
            R.color.pg_cal_lunar, R.color.pg_cal_grid, R.color.pg_today_ring,
            R.drawable.widget_bg_pg, R.drawable.widget_section_bg_pg, R.drawable.bg_widget_today_cell_pg
        )
        AppTheme.BLUE -> Ids(
            R.color.pb_toolbar, R.color.pb_accent, R.color.pb_bg, R.color.pb_surface, R.color.pb_section,
            R.color.pb_stroke, R.color.pb_text_primary, R.color.pb_text_secondary, R.color.pb_text_muted,
            R.color.pb_divider, R.color.pb_on_selected, R.color.pb_cal_normal, R.color.pb_cal_outside,
            R.color.pb_cal_lunar, R.color.pb_cal_grid, R.color.pb_today_ring,
            R.drawable.widget_bg_pb, R.drawable.widget_section_bg_pb, R.drawable.bg_widget_today_cell_pb
        )
        AppTheme.PINK -> Ids(
            R.color.pp_toolbar, R.color.pp_accent, R.color.pp_bg, R.color.pp_surface, R.color.pp_section,
            R.color.pp_stroke, R.color.pp_text_primary, R.color.pp_text_secondary, R.color.pp_text_muted,
            R.color.pp_divider, R.color.pp_on_selected, R.color.pp_cal_normal, R.color.pp_cal_outside,
            R.color.pp_cal_lunar, R.color.pp_cal_grid, R.color.pp_today_ring,
            R.drawable.widget_bg_pp, R.drawable.widget_section_bg_pp, R.drawable.bg_widget_today_cell_pp
        )
        AppTheme.MONO -> Ids(
            R.color.pm_toolbar, R.color.pm_accent, R.color.pm_bg, R.color.pm_surface, R.color.pm_section,
            R.color.pm_stroke, R.color.pm_text_primary, R.color.pm_text_secondary, R.color.pm_text_muted,
            R.color.pm_divider, R.color.pm_on_selected, R.color.pm_cal_normal, R.color.pm_cal_outside,
            R.color.pm_cal_lunar, R.color.pm_cal_grid, R.color.pm_today_ring,
            R.drawable.widget_bg_pm, R.drawable.widget_section_bg_pm, R.drawable.bg_widget_today_cell_pm
        )
        AppTheme.PURPLE -> Ids(
            R.color.pa_toolbar, R.color.pa_accent, R.color.pa_bg, R.color.pa_surface, R.color.pa_section,
            R.color.pa_stroke, R.color.pa_text_primary, R.color.pa_text_secondary, R.color.pa_text_muted,
            R.color.pa_divider, R.color.pa_on_selected, R.color.pa_cal_normal, R.color.pa_cal_outside,
            R.color.pa_cal_lunar, R.color.pa_cal_grid, R.color.pa_today_ring,
            R.drawable.widget_bg_pa, R.drawable.widget_section_bg_pa, R.drawable.bg_widget_today_cell_pa
        )
        AppTheme.SUNSET -> Ids(
            R.color.pu_toolbar, R.color.pu_accent, R.color.pu_bg, R.color.pu_surface, R.color.pu_section,
            R.color.pu_stroke, R.color.pu_text_primary, R.color.pu_text_secondary, R.color.pu_text_muted,
            R.color.pu_divider, R.color.pu_on_selected, R.color.pu_cal_normal, R.color.pu_cal_outside,
            R.color.pu_cal_lunar, R.color.pu_cal_grid, R.color.pu_today_ring,
            R.drawable.widget_bg_pu, R.drawable.widget_section_bg_pu, R.drawable.bg_widget_today_cell_pu
        )
        AppTheme.DARK -> Ids(
            R.color.pd_toolbar, R.color.pd_accent, R.color.pd_bg, R.color.pd_surface, R.color.pd_section,
            R.color.pd_stroke, R.color.pd_text_primary, R.color.pd_text_secondary, R.color.pd_text_muted,
            R.color.pd_divider, R.color.pd_on_selected, R.color.pd_cal_normal, R.color.pd_cal_outside,
            R.color.pd_cal_lunar, R.color.pd_cal_grid, R.color.pd_today_ring,
            R.drawable.widget_bg_pd, R.drawable.widget_section_bg_pd, R.drawable.bg_widget_today_cell_pd
        )
        AppTheme.MIDNIGHT -> Ids(
            R.color.pe_toolbar, R.color.pe_accent, R.color.pe_bg, R.color.pe_surface, R.color.pe_section,
            R.color.pe_stroke, R.color.pe_text_primary, R.color.pe_text_secondary, R.color.pe_text_muted,
            R.color.pe_divider, R.color.pe_on_selected, R.color.pe_cal_normal, R.color.pe_cal_outside,
            R.color.pe_cal_lunar, R.color.pe_cal_grid, R.color.pe_today_ring,
            R.drawable.widget_bg_pe, R.drawable.widget_section_bg_pe, R.drawable.bg_widget_today_cell_pe
        )
    }

    private fun c(id: Int) = ContextCompat.getColor(ctx, id)

    val toolbar = c(ids.toolbar)
    val accent = c(ids.accent)
    val bg = c(ids.bg)
    val surface = c(ids.surface)
    val section = c(ids.section)
    val stroke = c(ids.stroke)
    val textPrimary = c(ids.textPrimary)
    val textSecondary = c(ids.textSecondary)
    val textMuted = c(ids.textMuted)
    val divider = c(ids.divider)
    val onSelected = c(ids.onSelected)
    val calNormal = c(ids.calNormal)
    val calOutside = c(ids.calOutside)
    val calLunar = c(ids.calLunar)
    val calGrid = c(ids.calGrid)
    val todayRing = c(ids.todayRing)

    /** 팔레트와 상관없이 고정 (달력 관례) */
    val sun = c(R.color.cal_sun)
    val sat = c(R.color.cal_sat)

    /** 위젯 배경/구역/오늘칸 그림 리소스 */
    val widgetBgRes = ids.widgetBg
    val widgetSectionRes = ids.widgetSection
    val todayCellRes = ids.todayCell

    companion object {
        /** 현재 설정된 팔레트로 만든다. */
        fun of(context: Context) = Palette(context, Prefs.appTheme(context))
    }
}
