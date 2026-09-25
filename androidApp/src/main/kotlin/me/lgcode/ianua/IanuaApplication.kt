package me.lgcode.ianua

import android.app.Application
import me.lgcode.ianua.data.RuleRefreshWorker
import me.lgcode.ianua.data.Rules
import me.lgcode.ianua.data.Settings

class IanuaApplication : Application() {
    lateinit var settings: Settings
        private set
    lateinit var rules: Rules
        private set

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        rules = Rules(this)
        RuleRefreshWorker.schedule(this)
    }
}

val android.content.Context.ianua: IanuaApplication get() = applicationContext as IanuaApplication
