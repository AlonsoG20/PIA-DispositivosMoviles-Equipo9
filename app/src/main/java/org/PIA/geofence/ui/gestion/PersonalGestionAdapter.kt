package org.PIA.geofence.ui.gestion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.PIA.geofence.R
import org.PIA.geofence.data.User

class PersonalGestionAdapter(
    private var users: List<User>,
    private val onStatusToggled: (User) -> Unit,
    private val onRoleChanged: (User, String) -> Unit
) : RecyclerView.Adapter<PersonalGestionAdapter.PersonalViewHolder>() {

    class PersonalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvUserName)
        val tvRole: TextView = view.findViewById(R.id.tvUserRole)
        val tvStatus: TextView = view.findViewById(R.id.tvUserStatus)
        val btnToggle: Button = view.findViewById(R.id.btnToggleStatus)
        val btnChangeRole: Button = view.findViewById(R.id.btnChangeRole)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_personal_gestion, parent, false)
        return PersonalViewHolder(view)
    }

    override fun onBindViewHolder(holder: PersonalViewHolder, position: Int) {
        val user = users[position]
        holder.tvName.text = user.nombreCompleto
        val rolActual = user.rol ?: ""
        holder.tvRole.text = "Rol: ${rolActual.replaceFirstChar { it.uppercase() }}"
        
        val isInactive = user.estado == "2"
        
        if (isInactive) {
            holder.tvStatus.text = "Estado: Fuera de Turno"
            holder.tvStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark))
            holder.btnToggle.text = "Entrar a Turno"
        } else {
            val statusText = if (user.estado == "1") "Ocupado" else "Disponible"
            holder.tvStatus.text = "Estado: $statusText"
            holder.tvStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_dark))
            holder.btnToggle.text = "Finalizar Turno"
        }

        // Lógica de cambio de rol
        val nuevoRol = if (rolActual == "chofer") "despachador" else "chofer"
        holder.btnChangeRole.text = "Cambiar a $nuevoRol"

        holder.btnToggle.setOnClickListener { onStatusToggled(user) }
        holder.btnChangeRole.setOnClickListener { onRoleChanged(user, nuevoRol) }
    }

    override fun getItemCount() = users.size

    fun updateUsers(newUsers: List<User>) {
        users = newUsers
        notifyDataSetChanged()
    }
}