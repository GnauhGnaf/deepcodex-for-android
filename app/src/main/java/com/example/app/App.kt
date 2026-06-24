package com.example.app

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Start Linux environment setup in background
        appScope.launch {
            Log.d("LinuxEnv", "Starting Linux environment setup...")
            val result = container.linuxEnvironment.setup()
            result.fold(
                onSuccess = { Log.d("LinuxEnv", "Setup success:\n$it") },
                onFailure = { Log.e("LinuxEnv", "Setup failed", it) }
            )
        }
    }
}
