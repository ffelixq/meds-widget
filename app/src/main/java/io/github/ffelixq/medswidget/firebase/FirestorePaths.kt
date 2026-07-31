package io.github.ffelixq.medswidget.firebase

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

object FirestorePaths {
    const val USERS = "users"
    const val SETTINGS = "settings"
    const val SETTINGS_DOCUMENT = "preferences"
    const val MEDICINES = "medicines"
    const val DOSE_STATES = "doseStates"
    const val DOSE_EVENTS = "doseEvents"
    const val COUNTDOWN_STATES = "countdownStates"
    const val COUNTDOWN_EVENTS = "countdownEvents"

    fun user(
        firestore: FirebaseFirestore,
        uid: String,
    ): DocumentReference = firestore.collection(USERS).document(uid)

    fun settings(
        firestore: FirebaseFirestore,
        uid: String,
    ): DocumentReference = user(firestore, uid).collection(SETTINGS).document(SETTINGS_DOCUMENT)

    fun medicines(
        firestore: FirebaseFirestore,
        uid: String,
    ): CollectionReference = user(firestore, uid).collection(MEDICINES)

    fun doseStates(
        firestore: FirebaseFirestore,
        uid: String,
    ): CollectionReference = user(firestore, uid).collection(DOSE_STATES)

    fun doseEvents(
        firestore: FirebaseFirestore,
        uid: String,
    ): CollectionReference = user(firestore, uid).collection(DOSE_EVENTS)

    fun countdownStates(
        firestore: FirebaseFirestore,
        uid: String,
    ): CollectionReference = user(firestore, uid).collection(COUNTDOWN_STATES)

    fun countdownEvents(
        firestore: FirebaseFirestore,
        uid: String,
    ): CollectionReference = user(firestore, uid).collection(COUNTDOWN_EVENTS)
}
