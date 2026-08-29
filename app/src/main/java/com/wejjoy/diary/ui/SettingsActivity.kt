package com.wejjoy.diary.ui

import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.lifecycle.lifecycleScope
import com.wejjoy.diary.R
import com.wejjoy.diary.databinding.ActivitySettingsBinding
import com.wejjoy.diary.notify.ReminderScheduler
import com.wejjoy.diary.sync.FirebaseSync
import com.wejjoy.diary.sync.GroupDoc
import com.wejjoy.diary.sync.JoinResult
import com.wejjoy.diary.util.AppTheme
import com.wejjoy.diary.util.DateUtil
import com.wejjoy.diary.util.Prefs
import com.wejjoy.diary.widget.WidgetUpdater
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val requestNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val pinSetupLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateSecurityControls()
        }

    private val dailyListener = CompoundButton.OnCheckedChangeListener { _, checked ->
        Prefs.setDailyEnabled(this, checked)
        binding.btnDailyTime.isEnabled = checked
        if (checked) {
            requestNotifIfNeeded()
            ensureExactAlarm()
        }
        ReminderScheduler.scheduleDaily(this)
    }

    private val lockListener = CompoundButton.OnCheckedChangeListener { _, checked ->
        if (checked) {
            if (!Prefs.hasPin(this)) {
                pinSetupLauncher.launch(Intent(this, PinSetupActivity::class.java))
            } else {
                Prefs.setLockEnabled(this, true)
                updateSecurityControls()
            }
        } else {
            Prefs.setLockEnabled(this, false)
            updateSecurityControls()
        }
    }

    private val bioListener = CompoundButton.OnCheckedChangeListener { _, checked ->
        Prefs.setBiometricEnabled(this, checked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Prefs.appTheme(this).styleRes)
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarSettings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarSettings.setNavigationOnClickListener { finish() }

        // ── 디자인(팔레트 8종) ──
        when (Prefs.appTheme(this)) {
            AppTheme.BLUE -> binding.rbThemeBlue.isChecked = true
            AppTheme.PINK -> binding.rbThemePink.isChecked = true
            AppTheme.MONO -> binding.rbThemeMono.isChecked = true
            AppTheme.PURPLE -> binding.rbThemePurple.isChecked = true
            AppTheme.SUNSET -> binding.rbThemeSunset.isChecked = true
            AppTheme.DARK -> binding.rbThemeDark.isChecked = true
            AppTheme.MIDNIGHT -> binding.rbThemeMidnight.isChecked = true
            else -> binding.rbThemeGreen.isChecked = true
        }
        binding.radioTheme.setOnCheckedChangeListener { _, id ->
            val t = when (id) {
                R.id.rbThemeBlue -> AppTheme.BLUE
                R.id.rbThemePink -> AppTheme.PINK
                R.id.rbThemeMono -> AppTheme.MONO
                R.id.rbThemePurple -> AppTheme.PURPLE
                R.id.rbThemeSunset -> AppTheme.SUNSET
                R.id.rbThemeDark -> AppTheme.DARK
                R.id.rbThemeMidnight -> AppTheme.MIDNIGHT
                else -> AppTheme.GREEN
            }
            if (t != Prefs.appTheme(this)) {
                Prefs.setAppTheme(this, t)
                WidgetUpdater.refreshAll(this)   // 위젯도 같이 바꾼다
                recreate()                        // 설정 화면 즉시 반영
            }
        }

        // ── 알림 ──
        binding.switchDaily.isChecked = Prefs.isDailyEnabled(this)
        binding.btnDailyTime.isEnabled = Prefs.isDailyEnabled(this)
        updateDailyTimeText()
        binding.switchDaily.setOnCheckedChangeListener(dailyListener)
        binding.btnDailyTime.setOnClickListener { pickDailyTime() }

        when (Prefs.notifyStyle(this)) {
            Prefs.STYLE_SOUND -> binding.rbSound.isChecked = true
            Prefs.STYLE_VIBRATE -> binding.rbVibrate.isChecked = true
            Prefs.STYLE_SILENT -> binding.rbSilent.isChecked = true
            else -> binding.rbBoth.isChecked = true
        }
        binding.radioNotifyStyle.setOnCheckedChangeListener { _, checkedId ->
            val style = when (checkedId) {
                R.id.rbSound -> Prefs.STYLE_SOUND
                R.id.rbVibrate -> Prefs.STYLE_VIBRATE
                R.id.rbSilent -> Prefs.STYLE_SILENT
                else -> Prefs.STYLE_BOTH
            }
            Prefs.setNotifyStyle(this, style)
        }

        // ── 보안 ──
        binding.btnChangePin.setOnClickListener {
            pinSetupLauncher.launch(Intent(this, PinSetupActivity::class.java))
        }
        updateSecurityControls()

        // ── 공유 다이어리 ──
        setupShareSection()

        binding.tvVersion.text = "WeJJoy v1.0 · 함께 쓰는 다이어리"
    }

    // ═══════════════════════ 공유 다이어리 ═══════════════════════

    private fun setupShareSection() {
        if (!FirebaseSync.isConfigured) {
            binding.tvShareStatus.text =
                "Firebase 설정 파일(google-services.json)이 앱에 포함되지 않아 공유 기능이 꺼져 있습니다.\n" +
                "README의 'Firebase 설정'을 따라 파일을 추가한 APK를 설치하면 사용할 수 있습니다."
            binding.btnCreateGroup.visibility = View.GONE
            binding.btnJoinGroup.visibility = View.GONE
            binding.etShareNickname.visibility = View.GONE
            binding.etJoinCode.visibility = View.GONE
            binding.layoutShareConnected.visibility = View.GONE
            return
        }
        refreshShareUI()
        // 그룹 문서 실시간 감시 → 회원 목록 자동 갱신
        lifecycleScope.launch {
            FirebaseSync.groupState.collect { renderShareState(it) }
        }

        binding.btnCreateGroup.setOnClickListener {
            val nick = binding.etShareNickname.text.toString().trim()
            if (nick.isEmpty()) { toast("별명을 입력해 주세요"); return@setOnClickListener }
            setShareButtons(false)
            lifecycleScope.launch {
                when (val r = FirebaseSync.createGroup(this@SettingsActivity, nick)) {
                    is JoinResult.Success -> toast("그룹을 만들었습니다. 초대 코드를 상대에게 알려주세요.")
                    is JoinResult.Error -> toast(r.message)
                }
                setShareButtons(true)
                refreshShareUI()
            }
        }
        binding.btnJoinGroup.setOnClickListener {
            val nick = binding.etShareNickname.text.toString().trim()
            val code = binding.etJoinCode.text.toString().trim()
            if (nick.isEmpty()) { toast("별명을 입력해 주세요"); return@setOnClickListener }
            if (code.length != 6) { toast("6자리 초대 코드를 입력해 주세요"); return@setOnClickListener }
            setShareButtons(false)
            lifecycleScope.launch {
                when (val r = FirebaseSync.joinGroup(this@SettingsActivity, code, nick)) {
                    is JoinResult.Success ->
                        toast("가입 신청 완료. 관리자가 승인하면 공유가 시작됩니다.")
                    is JoinResult.Error -> toast(r.message)
                }
                setShareButtons(true)
                refreshShareUI()
            }
        }
        binding.tvJoinCode.setOnClickListener {
            val code = Prefs.syncJoinCode(this)
            if (code.isNotEmpty()) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("join_code", code))
                toast("초대 코드가 복사되었습니다")
            }
        }
        binding.btnRegenCode.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val code = FirebaseSync.regenerateCode(this@SettingsActivity)
                    binding.tvJoinCode.text = "초대 코드: $code  (누르면 복사)"
                    toast("새 코드가 발급되었습니다")
                } catch (e: Exception) { toast(e.message ?: "실패") }
            }
        }
    }

    private fun setShareButtons(enabled: Boolean) {
        binding.btnCreateGroup.isEnabled = enabled
        binding.btnJoinGroup.isEnabled = enabled
    }

    private fun refreshShareUI() {
        val gid = Prefs.syncGroupId(this)
        val connected = !gid.isNullOrEmpty()
        binding.layoutShareJoin.visibility = if (connected) View.GONE else View.VISIBLE
        binding.layoutShareConnected.visibility = if (connected) View.VISIBLE else View.GONE
        if (connected) {
            val role = Prefs.syncRole(this)
            val roleText = if (role == Prefs.ROLE_ADMIN) "관리자" else "공유자"
            binding.tvShareStatus.text =
                if (Prefs.isSyncPending(this)) "관리자 승인 대기 중입니다…"
                else "연결됨 · 내 역할: $roleText · 별명: ${Prefs.syncNickname(this)}"
            binding.layoutAdminTools.visibility =
                if (role == Prefs.ROLE_ADMIN) View.VISIBLE else View.GONE
            val code = Prefs.syncJoinCode(this)
            binding.tvJoinCode.text =
                if (code.isNotEmpty()) "초대 코드: $code  (누르면 복사)" else ""
        } else {
            binding.tvShareStatus.text = "아직 그룹에 연결되지 않았습니다."
        }
    }

    /** 그룹 문서 실시간 변경 시 회원 목록 다시 그리기 */
    private fun renderShareState(g: GroupDoc?) {
        refreshShareUI()
        val container = binding.layoutMembers
        container.removeAllViews()
        if (g == null) return
        val myUid = FirebaseSync.currentUid
        val amAdmin = Prefs.syncRole(this) == Prefs.ROLE_ADMIN

        g.members.forEach { (uid, m) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            val label = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = buildString {
                    append(m.name.ifBlank { "(이름 없음)" })
                    if (m.role == "admin") append("  · 관리자")
                    if (uid == myUid) append("  (나)")
                    if (m.status == "pending") append("  · 승인 대기")
                }
                textSize = 14f
            }
            row.addView(label)

            if (amAdmin && uid != myUid) {
                if (m.status == "pending") {
                    row.addView(smallButton("승인") {
                        lifecycleScope.launch {
                            try { FirebaseSync.approveMember(gId(), uid); toast("${m.name} 승인 완료") }
                            catch (e: Exception) { toast(e.message ?: "실패") }
                        }
                    })
                }
                row.addView(smallButton("해제") {
                    AlertDialog.Builder(this)
                        .setTitle("공유 해제")
                        .setMessage("${m.name} 님을 그룹에서 제외할까요? 상대 기기에서는 더 이상 일기가 공유되지 않습니다.")
                        .setNegativeButton("취소", null)
                        .setPositiveButton("해제") { _, _ ->
                            lifecycleScope.launch {
                                try { FirebaseSync.removeMember(gId(), uid); toast("${m.name} 해제 완료") }
                                catch (e: Exception) { toast(e.message ?: "실패") }
                            }
                        }
                        .show()
                })
            }
            container.addView(row)
        }
        if (g.members.size <= 1) {
            container.addView(TextView(this).apply {
                text = "아직 공유자가 없습니다. 초대 코드를 상대에게 알려주세요."
                textSize = 13f
            })
        }
    }

    private fun gId(): String = Prefs.syncGroupId(this) ?: ""

    private fun smallButton(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            textSize = 12f
            setOnClickListener { onClick() }
        }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ═══════════════════════ 알림/보안 (기존) ═══════════════════════

    private fun updateDailyTimeText() {
        val minutes = Prefs.dailyHour(this) * 60 + Prefs.dailyMinute(this)
        binding.btnDailyTime.text = "매일 알림 시간: ${DateUtil.formatTime(minutes)}"
    }

    private fun pickDailyTime() {
        TimePickerDialog(
            this,
            { _, h, m ->
                Prefs.setDailyTime(this, h, m)
                updateDailyTimeText()
                ReminderScheduler.scheduleDaily(this)
            },
            Prefs.dailyHour(this), Prefs.dailyMinute(this), false
        ).show()
    }

    private fun updateSecurityControls() {
        val bioAvailable = isBiometricAvailable()
        val lockOn = Prefs.isLockEnabled(this) && Prefs.hasPin(this)

        binding.switchLock.setOnCheckedChangeListener(null)
        binding.switchLock.isChecked = lockOn
        binding.switchLock.setOnCheckedChangeListener(lockListener)

        binding.btnChangePin.isEnabled = lockOn

        binding.switchBiometric.setOnCheckedChangeListener(null)
        binding.switchBiometric.isChecked = Prefs.isBiometricEnabled(this) && bioAvailable
        binding.switchBiometric.isEnabled = lockOn && bioAvailable
        binding.switchBiometric.setOnCheckedChangeListener(bioListener)

        binding.tvBiometricHint.text =
            if (bioAvailable) "지문 또는 얼굴 인식으로도 잠금을 해제합니다."
            else "이 기기에서는 생체인식을 사용할 수 없습니다."
    }

    private fun isBiometricAvailable(): Boolean {
        val bm = BiometricManager.from(this)
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun requestNotifIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun ensureExactAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("정확한 알림 권한")
                    .setMessage("정해진 시간에 정확히 알림을 울리려면 '알람 및 리마인더' 권한이 필요합니다. 설정으로 이동할까요?")
                    .setNegativeButton("나중에", null)
                    .setPositiveButton("설정 열기") { _, _ ->
                        try {
                            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                        } catch (_: Exception) {
                        }
                    }
                    .show()
            }
        }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
