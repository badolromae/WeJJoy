package com.jooshin.diary.sync

/**
 * 공유 서버(파이어베이스) 접속 정보.
 *
 * 파이어베이스 콘솔에서 프로젝트를 만든 뒤, 아래 4칸의 따옴표 사이에 값을 붙여넣으세요.
 * 자세한 순서는 저장소의 `파이어베이스-설정방법.md` 를 보시면 됩니다.
 *
 * 4칸을 비워두면 공유 기능이 꺼진 채로, 혼자 쓰는 일반 다이어리로 정상 동작합니다.
 */
object FirebaseConfig {

    /** 프로젝트 ID   예) wejjoy-1a2b3 */
    const val PROJECT_ID = "wejjoy-7f100"

    /** 앱 ID        예) 1:123456789012:android:abcdef1234567890 */
    const val APPLICATION_ID = "1:687152872259:android:da03f2a6ad3957faf3cdf6"

    /** 웹 API 키     예) AIzaSyD-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx */
    const val API_KEY = "AIzaSyAyxHjx4oN02oreq0s1fvGSepPkPDGRem8"

    /**
     * 실시간 데이터베이스 주소.
     *
     * ★ 아래 값은 '싱가포르(asia-southeast1)' 로 만들었을 때의 주소입니다.
     *   파이어베이스 콘솔 ▸ 빌드 ▸ Realtime Database 화면 위쪽에 나오는 주소와
     *   글자 하나까지 같은지 반드시 확인하세요. 다르면 그 주소로 바꿔주세요.
     *   (미국으로 만들었다면 https://wejjoy-7f100-default-rtdb.firebaseio.com 형태입니다)
     *   주소 끝에 / 를 붙이지 마세요.
     */
    const val DATABASE_URL = "https://wejjoy-7f100-default-rtdb.asia-southeast1.firebasedatabase.app"

    /**
     * 사진 공유용 저장소(Storage) 버킷 이름. (선택 — 사진도 같이 공유하고 싶을 때만)
     *
     * 파이어베이스 콘솔 ▸ 빌드 ▸ Storage 화면 위쪽에 나오는 gs:// 로 시작하는 주소에서
     * "gs://" 를 뺀 나머지입니다. (예: wejjoy-7f100.firebasestorage.app)
     * Storage 를 켜려면 파이어베이스 요금제를 Blaze(종량제)로 바꿔야 합니다 — 자세한 건
     * '파이어베이스-설정방법.md' 의 "사진도 같이 공유하기" 부분을 참고하세요.
     *
     * 비워두면 사진 공유 기능만 꺼진 채, 글·일정 공유는 지금처럼 정상 동작합니다.
     */
    const val STORAGE_BUCKET = ""

    /** 4칸이 모두 채워졌는가 (글·일정 공유의 최소 조건) */
    fun isFilled(): Boolean =
        PROJECT_ID.trim().isNotEmpty() &&
            APPLICATION_ID.trim().isNotEmpty() &&
            API_KEY.trim().isNotEmpty() &&
            DATABASE_URL.trim().isNotEmpty()

    /** 사진 공유까지 켤 준비가 됐는가 */
    fun isStorageFilled(): Boolean = isFilled() && STORAGE_BUCKET.trim().isNotEmpty()
}
