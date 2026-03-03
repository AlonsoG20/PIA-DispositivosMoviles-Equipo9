package org.PIA.geofence.ui.gestion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.PIA.geofence.R
import org.PIA.geofence.data.User

class UsersSinRolAdapter(
    private var users: List<User>,
    private val onRoleAssigned: (User, String) -> Unit
) : RecyclerView.Adapter<UsersSinRolAdapter.UserViewHolder>() {

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvUserName)
        val tvEmail: TextView = view.findViewById(R.id.tvUserEmail)
        val btnChofer: Button = view.findViewById(R.id.btnAsignarChofer)
        val btnDespachador: Button = view.findViewById(R.id.btnAsignarDespachador)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_sin_rol, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        holder.tvName.text = user.nombreCompleto
        holder.tvEmail.text = user.emailSeguro

        holder.btnChofer.setOnClickListener { onRoleAssigned(user, "chofer") }
        holder.btnDespachador.setOnClickListener { onRoleAssigned(user, "despachador") }
    }

    override fun getItemCount() = users.size

    fun updateUsers(newUsers: List<User>) {
        users = newUsers
        notifyDataSetChanged()
    }
}