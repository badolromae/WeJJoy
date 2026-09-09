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
        "둘이A" to listOf(
            "ac_01" to "사랑해", "ac_02" to "안녕", "ac_03" to "최고", "ac_04" to "삐짐", "ac_05" to "미안", "ac_06" to "화이팅", "ac_07" to "감동", "ac_08" to "기다려", "ac_09" to "심쿵", "ac_10" to "잘자", "ac_11" to "맘마", "ac_12" to "놀람", "ac_13" to "분노", "ac_14" to "부끄", "ac_15" to "흥", "ac_16" to "헉", "ac_17" to "신나", "ac_18" to "메롱"
        ),
        "아내A" to listOf(
            "aw_01" to "사랑해", "aw_02" to "기다려", "aw_03" to "화이팅", "aw_04" to "안녕", "aw_05" to "미안", "aw_06" to "심쿵", "aw_07" to "잘자", "aw_08" to "맘마", "aw_09" to "최고", "aw_10" to "감동", "aw_11" to "헉", "aw_12" to "놀람", "aw_13" to "부끄", "aw_14" to "새침", "aw_15" to "신나", "aw_16" to "졸림", "aw_17" to "흥", "aw_18" to "삐짐", "aw_19" to "분노", "aw_20" to "축하", "aw_21" to "메롱"
        ),
        "남편A" to listOf(
            "ah_01" to "사랑해", "ah_02" to "기다려", "ah_03" to "화이팅", "ah_04" to "안녕", "ah_05" to "미안", "ah_06" to "심쿵", "ah_07" to "무표정", "ah_08" to "잘자", "ah_09" to "굿", "ah_10" to "맘마", "ah_11" to "최고", "ah_12" to "감동", "ah_13" to "헉", "ah_14" to "부끄", "ah_15" to "신나", "ah_16" to "졸림", "ah_17" to "흥", "ah_18" to "삐짐", "ah_19" to "분노", "ah_20" to "축하", "ah_21" to "메롱"
        ),
        "둘이B" to listOf(
            "bc_01" to "안녕", "bc_02" to "사랑해", "bc_03" to "최고야", "bc_04" to "보고싶어", "bc_05" to "배고파", "bc_06" to "헉", "bc_07" to "놀람", "bc_08" to "고마워", "bc_09" to "미안해", "bc_10" to "잘자", "bc_11" to "좋은아침", "bc_12" to "뭐먹지", "bc_13" to "같이가자", "bc_14" to "기다려줘", "bc_15" to "힘내", "bc_16" to "축하해", "bc_17" to "우리부부", "bc_18" to "꼭안아줘", "bc_19" to "삐졌어", "bc_20" to "행복해", "bc_21" to "뽀뽀"
        ),
        "아내B" to listOf(
            "bw_01" to "안녕", "bw_02" to "사랑해", "bw_03" to "신나요", "bw_04" to "감동이야", "bw_05" to "헤헤", "bw_06" to "메롱", "bw_07" to "삐짐", "bw_08" to "풀죽", "bw_09" to "울지마", "bw_10" to "졸려", "bw_11" to "배고파", "bw_12" to "최고", "bw_13" to "부끄러워", "bw_14" to "기다려", "bw_15" to "고마워", "bw_16" to "미안해", "bw_17" to "보고싶어", "bw_18" to "화났어", "bw_19" to "행복해", "bw_20" to "잘자", "bw_21" to "뿅"
        ),
        "남편B" to listOf(
            "bh_01" to "안녕", "bh_02" to "사랑해", "bh_03" to "든든하지", "bh_04" to "헉", "bh_05" to "최고야", "bh_06" to "배고파", "bh_07" to "밥줘", "bh_08" to "졸려", "bh_09" to "힘내", "bh_10" to "걱정마", "bh_11" to "미안해", "bh_12" to "고마워", "bh_13" to "보고싶어", "bh_14" to "삐졌어", "bh_15" to "화해하자", "bh_16" to "기다릴게", "bh_17" to "출발", "bh_18" to "잘자", "bh_19" to "좋은아침", "bh_20" to "아내최고", "bh_21" to "뽀뽀"
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

    private val scaledCache = HashMap<String, Bitmap?>()

    /** 위젯 등 RemoteViews 용으로 작게 줄인 비트맵 (name@px 캐시) */
    fun bitmapScaled(ctx: Context, name: String, px: Int): Bitmap? {
        val key = "$name@$px"
        if (scaledCache.containsKey(key)) return scaledCache[key]
        val base = bitmap(ctx, name)
        val out = if (base == null) null else try {
            Bitmap.createScaledBitmap(base, px, px, true)
        } catch (t: Throwable) { base }
        scaledCache[key] = out
        return out
    }

    /** "[[s:이름]]" 토큰을 글에서 제거하고 공백을 정리한다. (이미지 못 넣는 위젯 텍스트용) */
    fun strip(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        var s = TOKEN.matcher(text).replaceAll("")
        s = s.replace(Regex("[ \\t]{2,}"), " ").replace(Regex("\\n{2,}"), "\n")
        return s.trim()
    }

    /** 그 일기의 대표 이모티콘 이름: entry.sticker 우선, 없으면 제목/내용 속 첫 이모티콘. */
    fun repName(sticker: String?, title: String?, content: String?): String {
        if (!sticker.isNullOrEmpty() && sticker in ALL) return sticker
        val a = firstInline(title); if (a.isNotEmpty()) return a
        return firstInline(content)
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

    /** "[[s:이름]]" 토큰을 글자 크기의 [scale]배 인라인 그림으로 바꾼다. (토큰 글자는 그림 뒤에 가려짐) */
    fun applyInline(ctx: Context, text: CharSequence?, textSizePx: Float, scale: Float = 3.0f): CharSequence {
        val src = text?.toString() ?: return ""
        if (!src.contains("[[s:")) return src
        val sb = SpannableStringBuilder(src)
        val m = TOKEN.matcher(src)
        val size = (textSizePx * scale).toInt().coerceAtLeast(1)
        while (m.find()) {
            val name = m.group(1) ?: continue
            val d = drawable(ctx, name, size) ?: continue
            sb.setSpan(ImageSpan(d, ImageSpan.ALIGN_BOTTOM), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }
}
