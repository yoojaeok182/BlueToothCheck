package com.bluetooth.chart.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bluetooth.chart.R
import com.bluetooth.chart.module.data.BlueToothInfoModel

class DeviceAdapter(private val deviceList: List<BlueToothInfoModel>) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {
    var adapterListener: AdapterListener? = null

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceNameTextView: TextView = itemView.findViewById(R.id.tvBlueThoothName)
        val deviceConnectBtn: Button = itemView.findViewById(R.id.btnConnect)
        val deviceIco: ImageView = itemView.findViewById(R.id.ivBlueThooth)
        val rootLayout : ConstraintLayout  = itemView.findViewById(R.id.rootLayout)
    }
    interface AdapterListener {
        fun onItemClick( item: BlueToothInfoModel, position : Int )
        fun onItemParing( item: BlueToothInfoModel, position : Int )

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.adapter_bluetooth_list, parent, false)
        return DeviceViewHolder(view)
    }

    override fun getItemCount(): Int {
        return deviceList.size
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {

        if(deviceList[position].isOpen){
            holder.rootLayout.setBackgroundColor(Color.parseColor("#990000"))
        }else{
            holder.rootLayout.setBackgroundColor(Color.parseColor("#00000000"))
        }
        holder.deviceNameTextView.text = deviceList[position].name
        holder.rootLayout.setOnClickListener {
            adapterListener?.onItemClick(deviceList[position],position)
        }
        holder.deviceConnectBtn.setOnClickListener {
            adapterListener?.onItemParing(deviceList[position],position)
        }
    }

}
