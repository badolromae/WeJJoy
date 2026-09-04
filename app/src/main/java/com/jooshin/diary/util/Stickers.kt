package com.jooshin.diary.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import java.util.regex.Pattern

/**
 * 커플 이모티콘(60종) 도우미. 웹과 같은 그림/이름을 쓴다.
 * 그림 파일은 assets/stickers/<이름>.png 로 앱에 내장돼 있다.
 *
 * - 일기의 '대표 이모티콘'(entry.sticker)은 목록/달력에 그림으로 표시
 * - 제목·내용 글 속의 "[[s:이름]]" 토큰은 [applyInline] 으로 인라인 그림으로 바뀐다 (웹과 동일)
 */
object Stickers {

    /** (그룹이름, [(파일이름, 라벨)...]) — 웹의 STICKER_GROUPS 와 동일 */
    val GROUPS: List<Pair<String, List<Pair<String, String>>>> = listOf(
        "2인" to listOf(
            "couple_01_love_hug" to "사랑", "couple_02_kiss" to "뽀뽀", "couple_03_happy" to "행복",
            "couple_04_cheer" to "화이팅", "couple_05_love_sign" to "사랑해", "couple_06_thanks" to "고마워",
            "couple_07_sorry" to "미안해", "couple_08_celebrate" to "축하", "couple_09_flowers" to "꽃선물",
            "couple_10_date" to "데이트", "couple_11_coffee" to "커피", "couple_12_yummy" to "맛있다",
            "couple_13_goodmorning" to "굿모닝", "couple_14_goodnight" to "잘자", "couple_15_sulk" to "삐짐",
            "couple_16_cry" to "위로", "couple_17_tired" to "피곤", "couple_18_sick" to "아파요",
            "couple_19_thumbs" to "최고", "couple_20_bye" to "바이바이"
        ),
        "남편" to listOf(
            "h_01_run" to "달려갈게", "h_02_jump" to "점프", "h_03_wave" to "안녕", "h_04_finger_heart" to "손하트",
            "h_05_blowkiss" to "뽀뽀날림", "h_06_cheer" to "화이팅", "h_07_dance" to "춤", "h_08_cry" to "엉엉",
            "h_09_angry" to "화남", "h_10_love" to "사랑", "h_11_thumbs" to "최고", "h_12_wink" to "윙크",
            "h_13_come" to "이리와", "h_14_tada" to "짜잔", "h_15_stretch" to "기지개", "h_16_phone" to "전화해",
            "h_17_surprise" to "깜짝", "h_18_think" to "생각중", "h_19_sadwalk" to "축쳐짐", "h_20_gift" to "선물"
        ),
        "아내" to listOf(
            "w_01_run" to "달려갈게", "w_02_jump" to "점프", "w_03_wave" to "안녕", "w_04_finger_heart" to "손하트",
            "w_05_blowkiss" to "뽀뽀날림", "w_06_cheer" to "화이팅", "w_07_dance" to "춤", "w_08_cry" to "엉엉",
            "w_09_angry" to "화남", "w_10_love" to "사랑", "w_11_thumbs" to "최고", "w_12_wink" to "윙크",
            "w_13_come" to "이리와", "w_14_tada" to "짜잔", "w_15_stretch" to "기지개", "w_16_phone" to "전화해",
            "w_17_surprise" to "깜짝", "w_18_think" to "생각중", "w_19_sadwalk" to "축쳐짐", "w_20_gift" to "선물"
        )
    )

    private val ALL: Set<String> by lazy { GROUPS.flatMap { g -> g.second.map { it.first } }.toSet() }
    private val cache = HashMap<String, Bitmap?>()
    private val TOKEN: Pattern = Pattern.compile("\\[\\[s:([a-z0-9_]+)\\]\\]")

    fun isValid(name: String): Boolean = name in ALL

    /** assets 에서 비트맵을 (한 번만) 읽어 캐시한다. */
    fun bitmap(ctx: Context, name: String): Bitmap? {
        if (name.isEmpty() || name !in ALL) return null
        if (cache.containsKey(name)) return cache[name]
        val bmp = try {
            ctx.applicationContext.assets.open("stickers/$name.png").use { BitmapFactory.decodeStream(it) }
        } catch (t: Throwable) {
            null
        }
        cache[name] = bmp
        return bmp
    }

    fun drawable(ctx: Context, name: String, sizePx: Int): Drawable? {
        val bmp = bitmap(ctx, name) ?: return null
        val d = BitmapDrawable(ctx.resources, bmp)
        val s = if (sizePx > 0) sizePx else bmp.width
        d.setBounds(0, 0, s, s)
        return d
    }

    /** 글 속 첫 번째 이모티콘 토큰의 이름 (없으면 "") — 달력 대표 이모티콘 계산용 */
    fun firstInline(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        val m = TOKEN.matcher(text)
        if (m.find()) {
            val n = m.group(1) ?: return ""
            if (n in ALL) return n
        }
        return ""
    }

    /** "[[s:이름]]" 토큰을 글자 크기의 약 1.5배 인라인 그림으로 바꾼다. (토큰 글자는 그림 뒤에 가려짐) */
    fun applyInline(ctx: Context, text: CharSequence?, textSizePx: Float): CharSequence {
        val src = text?.toString() ?: return ""
        if (!src.contains("[[s:")) return src
        val sb = SpannableStringBuilder(src)
        val m = TOKEN.matcher(src)
        val size = (textSizePx * 1.5f).toInt().coerceAtLeast(1)
        while (m.find()) {
            val name = m.group(1) ?: continue
            val d = drawable(ctx, name, size) ?: continue
            sb.setSpan(ImageSpan(d, ImageSpan.ALIGN_BOTTOM), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }
}
