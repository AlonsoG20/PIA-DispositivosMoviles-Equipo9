package org.PIA.geofence.ui.gestion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.PIA.geofence.R
import org.PIA.geofence.data.User

class ChoferAdapter(private var choferes: List<User>) : RecyclerView.Adapter<ChoferAdapter.ChoferViewHolder>() {

    class ChoferViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val vIndicator: View = view.findViewById(R.id.vEstadoIndicator)
        val tvNombre: TextView = view.findViewById(R.id.tvChoferNombre)
        val tvEstado: TextView = view.findViewById(R.id.tvChoferEstado)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChoferViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chofer_simple, parent, false)
        return ChoferViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChoferViewHolder, position: Int) {
        val chofer = choferes[position]
        holder.tvNombre.text = chofer.nombreCompleto
        
        // "0" = Disponible, "1" = Ocupado, "2" = Inactivo
        val estadoVal = chofer.estado ?: "0" 
        
        when (estadoVal) {
            "1" -> {
                holder.tvEstado.text = "Ocupado"
                holder.tvEstado.setTextColor(holder.itemView.context.getColor(android.R.color.holo_red_dark))
                holder.vIndicator.setBackgroundResource(R.drawable.circle_background)
                holder.vIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    holder.itemView.context.getColor(android.R.color.holo_red_dark)
                )
            }
            "2" -> {
                holder.tvEstado.text = "Inactivo"
                holder.tvEstado.setTextColor(holder.itemView.context.getColor(android.R.color.darker_gray))
                holder.vIndicator.setBackgroundResource(R.drawable.circle_background)
                holder.vIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    holder.itemView.context.getColor(android.R.color.darker_gray)
                )
            }
            else -> {
                holder.tvEstado.text = "Disponible"
                holder.tvEstado.setTextColor(holder.itemView.context.getColor(android.R.color.holo_green_dark))
                holder.vIndicator.setBackgroundResource(R.drawable.circle_background)
                holder.vIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    holder.itemView.context.getColor(android.R.color.holo_green_dark)
                )
            }
        }
    }

    override fun getItemCount() = choferes.size

    fun updateChoferes(newChoferes: List<User>) {
        choferes = newChoferes
        notifyDataSetChanged()
    }
}