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
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.*

class BlueToothDetailChartActivity : AppCompatActivity() {
    final lateinit var binding: ActivityBluetoothChartBinding

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var deviceListAdapter: ArrayAdapter<String>
    private val deviceList = mutableListOf<String>()
    private var blueToothInfoModel: BlueToothInfoModel? = null

    // LineChart 관련 변수
    private lateinit var lineChart: LineChart
    private lateinit var lineDataSet: LineDataSet
    private lateinit var lineData: LineData

    // CoroutineScope를 사용해서 반복 작업을 처리
    private val scope = CoroutineScope(Dispatchers.Main)
    private var isReceivingData = false

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
            } else {
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

    private fun init() {
        // LineChart 초기화
        lineChart = binding.chart4
        setupLineChart()

        if (blueToothInfoModel != null) {
            val device = bluetoothAdapter.getRemoteDevice(blueToothInfoModel!!.address)
            if (ActivityCompat.checkSelfPermission(
                    this@BlueToothDetailChartActivity,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ActivityCompat.requestPermissions(this@BlueToothDetailChartActivity,
                        arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT), 11)
                }
                return
            }
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                BlueToothParingUtil.pairDevice(device)
            } else {
                BlueToothParingUtil.connectToDevice(this@BlueToothDetailChartActivity, device)
            }

            binding.chart1.value = 40.0f
            binding.tvInverterPer.text = "${40}%"

            // 페어링이 완료되면 데이터를 실시간으로 받아옴
            startReceivingBluetoothData()
        }
    }

    // LineChart 설정
    private fun setupLineChart() {
        val xAxis: XAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f // X축 단위

        val yAxisLeft: YAxis = lineChart.axisLeft
        yAxisLeft.granularity = 10f

        lineChart.axisRight.isEnabled = false // 오른쪽 Y축 비활성화

        lineDataSet = LineDataSet(mutableListOf(), "Bluetooth Data")
        lineDataSet.color = resources.getColor(R.color.colorPrimary, null)
        lineDataSet.lineWidth = 2f
        lineDataSet.setDrawCircles(false)
        lineDataSet.setDrawValues(false)

        lineData = LineData(lineDataSet)
        lineChart.data = lineData
    }

    // Bluetooth 데이터 실시간으로 받기 시작
    private fun startReceivingBluetoothData() {
        isReceivingData = true

        scope.launch {
            var time = 0f

            while (isReceivingData) {
                // 여기서 Bluetooth로부터 데이터를 받아와야 함
                val newDataValue = getBluetoothData()

                // 데이터를 차트에 추가
                addEntryToChart(time, newDataValue)
                time += 0.5f // 0.5초 단위로 시간 증가

                // 0.5초 대기
                delay(500)
            }
        }
    }

    // Bluetooth에서 데이터를 받는 메소드 (임시로 랜덤 데이터를 생성)
    private fun getBluetoothData(): Float {
        // 실제 구현에서는 BluetoothDevice를 통해 데이터를 받아오는 로직을 여기에 추가
        return (10..100).random().toFloat() // 임시로 10~100 사이의 값을 반환
    }

    // LineChart에 엔트리 추가
    private fun addEntryToChart(time: Float, value: Float) {
        lineDataSet.addEntry(Entry(time, value))
        lineData.notifyDataChanged()
        lineChart.notifyDataSetChanged()
        lineChart.setVisibleXRangeMaximum(10f) // 최대 10개의 데이터를 보여줌
        lineChart.moveViewToX(lineData.entryCount.toFloat())
    }

    // 페어링 상태 변화 감지 (브로드캐스트 리시버)
    private val pairingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == action) {
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)

                when (bondState) {
                    BluetoothDevice.BOND_BONDED -> {
                        Toast.makeText(context, "Device paired", Toast.LENGTH_SHORT).show()
                        device?.let {
                            BlueToothParingUtil.connectToDevice(this@BlueToothDetailChartActivity, device)
                            // 페어링 완료 후 데이터 받기 시작
                            startReceivingBluetoothData()
                        }
                    }
                    BluetoothDevice.BOND_BONDING -> {
                        Toast.makeText(context, "Pairing in progress", Toast.LENGTH_SHORT).show()
                    }
                    BluetoothDevice.BOND_NONE -> {
                        Toast.makeText(context, "Pairing failed or removed", Toast.LENGTH_SHORT).show()
                        stopReceivingBluetoothData() // 페어링 실패 시 데이터 받기 중단
                    }
                }
            }
        }
    }

    // Bluetooth 데이터 받기 중지
    private fun stopReceivingBluetoothData() {
        isReceivingData = false
        scope.cancel() // CoroutineScope 중단
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        registerReceiver(pairingReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(pairingReceiver)
        stopReceivingBluetoothData() // Activity가 종료될 때 데이터 받기 중단
    }
}
