package com.pixelthings.shannonconfigpro

import android.app.Application
import com.topjohnwu.superuser.Shell

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 修正：把 .build() 拿掉，直接傳入 Builder。
        // 加入 FLAG_REDIRECT_STDERR 可以幫助我們之後在 Logcat 看到錯誤訊息。
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
        )
    }
}