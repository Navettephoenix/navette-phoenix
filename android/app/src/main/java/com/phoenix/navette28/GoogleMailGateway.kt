package com.phoenix.navette28

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import java.util.Locale

internal object GoogleMailGateway {
    private val database by lazy {
        FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
    }

    fun submit(booking: Booking, requestId: String): Task<Map<String, String>> =
        FirebaseGateway.get().userId().onSuccessTask { uid ->
            val account = database.collection("googleMailAccounts").document(uid)
            val request = account.collection("googleMailRequests").document(requestId)
            val reference = reference(requestId)
            val payload = booking.payload(requestId)
            database.runTransaction { transaction ->
                val existing = transaction.get(request)
                if (existing.exists()) {
                    check(payload.all { (key, value) ->
                        val saved = existing.get(key)
                        if (value is Int) saved == value.toLong() else saved == value
                    })
                } else {
                    transaction.set(account, mapOf(
                        "lastRequestId" to requestId, "lastRequestAt" to FieldValue.serverTimestamp(),
                    ))
                    transaction.set(request, payload + mapOf(
                        "mailStatus" to "PENDING", "createdAt" to FieldValue.serverTimestamp(),
                    ))
                }
                mapOf("reference" to reference, "status" to (existing.getString("mailStatus") ?: "PENDING"))
            }
        }

    fun list(): Task<List<RequestSummary>> = FirebaseGateway.get().userId().onSuccessTask { uid ->
        database.collection("googleMailAccounts").document(uid).collection("googleMailRequests")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(30).get(Source.SERVER)
            .continueWith { task ->
                task.result.documents.map { document ->
                    RequestSummary(reference(document.id), document.getString("mailStatus"),
                        document.getString("service"), document.getString("departure"), document.getString("arrival"),
                        document.getString("date"), document.getString("time"),
                        document.getString("pricingType"), document.getString("pricingSector"),
                        document.getLong("pricingZone")?.toInt(), document.getLong("passengers")?.toInt(),
                        document.getLong("pricePerPassenger")?.toInt(), document.getLong("priceTotal")?.toInt(),
                        document.getTimestamp("createdAt")?.toDate()?.time,
                        document.getTimestamp("mailStatusUpdatedAt")?.toDate()?.time)
                }
            }
    }

    fun requestDeletion(): Task<Unit> = FirebaseGateway.get().userId().onSuccessTask { uid ->
        val request = database.collection("googleMailAccounts").document(uid)
            .collection("privacy").document("deletion")
        database.runTransaction { transaction ->
            if (!transaction.get(request).exists()) {
                transaction.set(request, mapOf("createdAt" to FieldValue.serverTimestamp()))
            }
            Unit
        }
    }

    private fun reference(requestId: String) =
        "NP-" + requestId.replace("-", "").take(16).uppercase(Locale.ROOT)
}
