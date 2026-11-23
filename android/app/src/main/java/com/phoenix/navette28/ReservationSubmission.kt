package com.phoenix.navette28

import androidx.lifecycle.ViewModel
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.TimeUnit

internal data class SubmissionReceipt(val reference: String, val status: String, val notifications: Boolean)

/** The activity can reattach to the same acknowledgement after rotation or a trip to the background. */
internal class ReservationSubmission : ViewModel() {
    var pending: Task<SubmissionReceipt>? = null
        private set

    fun start(send: () -> Task<SubmissionReceipt>): Task<SubmissionReceipt> {
        pending?.let { return it }
        val operation = try {
            Tasks.withTimeout(send(), 30, TimeUnit.SECONDS)
        } catch (error: Exception) {
            Tasks.forException<SubmissionReceipt>(error)
        }
        pending = operation
        return operation
    }

    fun consume(completed: Task<SubmissionReceipt>): Boolean {
        if (pending !== completed) return false
        pending = null
        return true
    }
}

internal fun submitBooking(booking: Booking, requestId: String, notify: Boolean): Task<SubmissionReceipt> {
    fun receipt(reply: Any?, notifications: Boolean): SubmissionReceipt {
        val data = reply as? Map<*, *>
        val reference = data?.get("reference") as? String
        check(!reference.isNullOrBlank()) { "Référence de demande absente." }
        return SubmissionReceipt(reference, data["status"] as? String ?: "RECEIVED", notifications)
    }
    if (BuildConfig.GOOGLE_MAIL) {
        return GoogleMailGateway.submit(booking, requestId).onSuccessTask { reply ->
            Tasks.forResult(receipt(reply, false))
        }
    }
    val token = if (notify) {
        Tasks.withTimeout(FirebaseMessaging.getInstance().token, 5, TimeUnit.SECONDS)
            .continueWith { if (it.isSuccessful) it.result else "" }
    } else Tasks.forResult("")
    return token.onSuccessTask { pushToken ->
        FirebaseGateway.get().call("createReservation", booking.payload(requestId) + ("pushToken" to pushToken))
            .onSuccessTask { reply -> Tasks.forResult(receipt(reply, pushToken.isNotEmpty())) }
    }
}
