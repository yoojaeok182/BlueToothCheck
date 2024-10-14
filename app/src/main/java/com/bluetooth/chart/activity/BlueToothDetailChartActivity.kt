package com.bluetooth.chart.activity

import android.bluetooth.BluetoothAdapter
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bluetooth.chart.R
import com.bluetooth.chart.databinding.ActivityBluetoothChartBinding
import com.bluetooth.chart.databinding.ActivityMainBinding
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry

class BlueToothDetailChartActivity : AppCompatActivity() {
    final lateinit var binding: ActivityBluetoothChartBinding

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var deviceListAdapter: ArrayAdapter<String>
    private val deviceList = mutableListOf<String>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        enableEdgeToEdge()
        binding = ActivityBluetoothChartBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.chartMain)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        init()
    }

    private fun init(){

        binding.chart1.value = 40.0f
        binding.tvInverterPer.text = "${40}%"
    }


}