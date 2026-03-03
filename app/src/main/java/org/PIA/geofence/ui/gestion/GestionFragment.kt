package org.PIA.geofence.ui.gestion

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.PIA.geofence.R
import org.PIA.geofence.data.User

class GestionFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: UsersSinRolAdapter
    private var usersListener: ListenerRegistration? = null
    private var isExpanded = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_gestion, container, false)
        db = FirebaseFirestore.getInstance()
        
        setupAccordion(view)
        setupRecyclerView(view)
        loadUsersSinRol()
        
        return view
    }

    private fun setupAccordion(view: View) {
        val header = view.findViewById<LinearLayout>(R.id.layoutHeaderAsignar)
        val rv = view.findViewById<RecyclerView>(R.id.rvUsuariosSinRol)
        val icon = view.findViewById<ImageView>(R.id.ivExpandIcon)

        header.setOnClickListener {
            isExpanded = !isExpanded
            rv.visibility = if (isExpanded) View.VISIBLE else View.GONE
            icon.setImageResource(
                if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
            )
        }
    }

    private fun setupRecyclerView(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.rvUsuariosSinRol)
        adapter = UsersSinRolAdapter(emptyList()) { user, newRole ->
            assignRole(user, newRole)
        }
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = adapter
    }

    private fun loadUsersSinRol() {
        usersListener?.remove()
        
        usersListener = db.collection("usuarios")
            .whereEqualTo("rol", "sinRol")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("GestionFragment", "Error de permisos o red: ${e.message}")
                    if (isAdded) {
                        Toast.makeText(context, "Error: No tienes permisos de Gerente", Toast.LENGTH_SHORT).show()
                    }
                    return@addSnapshotListener
                }

                val userList = snapshot?.toObjects(User::class.java) ?: emptyList()
                Log.d("GestionFragment", "Usuarios encontrados: ${userList.size}")
                adapter.updateUsers(userList)
            }
    }

    private fun assignRole(user: User, newRole: String) {
        if (user.id.isEmpty()) return

        db.collection("usuarios").document(user.id)
            .update("rol", newRole)
            .addOnSuccessListener {
                if (isAdded) Toast.makeText(context, "Rol actualizado", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("GestionFragment", "Error al actualizar: ${e.message}")
                if (isAdded) Toast.makeText(context, "Error al actualizar rol", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        usersListener?.remove()
    }
}