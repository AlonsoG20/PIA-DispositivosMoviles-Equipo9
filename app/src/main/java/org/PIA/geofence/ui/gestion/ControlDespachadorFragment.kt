package org.PIA.geofence.ui.gestion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import org.PIA.geofence.R
import org.PIA.geofence.data.Unidad
import org.PIA.geofence.data.User

class ControlDespachadorFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var adapterUnidades: UnidadAdapter
    private lateinit var adapterChoferes: ChoferAdapter

    private lateinit var tvEmptyFlota: TextView
    private lateinit var tvEmptyChoferes: TextView

    private var unidadesListener: ListenerRegistration? = null
    private var choferesListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_control_despachador, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = FirebaseFirestore.getInstance()

        val rvUnidades = view.findViewById<RecyclerView>(R.id.rvFlotaDespachador)
        val rvChoferes = view.findViewById<RecyclerView>(R.id.rvChoferesDespachador)
        val btnAdd = view.findViewById<Button>(R.id.btnAddUnidadDespachador)

        tvEmptyFlota = view.findViewById(R.id.tvEmptyFlota)
        tvEmptyChoferes = view.findViewById(R.id.tvEmptyChoferes)

        adapterUnidades = UnidadAdapter(emptyList())
        rvUnidades.layoutManager = LinearLayoutManager(context)
        rvUnidades.adapter = adapterUnidades

        adapterChoferes = ChoferAdapter(emptyList())
        rvChoferes.layoutManager = LinearLayoutManager(context)
        rvChoferes.adapter = adapterChoferes

        btnAdd.setOnClickListener {
            showAddUnidadDialog()
        }

        loadData()
    }

    private fun showAddUnidadDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_unidad, null)
        val etPlaca = dialogView.findViewById<TextInputEditText>(R.id.etPlaca)
        val etEconomico = dialogView.findViewById<TextInputEditText>(R.id.etEconomico)
        val etModelo = dialogView.findViewById<TextInputEditText>(R.id.etModelo)
        val btnCancelar = dialogView.findViewById<Button>(R.id.btnCancelar)
        val btnGuardar = dialogView.findViewById<Button>(R.id.btnGuardar)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        btnCancelar.setOnClickListener { dialog.dismiss() }

        btnGuardar.setOnClickListener {
            val placa = etPlaca.text.toString().trim().uppercase()
            val economico = etEconomico.text.toString().trim()
            val modelo = etModelo.text.toString().trim()

            if (placa.isEmpty() || economico.isEmpty() || modelo.isEmpty()) {
                Toast.makeText(context, "Por favor llena todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val nuevaUnidad = hashMapOf(
                "placa" to placa,
                "numeroEconomico" to economico,
                "modelo" to modelo,
                "estado" to "Disponible",
                "conductorAsignado" to "",
                "ultimaActualizacion" to Timestamp.now()
            )

            db.collection("unidades").add(nuevaUnidad)
                .addOnSuccessListener {
                    Toast.makeText(context, "Unidad registrada con éxito", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Error al registrar unidad", Toast.LENGTH_SHORT).show()
                }
        }

        dialog.show()
    }

    private fun loadData() {
        unidadesListener = db.collection("unidades")
            .orderBy("placa", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.toObjects(Unidad::class.java) ?: emptyList()
                adapterUnidades.updateUnidades(list)
                tvEmptyFlota.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }

        choferesListener = db.collection("usuarios")
            .whereEqualTo("rol", "chofer")
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.toObjects(User::class.java) ?: emptyList()
                adapterChoferes.updateChoferes(list)
                tvEmptyChoferes.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        unidadesListener?.remove()
        choferesListener?.remove()
    }
}