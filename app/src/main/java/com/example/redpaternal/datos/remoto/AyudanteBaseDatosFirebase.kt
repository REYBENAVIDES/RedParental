package com.example.redpaternal.datos.remoto

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.redpaternal.datos.modelo.Dispositivo
import com.example.redpaternal.datos.modelo.FiltroSitio
import com.example.redpaternal.datos.modelo.RouterInfo
import com.example.redpaternal.datos.modelo.TipoDispositivo
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date
import kotlin.random.Random

class AyudanteBaseDatosFirebase(private val context: Context) {

    private val auth: FirebaseAuth = Firebase.auth
    private val db = Firebase.firestore

    sealed class Resultado<out T> {
        data class Exito<out T>(val datos: T) : Resultado<T>()
        data class Error(val mensaje: String, val excepcion: Exception? = null) : Resultado<Nothing>()
    }

    private fun hayInternet(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private val fuenteDatos: Source
        get() = if (hayInternet()) Source.DEFAULT else Source.CACHE

    fun obtenerUsuarioActual(): FirebaseUser? {
        return auth.currentUser
    }

    suspend fun obtenerNombreGuardado(): String? {
        val usuario = auth.currentUser ?: return null
        return try {
            val snapshot = db.collection("usuarios")
                .document(usuario.uid)
                .get(fuenteDatos)
                .await()
            snapshot.getString("nombreMostrar")
        } catch (e: Exception) {
            null
        }
    }

    fun cerrarSesion() {
        auth.signOut()
    }

    fun escucharRoutersEnTiempoReal(
        onDatos: (List<RouterInfo>) -> Unit,
        onError: (String) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration? {
        val usuario = auth.currentUser
        if (usuario == null) {
            onError("No autenticado")
            return null
        }

        return db.collection("usuarios")
            .document(usuario.uid)
            .collection("routers")
            .orderBy("fechaConfiguracion", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { instantaneas, error ->
                if (error != null) {
                    onError(error.message ?: "Error desconocido al escuchar routers")
                    return@addSnapshotListener
                }

                if (instantaneas != null) {
                    val listaMapeada = instantaneas.documents.map { doc ->
                        RouterInfo(
                            nombreRed = doc.getString("nombreRed") ?: "Sin Nombre",
                            macRouter = doc.getString("direccionMac") ?: "",
                            ipPuertaEnlace = doc.getString("ipPuertaEnlace") ?: "",
                            claveAdmin = doc.getString("claveAdmin") ?: "",
                            ipLocal = "0.0.0.0", // Valor por defecto visual
                            frecuencia = 0,
                            intensidadSenal = 0,
                            modelo = doc.getString("modelo") ?: "Desconocido",
                            marca = doc.getString("marca") ?: "",
                            numeroSerie = doc.getString("numeroSerie"),
                            nextDnsProfileId = doc.getString("nextDnsProfileId"),
                            fechaConfiguracion = doc.getTimestamp("fechaConfiguracion")?.toDate()?.time
                        )
                    }
                    onDatos(listaMapeada)
                } else {
                    onDatos(emptyList())
                }
            }
    }

    fun escucharSitiosVisitados(
        macRouter: String,
        macDispositivo: String,
        ordenarPor: String,
        onDatos: (List<com.example.redpaternal.datos.modelo.SitioVisitado>) -> Unit,
        onError: (String) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration? {
        val usuario = auth.currentUser
        if (usuario == null) {
            onError("No autenticado")
            return null
        }

        var registrationSitios: com.google.firebase.firestore.ListenerRegistration? = null
        val deviceId = macDispositivo.replace(":", "-").uppercase()

        val queryRouter = db.collection("usuarios")
            .document(usuario.uid)
            .collection("routers")
            .whereEqualTo("direccionMac", macRouter)

        val registrationRouter = queryRouter.addSnapshotListener { snapshotRouter, errorRouter ->
            if (errorRouter != null) {
                onError(errorRouter.message ?: "Error buscando router")
                return@addSnapshotListener
            }

            if (snapshotRouter != null && !snapshotRouter.isEmpty) {
                val routerDoc = snapshotRouter.documents[0]

                registrationSitios?.remove()

                // Escuchamos la subcolección 'sitios' del dispositivo específico
                registrationSitios = routerDoc.reference
                    .collection("dispositivos")
                    .document(deviceId)
                    .collection("sitios")
                    .orderBy(ordenarPor, com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshotSitios, errorSitios ->
                        if (errorSitios != null) {
                            onError(errorSitios.message ?: "Error leyendo sitios")
                            return@addSnapshotListener
                        }

                        if (snapshotSitios != null) {
                            val lista = snapshotSitios.toObjects(com.example.redpaternal.datos.modelo.SitioVisitado::class.java)
                            onDatos(lista)
                        } else {
                            onDatos(emptyList())
                        }
                    }
            } else {
                onDatos(emptyList())
            }
        }

        return object : com.google.firebase.firestore.ListenerRegistration {
            override fun remove() {
                registrationRouter.remove()
                registrationSitios?.remove()
            }
        }
    }

    fun escucharDispositivos(
        macRouter: String,
        onDatos: (List<Dispositivo>) -> Unit,
        onError: (String) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration? {
        val usuario = auth.currentUser
        if (usuario == null) {
            onError("No autenticado")
            return null
        }

        var registrationDispositivos: com.google.firebase.firestore.ListenerRegistration? = null

        val queryRouter = db.collection("usuarios")
            .document(usuario.uid)
            .collection("routers")
            .whereEqualTo("direccionMac", macRouter)

        val registrationRouter = queryRouter.addSnapshotListener { snapshotRouter, errorRouter ->
            if (errorRouter != null) {
                onError(errorRouter.message ?: "Error buscando router")
                return@addSnapshotListener
            }

            if (snapshotRouter != null && !snapshotRouter.isEmpty) {
                val routerDoc = snapshotRouter.documents[0]

                registrationDispositivos?.remove()

                registrationDispositivos = routerDoc.reference.collection("dispositivos")
                    .orderBy("ultimaActualizacionSitios", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshotDisp, errorDisp ->
                        if (errorDisp != null) {
                            onError(errorDisp.message ?: "Error leyendo dispositivos")
                            return@addSnapshotListener
                        }

                        if (snapshotDisp != null) {
                            val lista = snapshotDisp.documents.map { doc ->
                                Dispositivo(
                                    macAddress = doc.getString("macAddress") ?: "",
                                    nombre = doc.getString("nombre") ?: "Desconocido",
                                    ipAddress = doc.getString("ipAddress") ?: "",
                                    tiempoConectadoHoy = (doc.getLong("tiempoConectadoHoy") ?: 0L).toString(),
                                    estaConectado = doc.getBoolean("estaConectado") ?: false,
                                    tipo = try {
                                        com.example.redpaternal.datos.modelo.TipoDispositivo.valueOf(doc.getString("tipo") ?: "DESCONOCIDO")
                                    } catch (e: Exception) {
                                        com.example.redpaternal.datos.modelo.TipoDispositivo.DESCONOCIDO
                                    },
                                    ultimaActualizacionSitios = doc.getDate("ultimaActualizacionSitios")
                                )
                            }
                            onDatos(lista)
                        } else {
                            onDatos(emptyList())
                        }
                    }
            } else {
                onDatos(emptyList())
            }
        }

        return object : com.google.firebase.firestore.ListenerRegistration {
            override fun remove() {
                registrationRouter.remove()
                registrationDispositivos?.remove()
            }
        }
    }

    fun escucharFiltros(
        macRouter: String,
        onDatos: (List<FiltroSitio>) -> Unit,
        onError: (String) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration? {
        val usuario = auth.currentUser
        if (usuario == null) {
            onError("No autenticado")
            return null
        }

        var registrationFiltros: com.google.firebase.firestore.ListenerRegistration? = null

        val queryRouter = db.collection("usuarios")
            .document(usuario.uid)
            .collection("routers")
            .whereEqualTo("direccionMac", macRouter)

        val registrationRouter = queryRouter.addSnapshotListener { snapshotRouter, errorRouter ->
            if (errorRouter != null) {
                onError(errorRouter.message ?: "Error buscando router")
                return@addSnapshotListener
            }

            if (snapshotRouter != null && !snapshotRouter.isEmpty) {
                val routerDoc = snapshotRouter.documents[0]

                registrationFiltros?.remove()

                registrationFiltros = routerDoc.reference.collection("filtros")
                    .orderBy("fechaCreacion", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshotFiltros, errorFiltros ->
                        if (errorFiltros != null) {
                            onError(errorFiltros.message ?: "Error leyendo filtros")
                            return@addSnapshotListener
                        }

                        if (snapshotFiltros != null) {
                            val lista = snapshotFiltros.toObjects(FiltroSitio::class.java)
                            onDatos(lista)
                        } else {
                            onDatos(emptyList())
                        }
                    }
            } else {
                onDatos(emptyList())
            }
        }

        return object : com.google.firebase.firestore.ListenerRegistration {
            override fun remove() {
                registrationRouter.remove()
                registrationFiltros?.remove()
            }
        }
    }

    suspend fun guardarFiltro(macRouter: String, filtro: FiltroSitio): Resultado<Boolean> {
        if (!hayInternet()) return Resultado.Error("No hay conexión a internet. No se guardaron los cambios.")

        val usuario = auth.currentUser ?: return Resultado.Error("No autenticado")
        try {
            val queryRouter = db.collection("usuarios").document(usuario.uid)
                .collection("routers").whereEqualTo("direccionMac", macRouter).get().await()

            if (queryRouter.isEmpty) return Resultado.Error("Router no encontrado")

            val filtrosRef = queryRouter.documents[0].reference.collection("filtros")

            val datos = hashMapOf(
                "nombre" to filtro.nombre,
                "tipoAlcance" to filtro.tipoAlcance,
                "tipoAccion" to filtro.tipoAccion,
                "listaCategorias" to filtro.listaCategorias,
                "listaDispositivos" to filtro.listaDispositivos,
                "listaSitios" to filtro.listaSitios,
                "esSiempreActivo" to filtro.esSiempreActivo,
                "horaInicio" to filtro.horaInicio,
                "horaFin" to filtro.horaFin,
                "diasSemana" to filtro.diasSemana,
                "estaActivo" to filtro.estaActivo,
                "fechaModificacion" to FieldValue.serverTimestamp()
            )

            if (filtro.id.isEmpty()) {
                datos["fechaCreacion"] = FieldValue.serverTimestamp()
                val docRef = filtrosRef.add(datos).await()
                docRef.update("id", docRef.id)
            } else {
                datos["id"] = filtro.id
                filtrosRef.document(filtro.id).update(datos).await()
            }
            return Resultado.Exito(true)
        } catch (e: Exception) {
            return Resultado.Error("Error guardando filtro: ${e.message}")
        }
    }

    suspend fun eliminarFiltro(macRouter: String, idFiltro: String): Resultado<Boolean> {
        if (!hayInternet()) return Resultado.Error("Se requiere internet para eliminar filtros.")

        val usuario = auth.currentUser ?: return Resultado.Error("No autenticado")
        try {
            val queryRouter = db.collection("usuarios").document(usuario.uid)
                .collection("routers").whereEqualTo("direccionMac", macRouter).get().await()

            if (queryRouter.isEmpty) return Resultado.Error("Router no encontrado")

            queryRouter.documents[0].reference.collection("filtros").document(idFiltro).delete().await()
            return Resultado.Exito(true)
        } catch (e: Exception) {
            return Resultado.Error("Error eliminando: ${e.message}")
        }
    }

    suspend fun cambiarEstadoFiltro(macRouter: String, idFiltro: String, activo: Boolean) {
        if (!hayInternet()) return // Simplemente no hace nada si no hay red

        val usuario = auth.currentUser ?: return
        try {
            val queryRouter = db.collection("usuarios").document(usuario.uid)
                .collection("routers").whereEqualTo("direccionMac", macRouter).get().await()

            if (!queryRouter.isEmpty) {
                queryRouter.documents[0].reference.collection("filtros")
                    .document(idFiltro)
                    .update("estaActivo", activo).await()
            }
        } catch (e: Exception) { Log.e("AyudanteDB", "Error toggle filtro: ${e.message}") }
    }

    suspend fun autenticarConGoogle(cuenta: GoogleSignInAccount): Resultado<FirebaseUser> {
        if (!hayInternet()) return Resultado.Error("No hay conexión para autenticarse.")
        return try {
            val credencial: AuthCredential = GoogleAuthProvider.getCredential(cuenta.idToken, null)
            val resultadoAuth = auth.signInWithCredential(credencial).await()
            val usuario = resultadoAuth.user
            if (usuario != null) Resultado.Exito(usuario) else Resultado.Error("Usuario nulo")
        } catch (e: Exception) {
            Resultado.Error("Error Auth: ${e.message}", e)
        }
    }

    suspend fun verificarOActualizarUsuario(usuario: FirebaseUser): Resultado<Boolean> {
        // Aquí permitimos lectura local (Source.DEFAULT maneja caché si es necesario)
        return try {
            val docRef = db.collection("usuarios").document(usuario.uid)
            val snapshot = docRef.get(fuenteDatos).await()

            if (snapshot.exists()) {
                if (hayInternet()) {
                    docRef.update("ultimoAcceso", FieldValue.serverTimestamp()).await()
                }
                Resultado.Exito(true)
            } else {
                if (!hayInternet()) return Resultado.Error("Se requiere internet para el primer registro.")

                val datosUsuario = hashMapOf(
                    "idUsuario" to usuario.uid,
                    "correo" to usuario.email,
                    "nombreMostrar" to usuario.displayName,
                    "urlFoto" to usuario.photoUrl?.toString(),
                    "fechaCreacion" to FieldValue.serverTimestamp(),
                    "ultimoAcceso" to FieldValue.serverTimestamp()
                )
                docRef.set(datosUsuario).await()
                Resultado.Exito(false)
            }
        } catch (e: Exception) {
            Resultado.Error("Error Usuario: ${e.message}", e)
        }
    }

    suspend fun guardarRouter(router: RouterInfo): Resultado<Unit> {
        if (!hayInternet()) return Resultado.Error("Conéctate a internet para guardar el router.")

        val usuario = auth.currentUser ?: return Resultado.Error("No hay sesión")
        return try {
            val datosRouter = hashMapOf(
                "nombreRed" to router.nombreRed,
                "ipPuertaEnlace" to router.ipPuertaEnlace,
                "modelo" to router.modelo,
                "marca" to router.marca,
                "direccionMac" to router.macRouter,
                "numeroSerie" to (router.numeroSerie ?: ""),
                "claveAdmin" to router.claveAdmin,
                "fechaConfiguracion" to FieldValue.serverTimestamp(),
                "nextDnsProfileId" to (router.nextDnsProfileId ?: "")
            )

            val coleccionRouters = db.collection("usuarios")
                .document(usuario.uid)
                .collection("routers")

            val busqueda = coleccionRouters.whereEqualTo("direccionMac", router.macRouter).get().await()

            if (!busqueda.isEmpty) {
                coleccionRouters.document(busqueda.documents[0].id).update(datosRouter).await()
            } else {
                coleccionRouters.add(datosRouter).await()
            }
            Resultado.Exito(Unit)
        } catch (e: Exception) {
            Resultado.Error("Error Router: ${e.message}", e)
        }
    }

    suspend fun guardarEstadoActualEnLote(macRouter: String, listaActualizada: List<Dispositivo>) {
        if (!hayInternet()) return // No guardamos historial si no hay red

        val usuario = auth.currentUser ?: return
        val batch = db.batch()

        try {
            val routerQuery = db.collection("usuarios")
                .document(usuario.uid)
                .collection("routers")
                .whereEqualTo("direccionMac", macRouter)
                .get()
                .await()

            if (routerQuery.isEmpty) return
            val routerRef = routerQuery.documents[0].reference
            val coleccionDispositivos = routerRef.collection("dispositivos")

            val snapshotActual = coleccionDispositivos.get().await()
            val mapaNombresExistentes = snapshotActual.documents.associate {
                it.id to (it.getString("nombre") ?: "")
            }
            val nombresUsadosEnBD = snapshotActual.documents.mapNotNull { it.getString("nombre") }.toMutableSet()

            for (dispositivo in listaActualizada) {
                val deviceId = dispositivo.macAddress.replace(":", "")
                val docRef = coleccionDispositivos.document(deviceId)

                var nombreFinal = dispositivo.nombre
                val nombreYaGuardado = mapaNombresExistentes[deviceId]

                if (!nombreYaGuardado.isNullOrEmpty()) {
                    nombreFinal = nombreYaGuardado
                } else {
                    if (nombresUsadosEnBD.contains(nombreFinal)) {
                        val sufijo = generarSufijoAleatorio()
                        nombreFinal = "$nombreFinal-$sufijo"
                    }
                    nombresUsadosEnBD.add(nombreFinal)
                }

                val tiempoLong = dispositivo.tiempoConectadoHoy.toLongOrNull() ?: 0L

                val datosActualizar = hashMapOf(
                    "macAddress" to dispositivo.macAddress,
                    "ipAddress" to dispositivo.ipAddress,
                    "fabricante" to dispositivo.fabricante,
                    "estaConectado" to dispositivo.estaConectado,
                    "tipo" to dispositivo.tipo.name,
                    "nombre" to nombreFinal,
                    "fechaUltimaConexion" to FieldValue.serverTimestamp(),
                    "tiempoConectadoHoy" to tiempoLong
                )

                batch.set(docRef, datosActualizar, SetOptions.merge())
            }

            batch.commit().await()
            Log.d("AyudanteDB", "Lote actualizado con validación de nombres.")

        } catch (e: Exception) {
            Log.e("AyudanteDB", "Error guardando lote: ${e.message}")
        }
    }

    suspend fun actualizarEstadoBloqueo(macRouter: String, macDispositivo: String, nombreSitio: String, esBloqueado: Boolean) {
        if (!hayInternet()) {
            Log.e("AyudanteDB", "Sin internet: No se pudo actualizar bloqueo")
            return
        }

        val usuario = auth.currentUser ?: return
        try {
            val deviceId = macDispositivo.replace(":", "-").uppercase()
            val sitioId = nombreSitio.replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()

            val queryRouter = db.collection("usuarios").document(usuario.uid)
                .collection("routers").whereEqualTo("direccionMac", macRouter).get().await()

            if (!queryRouter.isEmpty) {
                val routerRef = queryRouter.documents[0].reference

                val sitioRef = routerRef.collection("dispositivos").document(deviceId)
                    .collection("sitios").document(sitioId)

                sitioRef.update("esBloqueado", esBloqueado).await()
                Log.d("AyudanteDB", "🔒 BD Actualizada: $nombreSitio -> Bloqueado: $esBloqueado")
            }
        } catch (e: Exception) {
            Log.e("AyudanteDB", "Error actualizando bloqueo en BD: ${e.message}")
        }
    }

    suspend fun bloquearAccesoInternetDispositivo(macRouter: String, macDispositivo: String, bloquear: Boolean) {
        if (!hayInternet()) return
        val usuario = auth.currentUser ?: return
        try {
            val deviceId = macDispositivo.replace(":", "-").uppercase()

            val query = db.collection("usuarios").document(usuario.uid)
                .collection("routers").whereEqualTo("direccionMac", macRouter).get().await()

            if (!query.isEmpty) {
                val routerRef = query.documents[0].reference

                routerRef.collection("dispositivos").document(deviceId)
                    .update("estaBloqueado", bloquear).await()

                Log.d("AyudanteDB", "🔒 Estado de bloqueo para $macDispositivo cambiado a: $bloquear")
            }
        } catch (e: Exception) {
            Log.e("AyudanteDB", "Error al actualizar campo estaBloqueado: ${e.message}")
        }
    }

    suspend fun obtenerDispositivosSoloLectura(macRouter: String): List<Dispositivo> {
        val usuario = auth.currentUser ?: return emptyList()
        try {
            val routerQuery = db.collection("usuarios")
                .document(usuario.uid)
                .collection("routers")
                .whereEqualTo("direccionMac", macRouter)
                .get(fuenteDatos) // Lectura inteligente (Caché si offline)
                .await()

            if (routerQuery.isEmpty) return emptyList()
            val routerRef = routerQuery.documents[0].reference

            val snapshot = routerRef.collection("dispositivos")
                .get(fuenteDatos)
                .await()

            return snapshot.documents.map { doc ->
                Dispositivo(
                    macAddress = doc.getString("macAddress") ?: "",
                    nombre = doc.getString("nombre") ?: "Desconocido",
                    ipAddress = doc.getString("ipAddress") ?: "",
                    tiempoConectadoHoy = (doc.getLong("tiempoConectadoHoy") ?: 0L).toString(),
                    estaConectado = doc.getBoolean("estaConectado") ?: false,
                    tipo = TipoDispositivo.valueOf(doc.getString("tipo") ?: "DESCONOCIDO"),
                    ultimaActualizacionSitios = doc.getDate("ultimaActualizacionSitios")
                )
            }
        } catch (e: Exception) {
            return emptyList()
        }
    }

    suspend fun obtenerIdNextDnsPorMac(macRouter: String): String? {
        val usuario = auth.currentUser ?: return null
        return try {
            val query = db.collection("usuarios").document(usuario.uid)
                .collection("routers")
                .whereEqualTo("direccionMac", macRouter)
                .get(fuenteDatos)
                .await()

            if (!query.isEmpty) {
                val id = query.documents[0].getString("nextDnsProfileId")
                id
            } else null
        } catch (e: Exception) { null }
    }

    private fun esMismoDia(d1: Date, d2: Date): Boolean {
        val c1 = Calendar.getInstance().apply { time = d1 }
        val c2 = Calendar.getInstance().apply { time = d2 }
        return c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR) &&
                c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
    }

    private fun generarSufijoAleatorio(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        return (1..3)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }

    suspend fun obtenerContadoresDispositivo(macRouter: String, macDispositivo: String): Pair<Int, Long> {
        val usuario = auth.currentUser ?: return Pair(0, 0L)
        try {
            val queryRouter = db.collection("usuarios").document(usuario.uid)
                .collection("routers")
                .whereEqualTo("direccionMac", macRouter)
                .get(fuenteDatos) // Prioriza caché si no hay red
                .await()

            if (queryRouter.isEmpty) return Pair(0, 0L)
            val routerRef = queryRouter.documents[0].reference
            val filtrosRef = routerRef.collection("filtros")

            val filtrosGlobalesSnap = filtrosRef
                .whereEqualTo("tipoAlcance", "ROUTER")
                .get(fuenteDatos).await()

            val filtrosEspecificosSnap = filtrosRef
                .whereEqualTo("tipoAlcance", "DISPOSITIVO")
                .whereArrayContains("listaDispositivos", macDispositivo)
                .get(fuenteDatos).await()

            val totalFiltrosAplicables = filtrosGlobalesSnap.size() + filtrosEspecificosSnap.size()

            val deviceId = macDispositivo.replace(":", "-").uppercase()

            val deviceDoc = routerRef.collection("dispositivos").document(deviceId)
                .get(fuenteDatos).await()

            val cantidadBloqueos = deviceDoc.getLong("cantidadBloqueosHoy") ?: 0L

            return Pair(totalFiltrosAplicables, cantidadBloqueos)

        } catch (e: Exception) {
            Log.e("AyudanteDB", "Error obteniendo contadores: ${e.message}")
            return Pair(0, 0L)
        }
    }
}