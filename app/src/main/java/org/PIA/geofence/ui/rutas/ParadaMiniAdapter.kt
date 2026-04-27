package org.PIA.geofence.ui.rutas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.model.Marker
import org.PIA.geofence.R

class ParadaMiniAdapter(
    private val paradas: MutableList<Marker>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<ParadaMiniAdapter.ParadaViewHolder>() {

    class ParadaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvParadaNombre)
        val btnRemove: ImageView = view.findViewById(R.id.btnRemoveParada)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParadaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_parada_mini, parent, false)
        return ParadaViewHolder(view)
    }

    override fun onBindViewHolder(holder: ParadaViewHolder, position: Int) {
        val marker = paradas[position]
        holder.tvNombre.text = marker.title ?: "Parada ${position + 1}"
        holder.btnRemove.setOnClickListener { onRemove(holder.adapterPosition) }
    }

    override fun getItemCount() = paradas.size

    // Se eliminó notifyDataSetChanged() por eventos más específicos
    fun itemAdded() {
        notifyItemInserted(paradas.size - 1)
    }

    fun itemRemoved(position: Int) {
        notifyItemRemoved(position)
        // Notificar cambios en el rango para actualizar los números de las paradas restantes
        notifyItemRangeChanged(position, paradas.size - position)
    }
}