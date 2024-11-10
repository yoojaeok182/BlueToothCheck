package com.bluetooth.chart.activity

import android.bluetooth.BluetoothAdapter
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bluetooth.chart.R
import com.bluetooth.chart.databinding.ActivityBluetoothInformationBinding
import com.bluetooth.chart.databinding.ActivityMainBinding
import com.bluetooth.chart.module.data.BlueToothInfoModel

class BlueToothInformationActivity : AppCompatActivity() {
    final lateinit var binding: ActivityBluetoothInformationBinding
    private var blueToothInfoModel : BlueToothInfoModel? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        binding = ActivityBluetoothInformationBinding.inflate(layoutInflater)
        setContentView(binding.root)
       /* ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.informationMain)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }*/
        val intent = intent
        if (intent.hasExtra("blueToothData")) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                blueToothInfoModel = intent.getSerializableExtra("blueToothData", BlueToothInfoModel::class.java)
            }else{
                blueToothInfoModel =
                    intent.extras!!.getSerializable("blueToothData") as BlueToothInfoModel?
            }

        }


        init()
    }

    fun init(){
        binding.btnBack.setOnClickListener {
            finish()
        }

        Log.d("TAG", "init: ${blueToothInfoModel?.name} ")

        binding.tvTitle.text = blueToothInfoModel?.name ?:""
        binding.tvDeviceLocalName.text = blueToothInfoModel?.name ?:""
        binding.tvStatus.text = if(blueToothInfoModel?.bondState == "Paired" ){
            "Connect"
        }else if(blueToothInfoModel?.bondState =="Pairing"){
            "Pairing"
        }else{
            "Not Paired"
        }

        binding.tvDeviceUUID.text =  blueToothInfoModel?.uuid?:"-"
        binding.tvDeviceRssi.text = blueToothInfoModel?.rssi
        binding.tvDeviceAdress.text = blueToothInfoModel?.address
        binding.tvDeviceClass.text = blueToothInfoModel?.deviceClass
        binding.tvDeviceMajorClass.text = blueToothInfoModel?.deviceMajorClass
        binding.tvDeviceConnectable.text = if(blueToothInfoModel?.bondState == "Paired" ){
            "YES"
        }else if(blueToothInfoModel?.bondState =="Pairing"){
            "NO"
        }else{
            "No"
        }
    }

}