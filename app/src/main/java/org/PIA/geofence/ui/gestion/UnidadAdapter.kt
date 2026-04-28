package org.PIA.geofence.ui.gestion

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
        
        // Información de Gasolina
        val gasActual = unidad.gasolinaActual.toInt()
        val gasMax = unidad.capacidadMaxima.toInt()
        holder.tvGasInfo.text = "Combustible: $gasActual/$gasMax L"
        holder.pbGas.max = gasMax
        holder.pbGas.progress = gasActual

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