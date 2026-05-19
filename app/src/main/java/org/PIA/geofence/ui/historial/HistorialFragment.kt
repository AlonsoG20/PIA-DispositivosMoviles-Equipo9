package org.PIA.geofence.ui.historial

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.PIA.geofence.R
import org.PIA.geofence.data.Viaje
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class HistorialFragment : Fragment(R.layout.fragment_historial) {

    private lateinit var rvHistorial: RecyclerView
    private lateinit var tvContadorHoy: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var etSearch: EditText
    private lateinit var tilSearch: View
    private lateinit var layoutFilters: View
    private lateinit var chipGroupRole: ChipGroup
    private lateinit var chipGroupSort: ChipGroup
    private lateinit var btnResetFilters: View
    private lateinit var btnMoreFilters: View
    private lateinit var adapter: ViajeAdapter
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var todosLosViajes = listOf<Viaje>()
    private var currentRole = ""
    private var currentSortMode = "recent" // "recent", "distance", "fuel"
    private var currentRoleFilter = "all" // "all", "chofer", "despachador"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvHistorial = view.findViewById(R.id.rvHistorial)
        tvContadorHoy = view.findViewById(R.id.tvContadorHoy)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        etSearch = view.findViewById(R.id.etSearch)
        tilSearch = view.findViewById(R.id.tilSearch)
        layoutFilters = view.findViewById(R.id.layoutFilters)
        chipGroupRole = view.findViewById(R.id.chipGroupRole)
        chipGroupSort = view.findViewById(R.id.chipGroupSort)
        btnResetFilters = view.findViewById(R.id.btnResetFilters)
        btnMoreFilters = view.findViewById(R.id.btnMoreFilters)

        setupRecyclerView()
        setupListeners()
        cargarHistorial()
    }

    private fun setupRecyclerView() {
        adapter = ViajeAdapter(emptyList()) { viaje ->
            mostrarDetalleYImprimir(viaje)
        }
        rvHistorial.layoutManager = LinearLayoutManager(requireContext())
        rvHistorial.adapter = adapter
    }

    private fun getDisplayEstado(estado: String): String {
        return if (estado.equals("completada", true) || estado.equals("finalizada", true)) {
            "Completado"
        } else {
            "Abandonado"
        }
    }

    private fun mostrarDetalleYImprimir(viaje: Viaje) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_detalle_viaje, null)
        
        val displayEstado = getDisplayEstado(viaje.estado)
        val tvEstado = dialogView.findViewById<TextView>(R.id.tvDetalleEstado)
        tvEstado.text = displayEstado
        if (displayEstado == "Completado") {
            tvEstado.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E8F5E9"))
            tvEstado.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            tvEstado.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFEBEE"))
            tvEstado.setTextColor(Color.parseColor("#C62828"))
        }

        dialogView.findViewById<TextView>(R.id.tvDetalleTitulo).text = viaje.titulo
        dialogView.findViewById<TextView>(R.id.tvDetalleChofer).text = "Chofer: ${viaje.nombreChofer}"
        dialogView.findViewById<TextView>(R.id.tvDetalleDespachador).text = "Asignado por: ${viaje.nombreDespachador}"
        dialogView.findViewById<TextView>(R.id.tvDetalleUnidad).text = "Unidad: U-${viaje.numeroEconomico} (${viaje.placaUnidad})"
        dialogView.findViewById<TextView>(R.id.tvDetalleDistancia).text = "Distancia: ${viaje.distancia}"
        dialogView.findViewById<TextView>(R.id.tvDetalleCombustible).text = "Combustible estimado: ${viaje.combustible}L"
        dialogView.findViewById<TextView>(R.id.tvDetalleCosto).text = "Costo: $${viaje.costo}"
        dialogView.findViewById<TextView>(R.id.tvDetalleParadas).text = "Paradas realizadas: ${viaje.cantidadParadas}"

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        dialogView.findViewById<TextView>(R.id.tvDetalleFechaInicio).text = "Fecha Inicio: ${viaje.fechaInicio?.toDate()?.let { sdf.format(it) } ?: "---"}"
        dialogView.findViewById<TextView>(R.id.tvDetalleFechaFin).text = "Fecha Fin: ${viaje.fechaFin?.toDate()?.let { sdf.format(it) } ?: "---"}"

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btnCerrar).setOnClickListener { dialog.dismiss() }
        
        val btnImprimir = dialogView.findViewById<Button>(R.id.btnImprimir)
        if (currentRole == "gerente") {
            btnImprimir.visibility = View.VISIBLE
            btnImprimir.setOnClickListener {
                imprimirReporte(viaje)
                dialog.dismiss()
            }
        } else {
            btnImprimir.visibility = View.GONE
        }

        dialog.show()
    }

    private fun imprimirReporte(viaje: Viaje) {
        val printManager = requireContext().getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "${getString(R.string.app_name)} Reporte ${viaje.id}"

        printManager.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }

                val info = PrintDocumentInfo.Builder("reporte_viaje.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas
                val paint = Paint()
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

                // Dibujar Logo
                try {
                    val bitmap = BitmapFactory.decodeResource(resources, R.drawable.logo_app)
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 80, 80, true)
                    canvas.drawBitmap(scaledBitmap, 465f, 40f, paint)
                } catch (e: Exception) { e.printStackTrace() }

                var y = 80f
                paint.textSize = 24f
                paint.isFakeBoldText = true
                canvas.drawText("REPORTE DE VIAJE", 50f, y, paint)
                
                y += 25f
                paint.textSize = 16f
                paint.color = Color.DKGRAY
                canvas.drawText("GEOFENCE", 50f, y, paint)
                
                paint.color = Color.BLACK
                y += 45f
                paint.textSize = 14f
                paint.isFakeBoldText = false
                canvas.drawText("ID del Viaje: ${viaje.id}", 50f, y, paint)
                
                y += 40f
                paint.isFakeBoldText = true
                paint.textSize = 16f
                canvas.drawText("Información General", 50f, y, paint)
                canvas.drawLine(50f, y + 5, 250f, y + 5, paint)
                
                y += 30f
                paint.textSize = 14f
                paint.isFakeBoldText = false
                canvas.drawText("Título: ${viaje.titulo}", 60f, y, paint)
                
                y += 20f
                val displayEstado = getDisplayEstado(viaje.estado)
                paint.color = if (displayEstado == "Completado") Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
                paint.isFakeBoldText = true
                canvas.drawText("Estado: ${displayEstado.uppercase()}", 60f, y, paint)
                
                paint.color = Color.BLACK
                y += 40f
                paint.isFakeBoldText = true
                canvas.drawText("Personal y Unidad", 50f, y, paint)
                canvas.drawLine(50f, y + 5, 250f, y + 5, paint)
                
                y += 30f
                paint.isFakeBoldText = false
                canvas.drawText("Chofer: ${viaje.nombreChofer}", 60f, y, paint)
                y += 20f
                canvas.drawText("Despachador: ${viaje.nombreDespachador}", 60f, y, paint)
                y += 20f
                canvas.drawText("Unidad: U-${viaje.numeroEconomico} (Placa: ${viaje.placaUnidad})", 60f, y, paint)

                y += 40f
                paint.isFakeBoldText = true
                canvas.drawText("Métricas del Viaje", 50f, y, paint)
                canvas.drawLine(50f, y + 5, 250f, y + 5, paint)
                
                y += 30f
                paint.isFakeBoldText = false
                canvas.drawText("Distancia Recorrida: ${viaje.distancia}", 60f, y, paint)
                y += 20f
                canvas.drawText("Combustible Consumido: ${viaje.combustible} L", 60f, y, paint)
                y += 20f
                canvas.drawText("Costo del Viaje: $${viaje.costo}", 60f, y, paint)
                y += 20f
                canvas.drawText("Cantidad de Paradas: ${viaje.cantidadParadas}", 60f, y, paint)

                y += 40f
                paint.isFakeBoldText = true
                canvas.drawText("Tiempos", 50f, y, paint)
                canvas.drawLine(50f, y + 5, 250f, y + 5, paint)
                
                y += 30f
                paint.isFakeBoldText = false
                canvas.drawText("Inicio: ${viaje.fechaInicio?.toDate()?.let { sdf.format(it) } ?: "---"}", 60f, y, paint)
                y += 20f
                canvas.drawText("Fin: ${viaje.fechaFin?.toDate()?.let { sdf.format(it) } ?: "---"}", 60f, y, paint)

                pdfDocument.finishPage(page)
                try {
                    pdfDocument.writeTo(FileOutputStream(destination.fileDescriptor))
                } catch (e: Exception) { e.printStackTrace() } finally {
                    pdfDocument.close()
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }
        }, null)
    }

    private fun setupListeners() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                aplicarFiltrosYOrden()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        chipGroupRole.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull()
            currentRoleFilter = when (checkedId) {
                R.id.chipRoleChofer -> "chofer"
                R.id.chipRoleDespachador -> "despachador"
                else -> "all"
            }
            actualizarHintBusqueda()
            aplicarFiltrosYOrden()
        }

        chipGroupSort.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull()
            currentSortMode = when (checkedId) {
                R.id.chipDistance -> "distance"
                else -> "recent"
            }
            aplicarFiltrosYOrden()
        }

        btnMoreFilters.setOnClickListener { showMoreFiltersMenu() }
        btnResetFilters.setOnClickListener { resetFilters() }
    }

    private fun actualizarHintBusqueda() {
        val hint = when (currentRoleFilter) {
            "chofer" -> "Buscar por nombre de chofer..."
            "despachador" -> "Buscar por nombre de despachador..."
            else -> "Buscar por chofer, unidad o despachador..."
        }
        etSearch.hint = hint
    }

    private fun showMoreFiltersMenu() {
        val popup = PopupMenu(requireContext(), btnMoreFilters)
        popup.menu.add(0, 1, 0, "Más Recientes")
        popup.menu.add(0, 2, 1, "Más Distancia")
        popup.menu.add(0, 3, 2, "Más Combustible")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { currentSortMode = "recent"; chipGroupSort.check(R.id.chipRecent) }
                2 -> { currentSortMode = "distance"; chipGroupSort.check(R.id.chipDistance) }
                3 -> { currentSortMode = "fuel"; chipGroupSort.clearCheck() }
            }
            aplicarFiltrosYOrden()
            true
        }
        popup.show()
    }

    private fun resetFilters() {
        etSearch.setText("")
        currentSortMode = "recent"
        currentRoleFilter = "all"
        chipGroupSort.check(R.id.chipRecent)
        chipGroupRole.check(R.id.chipRoleAll)
        aplicarFiltrosYOrden()
    }

    private fun cargarHistorial() {
        val currentUserId = auth.currentUser?.uid ?: return
        db.collection("usuarios").document(currentUserId).get().addOnSuccessListener { userDoc ->
            currentRole = userDoc.getString("rol") ?: ""
            
            if (currentRole == "chofer") {
                tilSearch.visibility = View.GONE
                layoutFilters.visibility = View.GONE
                chipGroupRole.visibility = View.GONE
            } else {
                tilSearch.visibility = View.VISIBLE
                layoutFilters.visibility = View.VISIBLE
                chipGroupRole.visibility = View.VISIBLE
            }

            val collectionRef = db.collection("viajes")
            val query = when (currentRole) {
                "gerente" -> collectionRef
                "despachador" -> collectionRef.whereEqualTo("despachadorId", currentUserId)
                else -> collectionRef.whereEqualTo("choferId", currentUserId)
            }
            query.addSnapshotListener { value, error ->
                if (error != null) return@addSnapshotListener
                todosLosViajes = value?.toObjects(Viaje::class.java) ?: emptyList()
                aplicarFiltrosYOrden()
            }
        }
    }

    private fun aplicarFiltrosYOrden() {
        val query = etSearch.text.toString().lowercase(Locale.getDefault())
        val searchActive = query.isNotEmpty()
        val sortActive = currentSortMode != "recent"
        val roleActive = currentRoleFilter != "all"
        btnResetFilters.visibility = if (searchActive || sortActive || roleActive) View.VISIBLE else View.GONE

        var filtrados = todosLosViajes

        if (searchActive && currentRole != "chofer") {
            filtrados = filtrados.filter { viaje ->
                val displayEstado = getDisplayEstado(viaje.estado).lowercase()
                when (currentRoleFilter) {
                    "chofer" -> viaje.nombreChofer.lowercase().contains(query) || displayEstado.contains(query)
                    "despachador" -> viaje.nombreDespachador.lowercase().contains(query) || displayEstado.contains(query)
                    else -> {
                        viaje.nombreChofer.lowercase().contains(query) ||
                        viaje.nombreDespachador.lowercase().contains(query) ||
                        viaje.placaUnidad.lowercase().contains(query) ||
                        viaje.numeroEconomico.lowercase().contains(query) ||
                        viaje.titulo.lowercase().contains(query) ||
                        displayEstado.contains(query)
                    }
                }
            }
        }

        val sortedList = when (currentSortMode) {
            "distance" -> filtrados.sortedByDescending { it.distancia.replace(" km", "").toDoubleOrNull() ?: 0.0 }
            "fuel" -> filtrados.sortedByDescending { it.combustible.toDoubleOrNull() ?: 0.0 }
            else -> filtrados.sortedByDescending { it.fechaInicio }
        }

        adapter.updateData(sortedList)
        actualizarUI(sortedList)
    }

    private fun actualizarUI(lista: List<Viaje>) {
        val conteoHoy = contarHoy(todosLosViajes)
        tvContadorHoy.text = "Viajes realizados hoy: $conteoHoy"
        tvEmpty.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun contarHoy(lista: List<Viaje>): Int {
        val hoy = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        return lista.count { 
            it.fechaInicio?.toDate()?.let { f -> !f.before(hoy) } == true && 
            getDisplayEstado(it.estado) == "Completado" 
        }
    }
}
