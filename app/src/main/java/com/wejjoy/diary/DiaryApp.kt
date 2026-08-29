package com.wejjoy.diary

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.wejjoy.diary.notify.NotificationHelper
import com.wejjoy.diary.sync.FirebaseSync
import com.wejjoy.diary.util.AppLock

class DiaryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        FirebaseSync.init(this)   // 그룹 공유 실시간 동기화 시작

        // 앱이 백그라운드로 나가면 잠금 상태로 전환(잠금 사용 중일 때)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                AppLock.onEnterBackground()
            }
        })
    }
}
