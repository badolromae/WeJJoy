package com.jooshin.diary

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.jooshin.diary.notify.NotificationHelper
import com.jooshin.diary.notify.ReminderScheduler
import com.jooshin.diary.util.AppLock

class DiaryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 공유 그룹에 들어가 있으면 실시간 동기화 시작
        runCatching { com.jooshin.diary.sync.SyncManager.start(this) }
        NotificationHelper.createChannels(this)
        // 자정 위젯 갱신 + 무음 브리핑(오늘/이번주/이번달) 예약 (재부팅 안 해도 걸리도록)
        runCatching { ReminderScheduler.scheduleMidnight(this) }
        runCatching { ReminderScheduler.scheduleBriefings(this) }

        // 앱이 백그라운드로 나가면 잠금 상태로 전환(잠금 사용 중일 때)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                AppLock.onEnterBackground()
            }
        })
    }
}
