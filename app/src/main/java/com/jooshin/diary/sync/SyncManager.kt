package com.jooshin.diary.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.jooshin.diary.data.AppDatabase
import com.jooshin.diary.data.DiaryEntry
import com.jooshin.diary.notify.ReminderScheduler
import com.jooshin.diary.util.ImageStore
import com.jooshin.diary.util.Prefs
import com.jooshin.diary.widget.WidgetUpdater
import java.io.File
import java.util.concurrent.Executors

/** 공유 그룹 구성원 */
data class Member(
    val uid: String,
    val nick: String,
    val role: String,
    val joinedAt: Long
) {
    val isOwner: Boolean get() = role == ROLE_OWNER
    val isBanned: Boolean get() = role == ROLE_BANNED

    companion object {
        const val ROLE_OWNER = "owner"
        const val ROLE_MEMBER = "member"

        /** 관리자가 내보낸 사람. 목록 맨 아래에 '내보냄' 으로 표시된다. */
        const val ROLE_BANNED = "banned"
    }
}

/**
 * 공유(실시간 동기화) 담당.
 *
 * - 일기는 각자 폰의 데이터베이스에 그대로 저장된다. (인터넷이 끊겨도 앱은 평소처럼 동작)
 * - 그 위에서 파이어베이스가 "바뀐 내용만" 서로 주고받는다.
 * - 같은 일기인지는 [DiaryEntry.uid] 로 구분하고, 충돌하면 나중에 고친 쪽이 이긴다.
 *
 * 글·시간·태그·중요도·기분은 항상 공유된다.
 * 사진은 기본적으로는 폰 안에만 저장되지만, [FirebaseConfig.STORAGE_BUCKET] 이 설정돼
 * Storage 가 켜져 있으면 함께 올리고/내려받아 공유된다. (설정 안 하면 지금처럼 사진 제외)
 */
object SyncManager {

    private const val TAG = "WeJJoySync"
    private const val APP_NAME = "wejjoy"

    private val io = Executors.newSingleThreadExecutor()

    private var fbApp: FirebaseApp? = null
    private var root: DatabaseReference? = null
    private var entriesRef: DatabaseReference? = null
    private var entriesListener: ChildEventListener? = null
    private var membersRef: DatabaseReference? = null
    private var membersListener: ValueEventListener? = null
    private var bannedRef: DatabaseReference? = null
    private var bannedListener: ValueEventListener? = null

    /** 원격에서 새 내용이 들어왔을 때 화면을 새로 그리라고 알려주는 콜백 */
    @Volatile
    var onRemoteChange: (() -> Unit)? = null

    /** 마지막 상태 메시지 (설정 화면에 표시) */
    @Volatile
    var status: String = ""
        private set

    fun isConfigured(): Boolean = FirebaseConfig.isFilled()

    // ------------------------------------------------------------------
    // 초기화 / 로그인
    // ------------------------------------------------------------------

    private fun ensureApp(ctx: Context): FirebaseApp? {
        if (!isConfigured()) return null
        fbApp?.let { return it }
        return try {
            val existing = runCatching { FirebaseApp.getInstance(APP_NAME) }.getOrNull()
            val optionsBuilder = FirebaseOptions.Builder()
                .setProjectId(FirebaseConfig.PROJECT_ID.trim())
                .setApplicationId(FirebaseConfig.APPLICATION_ID.trim())
                .setApiKey(FirebaseConfig.API_KEY.trim())
                .setDatabaseUrl(FirebaseConfig.DATABASE_URL.trim())
            if (FirebaseConfig.isStorageFilled()) {
                optionsBuilder.setStorageBucket(FirebaseConfig.STORAGE_BUCKET.trim())
            }
            val app = existing ?: FirebaseApp.initializeApp(
                ctx.applicationContext,
                optionsBuilder.build(),
                APP_NAME
            )
            runCatching { FirebaseDatabase.getInstance(app).setPersistenceEnabled(true) }
            fbApp = app
            app
        } catch (t: Throwable) {
            Log.w(TAG, "Firebase 초기화 실패", t)
            status = "서버 설정을 확인해 주세요."
            null
        }
    }

    /** 익명 로그인. 성공하면 내 uid 를 돌려준다. */
    fun signIn(ctx: Context, cb: (uid: String?) -> Unit) {
        val app = ensureApp(ctx) ?: return cb(null)
        val auth = FirebaseAuth.getInstance(app)
        val cur = auth.currentUser
        if (cur != null) {
            Prefs.setMyUid(ctx, cur.uid)
            cb(cur.uid); return
        }
        auth.signInAnonymously()
            .addOnSuccessListener {
                val uid = it.user?.uid
                if (uid != null) Prefs.setMyUid(ctx, uid)
                cb(uid)
            }
            .addOnFailureListener {
                Log.w(TAG, "익명 로그인 실패", it)
                status = "서버에 연결하지 못했습니다. (익명 로그인 설정 확인)"
                cb(null)
            }
    }

    private fun groupRef(ctx: Context, code: String): DatabaseReference? {
        val app = ensureApp(ctx) ?: return null
        val r = FirebaseDatabase.getInstance(app).reference.child("groups").child(code)
        root = r
        return r
    }

    // ------------------------------------------------------------------
    // 사진 저장소(Storage) — STORAGE_BUCKET 이 설정된 경우에만 동작
    // ------------------------------------------------------------------

    private fun storageRoot(ctx: Context, code: String): StorageReference? {
        if (!FirebaseConfig.isStorageFilled() || code.isEmpty()) return null
        val app = ensureApp(ctx) ?: return null
        return try {
            FirebaseStorage.getInstance(app).reference.child("groups").child(code).child("photos")
        } catch (t: Throwable) {
            Log.w(TAG, "사진 저장소 참조 실패", t)
            null
        }
    }

    /** 사진 한 장을 서버로 올린다. (없으면 새로, 있으면 덮어씀) 실패해도 조용히 넘어간다. */
    private fun uploadPhoto(ctx: Context, code: String, name: String) {
        val ref = storageRoot(ctx, code)?.child(name) ?: return
        val file = ImageStore.file(ctx, name)
        if (!file.exists()) return
        runCatching {
            ref.putFile(Uri.fromFile(file))
                .addOnFailureListener { Log.w(TAG, "사진 업로드 실패: $name", it) }
        }.onFailure { Log.w(TAG, "사진 업로드 실패: $name", it) }
    }

    /** 사진을 서버에서 지운다. (그룹 미설정/Storage 미설정이면 아무 일도 하지 않는다) */
    fun deletePhoto(ctx: Context, name: String) {
        val code = Prefs.groupCode(ctx)
        val ref = storageRoot(ctx, code)?.child(name) ?: return
        ref.delete().addOnFailureListener { Log.w(TAG, "사진 삭제(서버) 실패: $name", it) }
    }

    /** 내 폰에 없는 사진들만 서버에서 받아온다. (호출한 스레드를 그대로 사용해 순서대로 내려받음) */
    private fun downloadMissingPhotos(ctx: Context, code: String, names: List<String>) {
        if (names.isEmpty()) return
        val root = storageRoot(ctx, code) ?: return
        for (name in names) {
            val local = ImageStore.file(ctx, name)
            if (local.exists()) continue
            val tmp = File(ImageStore.dir(ctx), "$name.part")
            try {
                Tasks.await(root.child(name).getFile(tmp))
                tmp.renameTo(local)
            } catch (t: Throwable) {
                Log.w(TAG, "사진 내려받기 실패: $name", t)
                runCatching { tmp.delete() }
            }
        }
    }

    // ------------------------------------------------------------------
    // 그룹 만들기 / 참여 / 나가기
    // ------------------------------------------------------------------

    /** 6자리 초대 코드 (헷갈리는 0/O/1/I 제외) */
    private fun newCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val sb = StringBuilder("WJ-")
        val rnd = java.security.SecureRandom()
        repeat(6) { sb.append(chars[rnd.nextInt(chars.length)]) }
        return sb.toString()
    }

    fun createGroup(ctx: Context, nick: String, cb: (code: String?, err: String?) -> Unit) {
        signIn(ctx) { uid ->
            if (uid == null) return@signIn cb(null, status.ifEmpty { "서버 연결 실패" })
            val code = newCode()
            val g = groupRef(ctx, code) ?: return@signIn cb(null, "서버 설정 오류")
            val now = System.currentTimeMillis()
            val data = HashMap<String, Any>()
            data["meta"] = mapOf("ownerUid" to uid, "createdAt" to now)
            data["members"] = mapOf(
                uid to mapOf("nick" to nick, "role" to Member.ROLE_OWNER, "joinedAt" to now)
            )
            g.updateChildren(data)
                .addOnSuccessListener {
                    Prefs.setGroup(ctx, code, nick, true)
                    pushAll(ctx)
                    start(ctx)
                    cb(code, null)
                }
                .addOnFailureListener { cb(null, "그룹을 만들지 못했습니다: ${it.message}") }
        }
    }

    fun joinGroup(ctx: Context, rawCode: String, nick: String, cb: (err: String?) -> Unit) {
        val code = rawCode.trim().uppercase()
        signIn(ctx) { uid ->
            if (uid == null) return@signIn cb(status.ifEmpty { "서버 연결 실패" })
            val g = groupRef(ctx, code) ?: return@signIn cb("서버 설정 오류")
            g.child("meta").get()
                .addOnSuccessListener { snap ->
                    if (!snap.exists()) return@addOnSuccessListener cb("그런 코드의 그룹이 없습니다.")
                    g.child("banned").child(uid).get()
                        .addOnSuccessListener { b ->
                            if (b.exists()) {
                                return@addOnSuccessListener cb(
                                    "관리자가 공유를 해제한 그룹입니다.\n관리자에게 '다시 허용' 을 요청하세요."
                                )
                            }
                            val now = System.currentTimeMillis()
                            g.child("members").child(uid)
                                .setValue(
                                    mapOf(
                                        "nick" to nick,
                                        "role" to Member.ROLE_MEMBER,
                                        "joinedAt" to now
                                    )
                                )
                                .addOnSuccessListener {
                                    Prefs.setGroup(ctx, code, nick, false)
                                    pushAll(ctx)
                                    start(ctx)
                                    cb(null)
                                }
                                .addOnFailureListener { cb("참여하지 못했습니다: ${it.message}") }
                        }
                        .addOnFailureListener { cb("코드를 확인하지 못했습니다: ${it.message}") }
                }
                .addOnFailureListener { cb("코드를 확인하지 못했습니다: ${it.message}") }
        }
    }

    /** 그룹에서 나간다. (내 폰의 기록은 그대로 남는다) */
    fun leaveGroup(ctx: Context) {
        val code = Prefs.groupCode(ctx)
        val uid = Prefs.myUid(ctx)
        if (code.isNotEmpty() && uid.isNotEmpty()) {
            runCatching { groupRef(ctx, code)?.child("members")?.child(uid)?.removeValue() }
        }
        stop()
        Prefs.clearGroup(ctx)
    }

    /**
     * 관리자가 공유자를 내보낸다.
     *
     * 구성원 목록에서 지우는 것만으로는 상대가 초대 코드를 다시 넣어 들어올 수 있으므로,
     * `banned` 목록에 같이 올려서 다시 못 들어오게 막는다. (되돌리려면 [allowMember])
     */
    fun removeMember(ctx: Context, uid: String, nick: String, cb: (err: String?) -> Unit) {
        val code = Prefs.groupCode(ctx)
        if (code.isEmpty()) return cb("그룹이 없습니다.")
        if (!Prefs.isOwner(ctx)) return cb("관리자만 내보낼 수 있습니다.")
        val g = groupRef(ctx, code) ?: return cb("서버 설정 오류")
        val ops = HashMap<String, Any?>()
        ops["members/$uid"] = null
        ops["banned/$uid"] = mapOf("nick" to nick, "at" to System.currentTimeMillis())
        g.updateChildren(ops)
            .addOnSuccessListener { cb(null) }
            .addOnFailureListener { cb("내보내지 못했습니다: ${it.message}") }
    }

    /** 잘못 내보냈을 때 다시 들어올 수 있게 풀어준다. (관리자만) */
    fun allowMember(ctx: Context, uid: String, cb: (err: String?) -> Unit) {
        val code = Prefs.groupCode(ctx)
        if (code.isEmpty()) return cb("그룹이 없습니다.")
        if (!Prefs.isOwner(ctx)) return cb("관리자만 할 수 있습니다.")
        val g = groupRef(ctx, code) ?: return cb("서버 설정 오류")
        g.child("banned").child(uid).removeValue()
            .addOnSuccessListener { cb(null) }
            .addOnFailureListener { cb("풀어주지 못했습니다: ${it.message}") }
    }

    /**
     * 내가 내보내진 상태인지 확인한다.
     * 맞으면 그룹 연결을 끊고 true 를 돌려준다.
     */
    fun checkKicked(ctx: Context, cb: (kicked: Boolean) -> Unit) {
        val code = Prefs.groupCode(ctx)
        val me = Prefs.myUid(ctx)
        if (code.isEmpty() || me.isEmpty()) return cb(false)
        val g = groupRef(ctx, code) ?: return cb(false)
        g.child("banned").child(me).get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    stop()
                    Prefs.clearGroup(ctx)
                    status = "관리자가 공유를 해제했습니다."
                    cb(true)
                } else cb(false)
            }
            .addOnFailureListener { cb(false) }
    }

    /**
     * 구성원 목록을 계속 지켜본다. 화면을 닫을 때 [stopObservingMembers] 를 부를 것.
     * 관리자에게는 내보낸 사람도 목록 아래에 함께 넘어온다.
     */
    fun observeMembers(ctx: Context, cb: (List<Member>) -> Unit) {
        val code = Prefs.groupCode(ctx)
        if (code.isEmpty()) return cb(emptyList())
        signIn(ctx) { uid ->
            if (uid == null) return@signIn cb(emptyList())
            stopObservingMembers()
            val g = groupRef(ctx, code) ?: return@signIn cb(emptyList())

            val members = ArrayList<Member>()
            val banned = ArrayList<Member>()
            var gotMembers = false

            fun emit() {
                val out = ArrayList<Member>(members)
                out.addAll(banned)
                cb(out)
            }

            val ml = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    gotMembers = true
                    members.clear()
                    for (c in snapshot.children) {
                        val k = c.key ?: continue
                        members.add(
                            Member(
                                uid = k,
                                nick = c.child("nick").getValue(String::class.java) ?: "(이름 없음)",
                                role = c.child("role").getValue(String::class.java) ?: Member.ROLE_MEMBER,
                                joinedAt = c.child("joinedAt").getValue(Long::class.java) ?: 0L
                            )
                        )
                    }
                    members.sortWith(compareByDescending<Member> { it.isOwner }.thenBy { it.joinedAt })
                    emit()
                }

                override fun onCancelled(error: DatabaseError) {
                    // 내보내지면 목록을 읽을 권한이 사라진다 → 정말 내보내진 건지 확인
                    checkKicked(ctx) { kicked ->
                        if (!kicked && !gotMembers) status = "구성원 목록을 읽지 못했습니다."
                        members.clear()
                        emit()
                    }
                }
            }

            val bl = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    banned.clear()
                    for (c in snapshot.children) {
                        val k = c.key ?: continue
                        banned.add(
                            Member(
                                uid = k,
                                nick = c.child("nick").getValue(String::class.java) ?: "(이름 없음)",
                                role = Member.ROLE_BANNED,
                                joinedAt = c.child("at").getValue(Long::class.java) ?: 0L
                            )
                        )
                    }
                    banned.sortBy { it.joinedAt }
                    // 내가 내보내진 명단에 있으면 즉시 연결을 끊는다
                    val me = Prefs.myUid(ctx)
                    if (banned.any { it.uid == me }) {
                        stop()
                        Prefs.clearGroup(ctx)
                        status = "관리자가 공유를 해제했습니다."
                    }
                    emit()
                }

                override fun onCancelled(error: DatabaseError) {
                    banned.clear()
                    emit()
                }
            }

            membersRef = g.child("members")
            membersListener = ml
            bannedRef = g.child("banned")
            bannedListener = bl
            membersRef?.addValueEventListener(ml)
            bannedRef?.addValueEventListener(bl)
        }
    }

    fun stopObservingMembers() {
        membersListener?.let { l -> membersRef?.removeEventListener(l) }
        membersListener = null
        membersRef = null
        bannedListener?.let { l -> bannedRef?.removeEventListener(l) }
        bannedListener = null
        bannedRef = null
    }

    // ------------------------------------------------------------------
    // 일기 주고받기
    // ------------------------------------------------------------------

    /** includePhotos: Storage 가 켜져 있을 때만 사진 파일명 목록도 같이 올린다. */
    private fun toMap(e: DiaryEntry, includePhotos: Boolean): Map<String, Any?> {
        val m = mutableMapOf<String, Any?>(
            "uid" to e.uid,
            "dateEpochDay" to e.dateEpochDay,
            "timeMinutes" to e.timeMinutes,
            "endDateEpochDay" to e.endDateEpochDay,
            "endTimeMinutes" to e.endTimeMinutes,
            "title" to e.title,
            "content" to e.content,
            "mood" to e.mood,
            "importance" to e.importance,
            "tags" to e.tags,
            "reminderAtMillis" to e.reminderAtMillis,
            "createdAt" to e.createdAt,
            "updatedAt" to e.updatedAt,
            "deletedAt" to e.deletedAt,
            "authorNick" to e.authorNick,
            "sticker" to e.sticker
        )
        if (includePhotos) m["photos"] = e.photos
        return m
    }

    @Suppress("UNCHECKED_CAST")
    private fun fromSnapshot(s: DataSnapshot): DiaryEntry? {
        val uid = s.child("uid").getValue(String::class.java) ?: s.key ?: return null
        fun l(k: String, d: Long = 0L) = s.child(k).getValue(Long::class.java) ?: d
        fun i(k: String, d: Int = 0) = (s.child(k).getValue(Long::class.java) ?: d.toLong()).toInt()
        fun st(k: String) = s.child(k).getValue(String::class.java) ?: ""
        val tags = (s.child("tags").value as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        // Storage 가 꺼져 있는 상대가 올린 글에는 photos 칸이 아예 없다 → 빈 목록
        val photos = (s.child("photos").value as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        return DiaryEntry(
            id = 0L,
            dateEpochDay = l("dateEpochDay"),
            timeMinutes = i("timeMinutes", -1),
            title = st("title"),
            content = st("content"),
            mood = st("mood"),
            importance = i("importance", 50).coerceIn(1, 100),
            tags = tags,
            photos = photos,
            reminderAtMillis = l("reminderAtMillis"),
            createdAt = l("createdAt"),
            updatedAt = l("updatedAt"),
            endDateEpochDay = l("endDateEpochDay", -1L),
            endTimeMinutes = i("endTimeMinutes", -1),
            uid = uid,
            deletedAt = l("deletedAt"),
            authorNick = st("authorNick"),
            sticker = st("sticker")
        )
    }

    /** 일기 한 건을 서버에 올린다. (저장/삭제 후 호출) */
    fun push(ctx: Context, entry: DiaryEntry) {
        val code = Prefs.groupCode(ctx)
        if (code.isEmpty() || entry.uid.isEmpty()) return
        val app = ensureApp(ctx) ?: return
        val storageOn = FirebaseConfig.isStorageFilled()
        runCatching {
            FirebaseDatabase.getInstance(app).reference
                .child("groups").child(code).child("entries").child(entry.uid)
                .setValue(toMap(entry, storageOn))
        }
        if (storageOn && entry.photos.isNotEmpty()) {
            io.execute {
                for (p in entry.photos) uploadPhoto(ctx, code, p)
            }
        }
    }

    /** 내 폰의 기록 전부를 서버에 올린다. (그룹을 만들거나 참여한 직후 한 번) */
    fun pushAll(ctx: Context) {
        val code = Prefs.groupCode(ctx)
        if (code.isEmpty()) return
        val app = ensureApp(ctx) ?: return
        val storageOn = FirebaseConfig.isStorageFilled()
        io.execute {
            runCatching {
                val dao = AppDatabase.get(ctx).diaryDao()
                val all = dao.getAllSync()
                val ref = FirebaseDatabase.getInstance(app).reference
                    .child("groups").child(code).child("entries")
                for (e in all) {
                    val withUid = if (e.uid.isEmpty()) {
                        val u = com.jooshin.diary.data.newEntryUid()
                        val fixed = e.copy(uid = u)
                        dao.updateSync(fixed); fixed
                    } else e
                    ref.child(withUid.uid).setValue(toMap(withUid, storageOn))
                    if (storageOn) {
                        for (p in withUid.photos) uploadPhoto(ctx, code, p)
                    }
                }
            }.onFailure { Log.w(TAG, "전체 업로드 실패", it) }
        }
    }

    /** 서버 변경을 계속 받아온다. */
    fun start(ctx: Context) {
        val code = Prefs.groupCode(ctx)
        if (code.isEmpty() || !isConfigured()) return
        signIn(ctx) { uid ->
            if (uid == null) return@signIn
            val app = ensureApp(ctx) ?: return@signIn
            stopEntries()
            val ref = FirebaseDatabase.getInstance(app).reference
                .child("groups").child(code).child("entries")
            val l = object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, prev: String?) = applyRemote(ctx, snapshot)
                override fun onChildChanged(snapshot: DataSnapshot, prev: String?) = applyRemote(ctx, snapshot)
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, prev: String?) {}
                override fun onCancelled(error: DatabaseError) {
                    status = "서버에서 내용을 받지 못했습니다."
                    // 관리자가 나를 내보냈을 수도 있다
                    checkKicked(ctx) { }
                }
            }
            entriesRef = ref
            entriesListener = l
            ref.addChildEventListener(l)
            status = "공유 중"
        }
    }

    private fun applyRemote(ctx: Context, snapshot: DataSnapshot) {
        val remote = fromSnapshot(snapshot) ?: return
        val code = Prefs.groupCode(ctx)
        io.execute {
            runCatching {
                val dao = AppDatabase.get(ctx).diaryDao()
                val local = dao.getByUidSync(remote.uid)
                // Storage 가 켜져 있으면 상대가 올린 사진을 받아와 같이 반영하고,
                // 꺼져 있으면 예전처럼 내 폰에 있는 사진 목록을 그대로 유지한다.
                val storageOn = FirebaseConfig.isStorageFilled() && code.isNotEmpty()
                if (storageOn) downloadMissingPhotos(ctx, code, remote.photos)
                val saved: DiaryEntry = when {
                    local == null -> {
                        val toInsert = if (storageOn) remote else remote.copy(photos = emptyList())
                        val id = dao.insertSync(toInsert)
                        toInsert.copy(id = id)
                    }
                    remote.updatedAt > local.updatedAt -> {
                        val merged = if (storageOn) {
                            remote.copy(id = local.id)
                        } else {
                            // 사진은 내 폰에 있는 것을 그대로 유지한다
                            remote.copy(id = local.id, photos = local.photos)
                        }
                        dao.updateSync(merged)
                        merged
                    }
                    else -> return@runCatching
                }
                if (saved.deletedAt == 0L) ReminderScheduler.scheduleEntry(ctx, saved)
                else ReminderScheduler.cancelEntry(ctx, saved.id)
                WidgetUpdater.refreshAll(ctx)
                onRemoteChange?.invoke()
            }.onFailure { Log.w(TAG, "받은 내용 반영 실패", it) }
        }
    }

    private fun stopEntries() {
        entriesListener?.let { l -> entriesRef?.removeEventListener(l) }
        entriesListener = null
        entriesRef = null
    }

    fun stop() {
        stopEntries()
        stopObservingMembers()
        status = ""
    }
}
