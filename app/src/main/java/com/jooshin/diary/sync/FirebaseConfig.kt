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
    const val APPLICATION_ID = "1:687152872259:android:1bd630a2769c4131f3cdf6"

    /** 웹 API 키     예) AIzaSyD-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx */
    const val API_KEY = "AIzaSyAyxHjx4oN02oreq0s1fvGSepPkPDGRem8"

    /** 실시간 데이터베이스 주소  예) https://wejjoy-1a2b3-default-rtdb.asia-southeast1.firebasedatabase.app */
    const val DATABASE_URL = "https://wejjoy-7f100-default-rtdb.asia-southeast1.firebasedatabase.app"

    /** 4칸이 모두 채워졌는가 */
    fun isFilled(): Boolean =
        PROJECT_ID.trim().isNotEmpty() &&
            APPLICATION_ID.trim().isNotEmpty() &&
            API_KEY.trim().isNotEmpty() &&
            DATABASE_URL.trim().isNotEmpty()
}
