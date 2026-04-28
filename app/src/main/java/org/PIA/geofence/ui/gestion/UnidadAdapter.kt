package org.PIA.geofence.ui.gestion

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import org.PIA.geofence.R
import org.PIA.geofence.data.Unidad

class UnidadAdapter(
    private var unidades: List<Unidad>,
    private val onRefuelClick: (Unidad) -> Unit
) : RecyclerView.Adapter<UnidadAdapter.UnidadViewHolder>() {

    class UnidadViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPlaca: TextView = view.findViewById(R.id.tvUnidadPlaca)
        val tvEco: TextView = view.findViewById(R.id.tvNumeroEconomico)
        val chipEstado: Chip = view.findViewById(R.id.chipEstado)
        val tvGasInfo: TextView = view.findViewById(R.id.tvGasolinaInfo)
        val pbGas: ProgressBar = view.findViewById(R.id.pbGasolina)
        val btnRefuel: Button = view.findViewById(R.id.btnRefuel)
        val tvConductor: TextView = view.findViewById(R.id.tvConductor)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UnidadViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_unidad, parent, false)
        return UnidadViewHolder(view)
    }

    override fun onBindViewHolder(holder: UnidadViewHolder, position: Int) {
        val unidad = unidades[position]
        holder.tvPlaca.text = "Placas: ${unidad.placa}"
        holder.tvEco.text = "U-${unidad.numeroEconomico}"
        holder.chipEstado.text = unidad.estado
        
        // Limpiar el apartado de conductor por ahora
        holder.tvConductor.text = "Conductor: (Sin asignar)"

        // Información de Gasolina
        val gasActual = unidad.gasolinaActual
        val gasMax = unidad.capacidadMaxima
        holder.tvGasInfo.text = "Combustible: ${gasActual.toInt()}/${gasMax.toInt()} L"
        holder.pbGas.max = gasMax.toInt()
        holder.pbGas.progress = gasActual.toInt()

        // Cambio de color dinámico de la barra
        val porcentaje = if (gasMax > 0) (gasActual / gasMax) else 0.0
        val color = when {
            porcentaje < 0.25 -> Color.RED        // Reserva (< 25%)
            porcentaje < 0.60 -> Color.YELLOW     // Medio (< 60%)
            else -> Color.parseColor("#4CAF50")   // Lleno (Verde)
        }
        holder.pbGas.progressTintList = ColorStateList.valueOf(color)

        // Acción de recarga
        holder.btnRefuel.setOnClickListener { onRefuelClick(unidad) }
        
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
