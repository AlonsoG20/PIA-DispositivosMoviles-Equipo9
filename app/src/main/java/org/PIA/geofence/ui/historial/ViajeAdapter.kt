package org.PIA.geofence.ui.historial

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.PIA.geofence.R
import org.PIA.geofence.data.Viaje
import java.text.SimpleDateFormat
import java.util.*

class ViajeAdapter(
    private var viajes: List<Viaje>,
    private val onItemClick: (Viaje) -> Unit = {}
) : RecyclerView.Adapter<ViajeAdapter.ViajeViewHolder>() {

    class ViajeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView = view.findViewById(R.id.tvTitulo)
        val tvEstado: TextView = view.findViewById(R.id.tvEstado)
        val tvDistancia: TextView = view.findViewById(R.id.tvDistancia)
        val tvFecha: TextView = view.findViewById(R.id.tvFecha)
        val tvFechaFin: TextView = view.findViewById(R.id.tvFechaFin)
        val tvDetalleChofer: TextView = view.findViewById(R.id.tvDetalleChofer)
        val tvCosto: TextView = view.findViewById(R.id.tvCosto)
        val tvCombustible: TextView = view.findViewById(R.id.tvCombustible)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViajeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_viaje, parent, false)
        return ViajeViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViajeViewHolder, position: Int) {
        val viaje = viajes[position]
        holder.tvTitulo.text = viaje.titulo
        holder.tvDistancia.text = if (viaje.distancia.contains("km")) viaje.distancia else "${viaje.distancia} km"
        holder.tvCosto.text = "Costo: $${viaje.costo}"
        holder.tvCombustible.text = "Combustible: ${viaje.combustible}L"
        
        val unidadText = if (viaje.numeroEconomico.isNotEmpty()) "U-${viaje.numeroEconomico}" else viaje.placaUnidad.ifEmpty { "---" }
        holder.tvDetalleChofer.text = "Unidad: $unidadText | Chofer: ${viaje.nombreChofer.ifEmpty { "---" }}"

        // Lógica de estado unificado: Completado o Abandonado
        val displayEstado = if (viaje.estado.equals("completada", true) || viaje.estado.equals("finalizada", true)) {
            "Completado"
        } else {
            "Abandonado"
        }
        
        holder.tvEstado.text = displayEstado
        if (displayEstado == "Completado") {
            holder.tvEstado.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E8F5E9"))
            holder.tvEstado.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            holder.tvEstado.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFEBEE"))
            holder.tvEstado.setTextColor(Color.parseColor("#C62828"))
        }

        val sdf = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
        
        holder.tvFecha.text = "Inicio: " + (viaje.fechaInicio?.toDate()?.let { sdf.format(it) } ?: "--/--/--")
        holder.tvFechaFin.text = "Fin: " + (viaje.fechaFin?.toDate()?.let { sdf.format(it) } ?: "---")

        holder.itemView.setOnClickListener { onItemClick(viaje) }
    }

    override fun getItemCount() = viajes.size

    fun updateData(newViajes: List<Viaje>) {
        viajes = newViajes
        notifyDataSetChanged()
    }
}