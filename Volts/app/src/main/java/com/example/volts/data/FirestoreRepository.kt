package com.example.volts.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object FirestoreRepository {

    private val db = FirebaseFirestore.getInstance()

    fun registrarAccion(
        accion: String,
        resultado: String = "ENVIADO",
        onSuccess: (() -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        val evento = hashMapOf(
            "robotId" to "VOLTS-01",
            "tipo" to "MOVIMIENTO",
            "accion" to accion,
            "resultado" to resultado,
            "fechaHora" to FieldValue.serverTimestamp()
        )

        db.collection("eventos")
            .add(evento)
            .addOnSuccessListener { documento ->
                Log.d(
                    "FIRESTORE",
                    "Acción guardada: ${documento.id}"
                )

                onSuccess?.invoke()
            }
            .addOnFailureListener { error ->
                Log.e(
                    "FIRESTORE",
                    "Error al guardar acción",
                    error
                )

                onError?.invoke(error)
            }
    }
}