package org.PIA.geofence.ui.gestion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.PIA.geofence.R
import org.PIA.geofence.data.Instruccion
import java.text.SimpleDateFormat
import java.util.*

class InstruccionAdapter(
    private var list: List<Instruccion>,
    private val onCompletarClick: ((Instruccion) -> Unit)? = null
) : RecyclerView.Adapter<InstruccionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRemitente: TextView = view.findViewById(R.id.tvInstruccionRemitente)
        val tvFecha: TextView = view.findViewById(R.id.tvInstruccionFecha)
        val tvMensaje: TextView = view.findViewById(R.id.tvInstruccionMensaje)
        val tvEstado: TextView = view.findViewById(R.id.tvInstruccionEstado)
        val btnCompletar: Button = view.findViewById(R.id.btnCompletarInstruccion)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_instruccion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

        holder.tvRemitente.text = "De: ${item.remitenteNombre}"
        holder.tvFecha.text = sdf.format(item.fechaCreacion.toDate())
        holder.tvMensaje.text = item.mensaje
        holder.tvEstado.text = item.estado.uppercase()

        if (item.estado == "pendiente") {
            holder.tvEstado.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_orange_dark))
            if (onCompletarClick != null) {
                holder.btnCompletar.visibility = View.VISIBLE
                holder.btnCompletar.setOnClickListener { onCompletarClick.invoke(item) }
            } else {
                holder.btnCompletar.visibility = View.GONE
            }
        } else {
            holder.tvEstado.text = "COMPLETADA"
            holder.tvEstado.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_dark))
            holder.btnCompletar.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = list.size

    fun updateData(newList: List<Instruccion>) {
        list = newList
        notifyDataSetChanged()
    }
}
