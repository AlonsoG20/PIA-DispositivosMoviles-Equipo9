package org.PIA.geofence.ui.historial

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.PIA.geofence.R
import org.PIA.geofence.data.Viaje
import java.text.SimpleDateFormat
import java.util.*

class ViajeAdapter(private var viajes: List<Viaje>) : RecyclerView.Adapter<ViajeAdapter.ViajeViewHolder>() {

    class ViajeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView = view.findViewById(R.id.tvTitulo)
        val tvDistancia: TextView = view.findViewById(R.id.tvDistancia)
        val tvFecha: TextView = view.findViewById(R.id.tvFecha)
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
        holder.tvDistancia.text = viaje.distancia
        holder.tvCosto.text = "costo: $${viaje.costo}"
        holder.tvCombustible.text = "combustible: ${viaje.combustible}L"

        val sdf = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
        holder.tvFecha.text = viaje.fecha?.toDate()?.let { sdf.format(it) } ?: "--/--/--"
    }

    override fun getItemCount() = viajes.size

    fun updateData(newViajes: List<Viaje>) {
        viajes = newViajes
        notifyDataSetChanged()
    }
}