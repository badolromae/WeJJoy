package com.jooshin.diary.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.jooshin.diary.databinding.ActivityGroupBinding
import com.jooshin.diary.sync.FirebaseConfig
import com.jooshin.diary.sync.Member
import com.jooshin.diary.sync.SyncManager
import com.jooshin.diary.util.Prefs
import com.jooshin.diary.widget.WidgetUpdater

/**
 * 공유 그룹 화면.
 *
 * - 관리자: 그룹을 만들고, 초대 코드를 알려주고, 공유자를 내보낼 수 있다.
 * - 공유자: 초대 코드로 참여한다.
 */
class GroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupBinding
    private var adapter: MemberAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Prefs.appTheme(this).styleRes)
        super.onCreate(savedInstanceState)
        binding = ActivityGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarGroup)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarGroup.setNavigationOnClickListener { finish() }

        binding.etNick.setText(Prefs.myNick(this))

        binding.btnCreate.setOnClickListener { createGroup() }
        binding.btnJoin.setOnClickListener { joinGroup() }
        binding.btnShare.setOnClickListener { shareCode() }
        binding.btnLeave.setOnClickListener { confirmLeave() }

        render()
    }

    override fun onDestroy() {
        SyncManager.stopObservingMembers()
        super.onDestroy()
    }

    // ------------------------------------------------------------------

    private fun render() {
        val configured = FirebaseConfig.isFilled()
        binding.cardSetup.visibility = if (configured) View.GONE else View.VISIBLE
        if (!configured) {
            binding.tvSetupHint.text =
                "아직 공유 서버가 연결되지 않았습니다.\n\n" +
                    "저장소의 FirebaseConfig.kt 파일에 파이어베이스 값 4개를 넣고 다시 빌드하면 " +
                    "공유 기능이 켜집니다. 자세한 순서는 저장소의 " +
                    "'파이어베이스-설정방법.md' 를 참고하세요.\n\n" +
                    "설정하지 않아도 혼자 쓰는 다이어리로는 문제없이 동작합니다."
            binding.groupNone.visibility = View.GONE
            binding.groupJoined.visibility = View.GONE
            return
        }

        val inGroup = Prefs.isInGroup(this)
        binding.groupNone.visibility = if (inGroup) View.GONE else View.VISIBLE
        binding.groupJoined.visibility = if (inGroup) View.VISIBLE else View.GONE
        binding.tvStatus.text = SyncManager.status

        if (!inGroup) return

        binding.tvCode.text = Prefs.groupCode(this)
        val owner = Prefs.isOwner(this)
        binding.tvRoleHint.text =
            if (owner) "나는 관리자입니다. 공유자를 내보낼 수 있어요."
            else "나는 공유자입니다. 관리자가 그룹을 관리합니다."

        val a = MemberAdapter(
            amOwner = owner,
            myUid = Prefs.myUid(this),
            onRemove = { confirmRemove(it) },
            onAllow = { confirmAllow(it) }
        )
        adapter = a
        binding.recyclerMembers.layoutManager = LinearLayoutManager(this)
        binding.recyclerMembers.adapter = a

        SyncManager.observeMembers(this) { list ->
            runOnUiThread {
                if (!Prefs.isInGroup(this)) {
                    toast("관리자가 공유를 해제했습니다.")
                    render()
                    return@runOnUiThread
                }
                a.submit(list)
                binding.tvMembersEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                binding.tvMembersEmpty.text = "아직 참여한 사람이 없습니다."
                binding.tvStatus.text = SyncManager.status
            }
        }
    }

    private fun nickOrWarn(): String? {
        val nick = binding.etNick.text?.toString()?.trim() ?: ""
        if (nick.isEmpty()) {
            toast("먼저 내 별명을 입력하세요.")
            return null
        }
        return nick
    }

    private fun createGroup() {
        val nick = nickOrWarn() ?: return
        binding.btnCreate.isEnabled = false
        binding.tvStatus.text = "그룹을 만드는 중…"
        SyncManager.createGroup(this, nick) { code, err ->
            runOnUiThread {
                binding.btnCreate.isEnabled = true
                if (code == null) {
                    binding.tvStatus.text = err ?: "실패했습니다."
                    toast(err ?: "그룹을 만들지 못했습니다.")
                } else {
                    WidgetUpdater.refreshAll(this)
                    toast("그룹이 만들어졌습니다: $code")
                    render()
                }
            }
        }
    }

    private fun joinGroup() {
        val nick = nickOrWarn() ?: return
        val code = binding.etCode.text?.toString()?.trim()?.uppercase() ?: ""
        if (code.isEmpty()) {
            toast("초대 코드를 입력하세요."); return
        }
        binding.btnJoin.isEnabled = false
        binding.tvStatus.text = "참여하는 중…"
        SyncManager.joinGroup(this, code, nick) { err ->
            runOnUiThread {
                binding.btnJoin.isEnabled = true
                if (err != null) {
                    binding.tvStatus.text = err
                    toast(err)
                } else {
                    WidgetUpdater.refreshAll(this)
                    toast("그룹에 참여했습니다.")
                    render()
                }
            }
        }
    }

    private fun shareCode() {
        val code = Prefs.groupCode(this)
        val msg = "WeJJoy 다이어리 공유 초대\n초대 코드: $code\n\n" +
            "앱 설정 ▸ 공유 그룹 ▸ '초대 코드로 참여' 에 이 코드를 넣으면 같은 일정을 함께 볼 수 있어요."
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, msg)
                },
                "초대 코드 보내기"
            )
        )
    }

    private fun confirmRemove(m: Member) {
        AlertDialog.Builder(this)
            .setTitle("공유 해제")
            .setMessage(
                "'${m.nick}' 님을 그룹에서 내보낼까요?\n\n" +
                    "그 사람 폰에서는 더 이상 새 내용이 오지 않고, 초대 코드를 다시 넣어도 들어올 수 없습니다.\n" +
                    "(나중에 목록에서 '다시 허용' 을 누르면 되돌릴 수 있습니다)"
            )
            .setNegativeButton("취소", null)
            .setPositiveButton("내보내기") { _, _ ->
                SyncManager.removeMember(this, m.uid, m.nick) { err ->
                    runOnUiThread { toast(err ?: "내보냈습니다.") }
                }
            }
            .show()
    }

    private fun confirmAllow(m: Member) {
        AlertDialog.Builder(this)
            .setTitle("다시 허용")
            .setMessage("'${m.nick}' 님이 초대 코드로 다시 들어올 수 있게 할까요?")
            .setNegativeButton("취소", null)
            .setPositiveButton("허용") { _, _ ->
                SyncManager.allowMember(this, m.uid) { err ->
                    runOnUiThread { toast(err ?: "이제 다시 참여할 수 있습니다.") }
                }
            }
            .show()
    }

    private fun confirmLeave() {
        AlertDialog.Builder(this)
            .setTitle("그룹 나가기")
            .setMessage("공유를 그만둘까요?\n지금까지 받은 기록은 내 폰에 그대로 남습니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("나가기") { _, _ ->
                SyncManager.leaveGroup(this)
                toast("그룹에서 나왔습니다.")
                render()
            }
            .show()
    }

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
