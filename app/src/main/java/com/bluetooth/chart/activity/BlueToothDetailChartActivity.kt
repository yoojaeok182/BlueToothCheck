package com.bluetooth.chart.activity

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bluetooth.chart.R
import com.bluetooth.chart.databinding.ActivityBluetoothChartBinding
import com.bluetooth.chart.module.BlueToothParingUtil
import com.bluetooth.chart.module.data.BlueToothInfoModel

class BlueToothDetailChartActivity : AppCompatActivity() {
    final lateinit var binding: ActivityBluetoothChartBinding

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var deviceListAdapter: ArrayAdapter<String>
    private val deviceList = mutableListOf<String>()
    private var blueToothInfoModel : BlueToothInfoModel? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        enableEdgeToEdge()
        binding = ActivityBluetoothChartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val intent = intent
        if (intent.hasExtra("blueToothData")) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                blueToothInfoModel = intent.getSerializableExtra("blueToothData", BlueToothInfoModel::class.java)
            }else{
                blueToothInfoModel =
                    intent.extras!!.getSerializable("blueToothData") as BlueToothInfoModel?
            }

        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.chartMain)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        init()
    }

    private fun init(){

        if(blueToothInfoModel != null){
            val device = bluetoothAdapter.getRemoteDevice(blueToothInfoModel!!.address)
            if (ActivityCompat.checkSelfPermission(
                    this@BlueToothDetailChartActivity,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ActivityCompat.requestPermissions(this@BlueToothDetailChartActivity,
                        arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT), 11)
                }
                return
            }
            if(device.bondState != BluetoothDevice.BOND_BONDED){
                BlueToothParingUtil.pairDevice(device)
            }else{
                BlueToothParingUtil.connectToDevice(this@BlueToothDetailChartActivity,device)

            }


            binding.chart1.value = 40.0f
            binding.tvInverterPer.text = "${40}%"
        }


    }


    // 페어링 상태 변화 감지 (브로드캐스트 리시버)
    private val pairingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == action) {
                var device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                val prevBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)

                when (bondState) {
                    BluetoothDevice.BOND_BONDED -> {
                        // 페어링 완료 후 연결 시작
                        Toast.makeText(context, "Device paired", Toast.LENGTH_SHORT).show()
                        device?.let {
                            BlueToothParingUtil.connectToDevice(this@BlueToothDetailChartActivity,device)
                        }
                    }
                    BluetoothDevice.BOND_BONDING -> {
                        Toast.makeText(context, "Pairing in progress", Toast.LENGTH_SHORT).show()
                    }
                    BluetoothDevice.BOND_NONE -> {
                        Toast.makeText(context, "Pairing failed or removed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 페어링 상태 변경 리시버 등록
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        registerReceiver(pairingReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        // 리시버 해제
        unregisterReceiver(pairingReceiver)
    }
}