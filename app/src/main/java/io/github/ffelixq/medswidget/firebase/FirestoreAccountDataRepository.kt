package io.github.ffelixq.medswidget.firebase

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import io.github.ffelixq.medswidget.data.AccountDataRepository
import kotlinx.coroutines.tasks.await

class FirestoreAccountDataRepository(
    private val firestore: FirebaseFirestore,
) : AccountDataRepository {
    override suspend fun deleteAll(uid: String) {
        val user = FirestorePaths.user(firestore, uid)
        val collections =
            listOf(
                user.collection(FirestorePaths.SETTINGS),
                user.collection(FirestorePaths.MEDICINES),
                user.collection(FirestorePaths.DOSE_STATES),
                user.collection(FirestorePaths.DOSE_EVENTS),
                user.collection(FirestorePaths.COUNTDOWN_STATES),
                user.collection(FirestorePaths.COUNTDOWN_EVENTS),
            )
        collections.forEach { deleteCollection(it) }
        user.delete().await()
    }

    private suspend fun deleteCollection(collection: CollectionReference) {
        while (true) {
            val documents =
                collection
                    .limit(BATCH_LIMIT.toLong())
                    .get(Source.SERVER)
                    .await()
                    .documents
            if (documents.isEmpty()) return
            val batch = firestore.batch()
            documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    private companion object {
        const val BATCH_LIMIT = 400
    }
}
