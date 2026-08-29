package com.wejjoy.diary.sync

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Firestore: groups/{groupId}
 * 관리자(adminUid) 1명 + 공유자들(members 맵) 로 이루어진 다이어리 그룹.
 * joinCode 는 6자리 숫자 비밀번호 — 서브 앱이 그룹에 연결할 때 사용.
 */
data class GroupDoc(
    var groupName: String = "",
    var joinCode: String = "",
    var adminUid: String = "",
    @ServerTimestamp var createdAt: Date? = null,
    /** uid -> 회원 정보 (관리자 포함). 맵이 최신 상태를 가진다. */
    var members: Map<String, MemberInfo> = emptyMap(),
    /** 빠른 조회용 uid 목록 (array-contains 쿼리에 사용) */
    var memberIds: List<String> = emptyList()
) {
    /** 이 uid 가 관리자인가 */
    fun isAdmin(uid: String?): Boolean = uid != null && uid == adminUid

    /** 현재 회원 uid 가 그룹에 속해 있는가 */
    fun isMember(uid: String?): Boolean = uid != null && members.containsKey(uid)

    fun memberName(uid: String?): String = members[uid]?.name ?: ""
}

/**
 * 그룹 회원 정보 (members 맵 값 / group_members 컬렉션과 동일 필드)
 * role: "admin" | "member", status: "active" | "pending"(관리자 승인 대기)
 */
data class MemberInfo(
    var name: String = "",
    var role: String = "member",
    var status: String = "active",
    @ServerTimestamp var joinedAt: Date? = null
)

/**
 * Firestore: groups/{groupId}/entries/{entryId}
 * RemoteView 는 Room 의 DiaryEntry 와 필드를 맞춘 공유용 스키마.
 * remoteId = 그룹 내 고유 ID = 로컬 Room id 와는 별개.
 */
data class RemoteEntry(
    var remoteId: String = "",
    var groupId: String = "",
    var authorUid: String = "",
    var authorName: String = "",
    var dateEpochDay: Long = 0L,
    var timeMinutes: Int = -1,
    var endDateEpochDay: Long = -1L,
    var endTimeMinutes: Int = -1,
    var title: String = "",
    var content: String = "",
    var mood: String = "",
    var importance: Int = 50,
    var tags: List<String> = emptyList(),
    /** Firebase Storage 의 파일 경로 목록 (entries/{remoteId}/{n}.jpg) */
    var photoPaths: List<String> = emptyList(),
    var reminderAtMillis: Long = 0L,
    var createdAt: Long = 0L,
    var updatedAt: Long = 0L,
    var deleted: Boolean = false
)

/** 그룹 가입/생성 결과 */
sealed class JoinResult {
    data class Success(val groupId: String, val isAdmin: Boolean) : JoinResult()
    data class Error(val message: String) : JoinResult()
}

/** 6자리 숫자 초대 코드 생성 (0~9, 중복 없는 랜덤) */
fun generateJoinCode(): String {
    val rand = java.security.SecureRandom()
    return buildString { repeat(6) { append(rand.nextInt(10)) } }
}
