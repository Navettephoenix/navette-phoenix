package com.phoenix.navette28

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions

class FirebaseGateway(context: Context) {
    val available = FirebaseApp.getApps(context).isNotEmpty()

    init {
        if (available) {
            FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        }
    }

    fun call(name: String, payload: Map<String, Any>): Task<Any?> {
        if (!available) return Tasks.forException(IllegalStateException("Service non configuré"))
        return authenticated().onSuccessTask {
            FirebaseFunctions.getInstance("europe-west1").getHttpsCallable(name)
                .call(payload).continueWith { task -> task.result.data }
        }
    }

    private fun authenticated(): Task<Unit> {
        val auth = FirebaseAuth.getInstance()
        return if (auth.currentUser != null) Tasks.forResult(Unit)
        else auth.signInAnonymously().continueWith { it.result; Unit }
    }
}
