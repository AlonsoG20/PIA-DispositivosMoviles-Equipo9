package org.PIA.geofence.ui.gestion

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import org.PIA.geofence.R
import org.PIA.geofence.data.Unidad
import java.util.Locale

class UnidadAdapter(private var unidades: List<Unidad>) : RecyclerView.Adapter<UnidadAdapter.UnidadViewHolder>() {

    class UnidadViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPlaca: TextView = view.findViewById(R.id.tvUnidadPlaca)
        val tvNumeroEconomico: TextView = view.findViewById(R.id.tvNumeroEconomico)
        val tvConductor: TextView = view.findViewById(R.id.tvConductor)
        val tvUbicacion: TextView = view.findViewById(R.id.tvUbicacion)
        val tvActualizacion: TextView = view.findViewById(R.id.tvActualizacion)
        val chipEstado: Chip = view.findViewById(R.id.chipEstado)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UnidadViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_unidad, parent, false)
        return UnidadViewHolder(view)
    }

    override fun onBindViewHolder(holder: UnidadViewHolder, position: Int) {
        val unidad = unidades[position]
        holder.tvPlaca.text = unidad.placa
        holder.tvNumeroEconomico.text = if (unidad.numeroEconomico.isNotEmpty()) "(#${unidad.numeroEconomico})" else ""
        holder.tvConductor.text = "Conductor: ${unidad.nombreChoferAsignado.ifEmpty { "Sin asignar" }}"

        val geo = unidad.ultimaUbicacion
        holder.tvUbicacion.text = if (geo != null) {
            String.format(Locale.getDefault(), "%.4f, %.4f", geo.latitude, geo.longitude)
        } else {
            "Ubicación desconocida"
        }

        val timestamp = unidad.ultimaActualizacion
        holder.tvActualizacion.text = if (timestamp != null) {
            DateUtils.getRelativeTimeSpanString(timestamp.toDate().time)
        } else {
            "Sin datos"
        }

        holder.chipEstado.text = unidad.estado
        
        when (unidad.estado) {
            "Disponible" -> holder.chipEstado.setChipBackgroundColorResource(android.R.color.holo_green_light)
            "En ruta" -> holder.chipEstado.setChipBackgroundColorResource(android.R.color.holo_blue_light)
            "Mantenimiento" -> holder.chipEstado.setChipBackgroundColorResource(android.R.color.holo_orange_light)
            else -> holder.chipEstado.setChipBackgroundColorResource(android.R.color.darker_gray)
        }
    }

    override fun getItemCount() = unidades.size

    fun updateUnidades(newUnidades: List<Unidad>) {
        unidades = newUnidades
        notifyDataSetChanged()
    }
}