package org.PIA.geofence.ui.gestion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.PIA.geofence.R
import org.PIA.geofence.data.PuntoInteres
import java.util.Locale

class PoiAdapter(
    private var pois: List<PuntoInteres>,
    private val onDeleteClick: (PuntoInteres) -> Unit
) : RecyclerView.Adapter<PoiAdapter.PoiViewHolder>() {

    class PoiViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvPoiName)
        val tvCoords: TextView = view.findViewById(R.id.tvPoiCoords)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeletePoi)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PoiViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_poi, parent, false)
        return PoiViewHolder(view)
    }

    override fun onBindViewHolder(holder: PoiViewHolder, position: Int) {
        val poi = pois[position]
        holder.tvName.text = poi.nombre
        holder.tvCoords.text = String.format(Locale.US, "%.5f, %.5f", poi.lugar?.latitude ?: 0.0, poi.lugar?.longitude ?: 0.0)
        holder.btnDelete.setOnClickListener { onDeleteClick(poi) }
    }

    override fun getItemCount() = pois.size

    fun updatePois(newPois: List<PuntoInteres>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = pois.size
            override fun getNewListSize(): Int = newPois.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return pois[oldItemPosition].id == newPois[newItemPosition].id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return pois[oldItemPosition] == newPois[newItemPosition]
            }
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        pois = newPois
        diffResult.dispatchUpdatesTo(this)
    }
}
