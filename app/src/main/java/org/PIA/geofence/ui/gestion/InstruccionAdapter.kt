package org.PIA.geofence.ui.gestion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import org.PIA.geofence.R
import org.PIA.geofence.data.Instruccion
import java.text.SimpleDateFormat
import java.util.*

class InstruccionAdapter(
    private var list: List<Instruccion>,
    private val onActionClick: ((Instruccion, String) -> Unit)? = null,
    private val onDeleteClick: ((Instruccion) -> Unit)? = null
) : RecyclerView.Adapter<InstruccionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRemitente: TextView = view.findViewById(R.id.tvInstruccionRemitente)
        val tvFecha: TextView = view.findViewById(R.id.tvInstruccionFecha)
        val tvMensaje: TextView = view.findViewById(R.id.tvInstruccionMensaje)
        val tvEstado: TextView = view.findViewById(R.id.tvInstruccionEstado)
        val tvDestinatario: TextView = view.findViewById(R.id.tvInstruccionDestinatario)
        val tvModificado: TextView = view.findViewById(R.id.tvInstruccionModificado)
        val btnGuardar: Button = view.findViewById(R.id.btnGuardarInstruccion)
        val btnDescartar: Button = view.findViewById(R.id.btnDescartarInstruccion)
        val btnCompletar: Button = view.findViewById(R.id.btnCompletarInstruccion)
        val btnDelete: ImageButton? = view.findViewById(R.id.btnDeleteInstruccion)
        val layoutAcciones: View = view.findViewById(R.id.layoutAcciones)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_instruccion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        val context = holder.itemView.context
        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

        holder.tvRemitente.text = "De: ${item.remitenteNombre}"
        holder.tvFecha.text = "Creado: ${item.fechaCreacion?.toDate()?.let { sdf.format(it) } ?: "Sincronizando..."}"
        holder.tvMensaje.text = item.mensaje

        // Mostrar destinatario si estamos en vista de Gerente
        if (onDeleteClick != null) {
            holder.tvDestinatario.visibility = View.VISIBLE
            holder.tvDestinatario.text = "Para: ${item.destinatarioNombre}"
            holder.btnDelete?.visibility = View.VISIBLE
            holder.btnDelete?.setOnClickListener { onDeleteClick.invoke(item) }
        } else {
            holder.tvDestinatario.visibility = View.GONE
            holder.btnDelete?.visibility = View.GONE
        }

        // Mostrar fecha de primera modificación si existe
        if (item.primeraModificacion != null) {
            holder.tvModificado.visibility = View.VISIBLE
            holder.tvModificado.text = "Modificado: ${sdf.format(item.primeraModificacion.toDate())}"
        } else {
            holder.tvModificado.visibility = View.GONE
        }

        val isBloqueado = item.bloqueado == 1
        
        // Configurar botones de acción para el despachador
        if (onActionClick != null) {
            holder.layoutAcciones.visibility = View.VISIBLE
            holder.btnGuardar.visibility = if (item.estado != "guardada") View.VISIBLE else View.GONE
            holder.btnDescartar.visibility = if (item.estado != "descartada") View.VISIBLE else View.GONE
            holder.btnCompletar.visibility = if (item.estado != "completada") View.VISIBLE else View.GONE

            holder.btnGuardar.setOnClickListener { onActionClick.invoke(item, "guardada") }
            holder.btnDescartar.setOnClickListener { onActionClick.invoke(item, "descartada") }
            holder.btnCompletar.setOnClickListener { onActionClick.invoke(item, "completada") }

            if (isBloqueado) {
                val colorApagado = ContextCompat.getColor(context, R.color.gray_hint)
                holder.btnGuardar.isEnabled = false
                holder.btnDescartar.isEnabled = false
                holder.btnCompletar.isEnabled = false
                holder.btnGuardar.setTextColor(colorApagado)
                holder.btnDescartar.setTextColor(colorApagado)
                holder.btnCompletar.setTextColor(colorApagado)
            } else {
                val colorActivo = ContextCompat.getColor(context, R.color.teal_primary)
                holder.btnGuardar.isEnabled = true
                holder.btnDescartar.isEnabled = true
                holder.btnCompletar.isEnabled = true
                holder.btnGuardar.setTextColor(colorActivo)
                holder.btnDescartar.setTextColor(colorActivo)
                holder.btnCompletar.setTextColor(colorActivo)
            }
        } else {
            holder.layoutAcciones.visibility = View.GONE
        }

        holder.tvEstado.text = item.estado.uppercase()
        val badgeColor = when (item.estado) {
            "pendiente" -> ContextCompat.getColor(context, R.color.gray_hint)
            "guardada" -> ContextCompat.getColor(context, android.R.color.holo_blue_dark)
            "completada" -> ContextCompat.getColor(context, android.R.color.holo_green_dark)
            "descartada" -> ContextCompat.getColor(context, android.R.color.holo_red_dark)
            else -> ContextCompat.getColor(context, R.color.gray_hint)
        }
        holder.tvEstado.background.setTint(badgeColor)
    }

    override fun getItemCount(): Int = list.size

    fun updateData(newList: List<Instruccion>) {
        list = newList
        notifyDataSetChanged()
    }
}
