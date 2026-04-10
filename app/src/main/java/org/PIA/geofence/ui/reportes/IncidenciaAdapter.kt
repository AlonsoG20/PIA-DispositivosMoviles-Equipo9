package org.PIA.geofence.ui.reportes

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.PIA.geofence.R
import org.PIA.geofence.data.Incidencia
import java.text.SimpleDateFormat
import java.util.*

class IncidenciaAdapter(private var incidencias: List<Incidencia>) : RecyclerView.Adapter<IncidenciaAdapter.IncidenciaViewHolder>() {

    class IncidenciaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTipo: TextView = view.findViewById(R.id.tvTipoIncidencia)
        val tvFecha: TextView = view.findViewById(R.id.tvFechaIncidencia)
        val tvChofer: TextView = view.findViewById(R.id.tvChoferIncidencia)
        val tvDetalle: TextView = view.findViewById(R.id.tvDetalleIncidencia)
        val viewPriority: View = view.findViewById(R.id.viewPriority)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IncidenciaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_incidencia, parent, false)
        return IncidenciaViewHolder(view)
    }

    override fun onBindViewHolder(holder: IncidenciaViewHolder, position: Int) {
        val incidencia = incidencias[position]
        holder.tvTipo.text = incidencia.tipo
        holder.tvChofer.text = "Chofer: ${incidencia.chofer}"
        holder.tvDetalle.text = incidencia.detalle

        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        holder.tvFecha.text = incidencia.fecha?.toDate()?.let { sdf.format(it) } ?: "--:--"

        // Color de prioridad
        val color = when (incidencia.nivelPrioridad) {
            "Alta" -> "#E53935"   // Rojo
            "Media" -> "#FB8C00"  // Naranja
            "Baja" -> "#FDD835"   // Amarillo
            else -> "#757575"
        }
        holder.viewPriority.setBackgroundColor(Color.parseColor(color))
    }

    override fun getItemCount() = incidencias.size

    fun updateData(newIncidencias: List<Incidencia>) {
        incidencias = newIncidencias
        notifyDataSetChanged()
    }
}