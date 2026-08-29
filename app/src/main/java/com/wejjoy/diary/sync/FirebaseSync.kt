package com.wejjoy.diary.sync

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.wejjoy.diary.data.AppDatabase
import com.wejjoy.diary.data.DiaryEntry
import com.wejjoy.diary.util.ImageStore
import com.wejjoy.diary.util.Prefs
import com.wejjoy.diary.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Firebase 를 이용한 그룹 다이어리 동기화 엔진.
 *
 * ─ 구조 ─
 *  Firebase Auth    : 익명 로그인으로 uid 발급
 *  Firestore        : groups/{groupId} (그룹·회원) , groups/{groupId}/entries (일기)
 *  Storage          : entries/{entryId}/{i}.jpg (사진)
 *
 * ─ 흐름 ─
 *  [관리자] 그룹 만들기 → joinCode 발급 → 설정에서 공유자 승인/해제
 *  [공유자] 초대 코드 + 별명으로 가입 → 관리자 승인 후 실시간 공유 시작
 *  양쪽 모두 쓰기 즉시 업로드 + 실시간 리스너로 상대방 변경을 자동 반영
 */
object FirebaseSync {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage get() = FirebaseStorage.getInstance()

    private var entriesListener: ListenerRegistration? = null
    private var groupListener: ListenerRegistration? = null

    /** 현재 동기화된 그룹 상태 (UI 관찰용) */
    private val _groupState = MutableStateFlow<GroupDoc?>(null)
    val groupState: StateFlow<GroupDoc?> = _groupState

    /** 동기화 가능한 설정(google-services.json)이 포함돼 있는가 */
    val isConfigured: Boolean by lazy {
        // google-services.json 이 앱에 없으면 FirebaseApp 초기화가 실패한다.
        try { com.google.firebase.FirebaseApp.getInstance(); true } catch (_: Exception) { false }
    }

    val currentUid: String? get() = auth.currentUser?.uid

    // ───────────────────────── 초기화 ─────────────────────────

    /** 앱 시작 시 호출: 익명 로그인 보장 + 저장된 그룹이 있으면 리스너 재개 */
    fun init(context: Context) {
        if (!isConfigured) return
        scope.launch {
            try { ensureSignedIn() } catch (_: Exception) { return@launch }
            val gid = Prefs.syncGroupId(context)
            if (!gid.isNullOrEmpty()) startListeners(context, gid)
        }
    }

    private suspend fun ensureSignedIn(): String {
        val cur = auth.currentUser
        if (cur != null) return cur.uid
        return auth.signInAnonymously().await().user!!.uid
    }

    // ───────────────────────── 그룹 생성 (관리자) ─────────────────────────

    /** 관리자: 새 그룹 생성. 성공 시 JoinResult.Success(groupId, true) */
    suspend fun createGroup(context: Context, adminName: String): JoinResult {
        if (!isConfigured) return JoinResult.Error("Firebase 설정(google-services.json)이 없습니다.")
        return try {
            val uid = ensureSignedIn()
            val code = uniqueJoinCode()
            val ref = db.collection("groups").document()
            val group = GroupDoc(
                groupName = "${adminName}의 다이어리",
                joinCode = code,
                adminUid = uid,
                members = mapOf(uid to MemberInfo(adminName, "admin", "active")),
                memberIds = listOf(uid)
            )
            ref.set(group).await()
            Prefs.setSyncGroupId(context, ref.id)
            Prefs.setSyncJoinCode(context, code)
            Prefs.setSyncRole(context, Prefs.ROLE_ADMIN)
            Prefs.setSyncNickname(context, adminName)
            startListeners(context, ref.id)
            JoinResult.Success(ref.id, true)
        } catch (e: Exception) {
            JoinResult.Error(e.message ?: "그룹 생성 실패")
        }
    }

    /** 중복되지 않는 6자리 코드 발급 */
    private suspend fun uniqueJoinCode(): String {
        repeat(20) {
            val code = generateJoinCode()
            val snap = db.collection("groups").whereEqualTo("joinCode", code).limit(1).get().await()
            if (snap.isEmpty) return code
        }
        return generateJoinCode()
    }

    // ───────────────────────── 그룹 가입 (공유자) ─────────────────────────

    /**
     * 공유자: 초대 코드로 그룹 찾아 가입 신청.
     * 관리자가 승인(status=active)하면 실시간 공유가 시작된다.
     */
    suspend fun joinGroup(context: Context, code: String, name: String): JoinResult {
        if (!isConfigured) return JoinResult.Error("Firebase 설정(google-services.json)이 없습니다.")
        return try {
            val uid = ensureSignedIn()
            val snap = db.collection("groups").whereEqualTo("joinCode", code.trim()).limit(1).get().await()
            if (snap.isEmpty) return JoinResult.Error("코드가 올바르지 않습니다.")
            val doc = snap.documents[0]
            val group = doc.toObject(GroupDoc::class.java) ?: return JoinResult.Error("그룹 정보 오류")

            if (group.members.containsKey(uid)) {
                // 이미 가입된 기기 (재설치 등)
                Prefs.setSyncGroupId(context, doc.id)
                Prefs.setSyncJoinCode(context, code.trim())
                Prefs.setSyncRole(context, if (group.isAdmin(uid)) Prefs.ROLE_ADMIN else Prefs.ROLE_MEMBER)
                startListeners(context, doc.id)
                return JoinResult.Success(doc.id, group.isAdmin(uid))
            }

            // 가입 신청: status = pending (관리자 승인 필요)
            val info = MemberInfo(name, "member", "pending")
            db.runTransaction { tx ->
                val fresh = tx.get(doc.reference).toObject(GroupDoc::class.java) ?: return@runTransaction
                val members = fresh.members.toMutableMap()
                members[uid] = info
                val ids = fresh.memberIds.toMutableList()
                if (!ids.contains(uid)) ids.add(uid)
                tx.update(doc.reference, mapOf("members" to members, "memberIds" to ids))
            }.await()

            Prefs.setSyncGroupId(context, doc.id)
            Prefs.setSyncJoinCode(context, code.trim())
            Prefs.setSyncRole(context, Prefs.ROLE_MEMBER)
            Prefs.setSyncNickname(context, name)
            Prefs.setSyncPending(context, true)
            startListeners(context, doc.id)
            JoinResult.Success(doc.id, false)
        } catch (e: Exception) {
            JoinResult.Error(e.message ?: "가입 실패")
        }
    }

    // ───────────────────────── 회원 관리 (관리자) ─────────────────────────

    /** 관리자: 대기 중인 공유자 승인 */
    suspend fun approveMember(groupId: String, uid: String) {
        val ref = db.collection("groups").document(groupId)
        db.runTransaction { tx ->
            val g = tx.get(ref).toObject(GroupDoc::class.java) ?: return@runTransaction
            val m = g.members[uid] ?: return@runTransaction
            val members = g.members.toMutableMap()
            members[uid] = m.copy(status = "active")
            tx.update(ref, "members", members)
        }.await()
    }

    /** 관리자: 공유 해제 (멤버 제거) — 본인은 제거 불가 */
    suspend fun removeMember(groupId: String, uid: String) {
        val ref = db.collection("groups").document(groupId)
        db.runTransaction { tx ->
            val g = tx.get(ref).toObject(GroupDoc::class.java) ?: return@runTransaction
            if (g.adminUid == uid) return@runTransaction
            val members = g.members.toMutableMap().apply { remove(uid) }
            val ids = g.memberIds.toMutableList().apply { remove(uid) }
            tx.update(ref, mapOf("members" to members, "memberIds" to ids))
        }.await()
    }

    /** 관리자: 초대 코드 재발급 (구 코드로는 더 이상 가입 불가) */
    suspend fun regenerateCode(context: Context): String {
        val gid = Prefs.syncGroupId(context) ?: return ""
        val code = uniqueJoinCode()
        db.collection("groups").document(gid).update("joinCode", code).await()
        Prefs.setSyncJoinCode(context, code)
        return code
    }

    /** 별명 변경 (내 기기에서 표시되는 이름) */
    suspend fun renameMe(context: Context, newName: String) {
        val gid = Prefs.syncGroupId(context) ?: return
        val uid = currentUid ?: return
        val ref = db.collection("groups").document(gid)
        db.runTransaction { tx ->
            val g = tx.get(ref).toObject(GroupDoc::class.java) ?: return@runTransaction
            val m = g.members[uid] ?: return@runTransaction
            val members = g.members.toMutableMap()
            members[uid] = m.copy(name = newName)
            tx.update(ref, "members", members)
        }.await()
        Prefs.setSyncNickname(context, newName)
    }

    // ───────────────────────── 실시간 리스너 ─────────────────────────

    /** 그룹 문서 + 일기 컬렉션에 실시간 리스너 부착 */
    private fun startListeners(context: Context, groupId: String) {
        stopListeners()
        val appCtx = context.applicationContext

        // 1) 그룹 문서: 회원 승인/해제·코드 변경 감지
        groupListener = db.collection("groups").document(groupId)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || !snap.exists()) return@addSnapshotListener
                val g = snap.toObject(GroupDoc::class.java) ?: return@addSnapshotListener
                _groupState.value = g
                val uid = currentUid
                val me = g.members[uid]
                // 관리자가 나를 해제했거나 승인 대기 → 동기화 중단
                if (me == null) {
                    Prefs.clearSync(appCtx); stopListeners(); _groupState.value = null
                    return@addSnapshotListener
                }
                Prefs.setSyncPending(appCtx, me.status == "pending")
                Prefs.setSyncJoinCode(appCtx, g.joinCode)
            }

        // 2) 일기 컬렉션: 실시간 자동 반영 (추가/수정/삭제)
        entriesListener = db.collection("groups").document(groupId)
            .collection("entries")
            .addSnapshotListener { snaps, err ->
                if (err != null || snaps == null) return@addSnapshotListener
                val uid = currentUid
                scope.launch {
                    val dao = AppDatabase.get(appCtx).diaryDao()
                    var changed = false
                    for (dc in snaps.documentChanges) {
                        val r = dc.document.toObject(RemoteEntry::class.java)
                        r.remoteId = dc.document.id
                        // 내가 방금 올린 변경은 로컬에 이미 반영돼 있으므로 건너뜀
                        if (r.authorUid == uid) continue
                        when (dc.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                if (r.deleted) { dao.deleteByRemoteId(r.remoteId); changed = true }
                                else { upsertFromRemote(appCtx, dao, r); changed = true }
                            }
                            DocumentChange.Type.REMOVED -> { dao.deleteByRemoteId(r.remoteId); changed = true }
                        }
                    }
                    if (changed) WidgetUpdater.refreshAll(appCtx)
                }
            }
    }

    fun stopListeners() {
        entriesListener?.remove(); entriesListener = null
        groupListener?.remove(); groupListener = null
    }

    // ───────────────────────── 업로드 (로컬 → 클라우드) ─────────────────────────

    /** 일기 저장/수정 시 호출: Firestore 에 업로드 + 사진은 Storage 업로드 */
    suspend fun uploadEntry(context: Context, entry: DiaryEntry) {
        if (!isConfigured) return
        val gid = Prefs.syncGroupId(context) ?: return
        val uid = ensureSignedIn()
        val now = System.currentTimeMillis()

        // remoteId 결정 (기존 것 재사용, 없으면 새로)
        val dao = AppDatabase.get(context).diaryDao()
        val remoteId = entry.remoteId.ifBlank {
            db.collection("groups").document(gid).collection("entries").document().id
        }

        // 사진 업로드 → Storage 경로 목록
        val photoPaths = mutableListOf<String>()
        entry.photos.forEachIndexed { i, name ->
            try {
                val f = ImageStore.file(context, name)
                if (f.exists()) {
                    val path = "groups/$gid/entries/$remoteId/$i.jpg"
                    storage.reference.child(path).putFile(android.net.Uri.fromFile(f)).await()
                    photoPaths.add(path)
                }
            } catch (_: Exception) { /* 개별 사진 실패는 무시 */ }
        }

        val remote = RemoteEntry(
            remoteId = remoteId, groupId = gid,
            authorUid = uid, authorName = Prefs.syncNickname(context),
            dateEpochDay = entry.dateEpochDay, timeMinutes = entry.timeMinutes,
            endDateEpochDay = entry.endDateEpochDay, endTimeMinutes = entry.endTimeMinutes,
            title = entry.title, content = entry.content, mood = entry.mood,
            importance = entry.importance, tags = entry.tags,
            photoPaths = photoPaths,
            reminderAtMillis = 0L, // 알림은 각자 기기에서만
            createdAt = if (entry.createdAt > 0) entry.createdAt else now,
            updatedAt = now, deleted = false
        )
        db.collection("groups").document(gid).collection("entries").document(remoteId)
            .set(remote).await()

        // 로컬에 remoteId 저장 (다음 수정 때 같은 문서에 업로드)
        if (entry.remoteId != remoteId) dao.setRemoteId(entry.id, remoteId)
    }

    /** 일기 삭제 시 호출: tombstone(deleted=true) 으로 표시 */
    suspend fun markDeleted(context: Context, entry: DiaryEntry) {
        if (!isConfigured || entry.remoteId.isBlank()) return
        val gid = Prefs.syncGroupId(context) ?: return
        try {
            db.collection("groups").document(gid).collection("entries")
                .document(entry.remoteId)
                .set(mapOf("deleted" to true, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
                .await()
        } catch (_: Exception) {}
    }

    // ───────────────────────── 다운로드 (클라우드 → 로컬) ─────────────────────────

    /** 원격 일기를 로컬 Room 에 반영 (remoteId 기준 upsert) */
    private suspend fun upsertFromRemote(context: Context, dao: com.wejjoy.diary.data.DiaryDao, r: RemoteEntry) {
        // 사진 다운로드 (아직 없는 것만)
        val localPhotos = mutableListOf<String>()
        r.photoPaths.forEachIndexed { i, path ->
            try {
                val name = "remote_${r.remoteId}_$i.jpg"
                val f = ImageStore.file(context, name)
                if (!f.exists()) {
                    storage.reference.child(path).getFile(f).await()
                }
                localPhotos.add(name)
            } catch (_: Exception) {}
        }

        val existing = dao.getByRemoteId(r.remoteId)
        val local = DiaryEntry(
            id = existing?.id ?: 0L,
            remoteId = r.remoteId,
            dateEpochDay = r.dateEpochDay, timeMinutes = r.timeMinutes,
            endDateEpochDay = r.endDateEpochDay, endTimeMinutes = r.endTimeMinutes,
            title = r.title, content = r.content, mood = r.mood,
            importance = r.importance, tags = r.tags, photos = localPhotos,
            reminderAtMillis = existing?.reminderAtMillis ?: 0L,
            createdAt = r.createdAt, updatedAt = r.updatedAt,
            authorName = r.authorName
        )
        if (existing == null) dao.insert(local) else dao.update(local)
    }
}
