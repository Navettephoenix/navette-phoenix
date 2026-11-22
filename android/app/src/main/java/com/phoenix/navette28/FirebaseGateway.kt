package com.phoenix.navette28

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions

class FirebaseGateway private constructor() {
    val available = runCatching { FirebaseApp.getInstance() }.isSuccess
    private var pendingSignIn: Task<AuthResult>? = null

    init {
        if (available) {
            FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(appCheckProvider())
        }
    }

    fun call(name: String, payload: Map<String, Any>): Task<Any?> {
        if (!available) return Tasks.forException(IllegalStateException("Service non configuré"))
        return authenticated().onSuccessTask {
            FirebaseFunctions.getInstance("europe-west1").getHttpsCallable(name)
                .call(payload)
        }.onSuccessTask { result -> Tasks.forResult(result.data) }
    }

    @Synchronized
    private fun authenticated(): Task<Unit> {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) return Tasks.forResult(Unit)
        val signIn = pendingSignIn?.takeUnless { it.isComplete }
            ?: auth.signInAnonymously().also { pendingSignIn = it }
        return signIn.onSuccessTask { Tasks.forResult(Unit) }
    }

    companion object {
        private var instance: FirebaseGateway? = null

        @Synchronized
        fun get(): FirebaseGateway =
            instance ?: FirebaseGateway().also { instance = it }
    }
}
