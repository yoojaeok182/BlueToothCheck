package com.bluetooth.chart.activity

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bluetooth.chart.R
import com.bluetooth.chart.databinding.ActivityBluetoothChartBinding
import com.bluetooth.chart.module.data.BlueToothInfoModel
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class BlueToothDetailChartActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBluetoothChartBinding
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothSocket: BluetoothSocket
    private lateinit var inputStream: InputStream
    private lateinit var combinedChart: CombinedChart
    private lateinit var lineDataSet: LineDataSet
    private lateinit var barDataSet: BarDataSet

    private val scope = CoroutineScope(Dispatchers.Main)
    private var isReceivingData = false

    private val dailyFourthDataList = mutableListOf<Float>()
    private var currentXValue: Float = 0f // X축 값

    private var dataBuffer = mutableListOf<List<Float>>() // 30초 동안 데이터를 버퍼에 저장
    private val chartUpdateInterval = 30_000L // 30초
    private var isFirstDataReceived = false // 첫 번째 데이터 수신 확인 플래그
    private var blueToothInfoModel : BlueToothInfoModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        binding = ActivityBluetoothChartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        init()
        resetDataAtMidnight() // 자정에 데이터 초기화
    }

    private fun init() {

        val intent = intent
        if (intent.hasExtra("blueToothData")) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                blueToothInfoModel = intent.getSerializableExtra("blueToothData", BlueToothInfoModel::class.java)
            }else{
                blueToothInfoModel =
                    intent.extras!!.getSerializable("blueToothData") as BlueToothInfoModel?
            }

        }

        // UI 초기화
        binding.tvCurrentOutPut.text = ""
        binding.tvTodayPower.text = ""
        binding.tvTodayPowerTime.text = ""
        binding.chart1.value = 0f
        binding.tvInverterPer.text = "0%"
        binding.chart2.value = 0f
        binding.tvCurrentPowerOutputPer.text = "0%"
        binding.chart3.value = 0f
        binding.tvPowerGenerationEfciency.text = "0%"

        binding.ivExst.setOnClickListener {
            finish()
        }

        // CombinedChart 초기화
        combinedChart = binding.chart4
        setupCombinedChart()

        // Bluetooth 초기화
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter ?: run {
            Toast.makeText(this, "Bluetooth를 지원하지 않는 장치입니다", Toast.LENGTH_SHORT).show()
            return
        }

        connectToBluetoothDevice() // Bluetooth 디바이스 연결
        startReceivingBluetoothData() // Bluetooth 데이터 수신 시작
    }

    // CombinedChart 설정
    private fun setupCombinedChart() {
        val xAxis: XAxis = combinedChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f // X축 단위

        val yAxisLeft: YAxis = combinedChart.axisLeft
        yAxisLeft.granularity = 10f
        combinedChart.axisRight.isEnabled = false // 오른쪽 Y축 비활성화

        // LineDataSet 설정 (선형 차트 데이터)
        lineDataSet = LineDataSet(mutableListOf(), "Bluetooth 데이터")
        lineDataSet.color = ContextCompat.getColor(this, R.color.color_gauge_chart_02)
        lineDataSet.lineWidth = 2f
        lineDataSet.setDrawCircles(false) // 선 그래프에서 점 안 보이게
        lineDataSet.setDrawValues(false)  // 값 표시 비활성화

        // BarDataSet 설정 (막대형 차트 데이터)
        barDataSet = BarDataSet(mutableListOf(), "Power Output") // 데이터의 이름 변경 가능
        barDataSet.color = ContextCompat.getColor(this, R.color.color_base_gauge_chart_bg_02)
        barDataSet.setDrawValues(false) // 막대형 차트의 값 비활성화

        // X축을 시간 단위로 표시되도록 설정
        xAxis.granularity = 30f // 30초 간격으로 X축에 데이터 추가
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val timeInSeconds = (value * 30).toInt() // 30초 간격
                val minutes = (timeInSeconds / 60) % 60
                val seconds = timeInSeconds % 60
                return String.format("%02d:%02d", minutes, seconds)
            }
        }
    }

    // Bluetooth 디바이스에 연결
    private fun connectToBluetoothDevice() {
        if(blueToothInfoModel == null){
            Toast.makeText(this, "Bluetooth 연결 실패", Toast.LENGTH_SHORT).show()
            return
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            ActivityCompat.requestPermissions(this@BlueToothDetailChartActivity,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 10)
            return
        }

        val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(blueToothInfoModel!!.address) // 실제 디바이스 주소로 변경
        val uuid = device.uuids[0].uuid // 디바이스의 UUID 가져오기
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            bluetoothSocket.connect()
            inputStream = bluetoothSocket.inputStream // InputStream 초기화
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Bluetooth 연결 실패", Toast.LENGTH_SHORT).show()
        }
    }

    // Bluetooth 데이터 실시간 수신 시작
    private fun startReceivingBluetoothData() {
        isReceivingData = true

        scope.launch {
            while (isReceivingData) {
                val newDataValues = getBluetoothDataList()

                // 첫 번째 데이터는 즉시 차트에 추가
                if (!isFirstDataReceived) {
                    processBluetoothData(newDataValues)
                    addEntryToChartImmediately(newDataValues)
                    isFirstDataReceived = true

                    // 이후에는 30초 간격으로 업데이트
                    startChartUpdateTimer()
                }

                // 데이터를 버퍼에 추가 (0.5초마다)
                dataBuffer.add(newDataValues)

                processBluetoothData(newDataValues)

                delay(500) // 0.5초 대기 (실시간 데이터 수신)
            }
        }
    }

    // 30초마다 차트 업데이트
    private fun startChartUpdateTimer() {
        scope.launch {
            while (isReceivingData) {
                delay(chartUpdateInterval) // 30초 대기

                // 30초 동안 수집된 데이터를 차트에 추가
                updateChartWithBufferedData()

                // 데이터 버퍼 초기화
                dataBuffer.clear()
            }
        }
    }

    // Bluetooth 데이터 받아오는 메소드
    private fun getBluetoothDataList(): List<Float> {
        val buffer = ByteArray(1024) // Bluetooth로부터 데이터를 받을 버퍼
        var bytesRead: Int
        val receivedDataList = mutableListOf<Float>()

        try {
            // InputStream에서 데이터 읽기
            bytesRead = inputStream.read(buffer)
            if (bytesRead > 0) {
                val receivedData = String(buffer, 0, bytesRead).trim() // 받은 데이터를 문자열로 변환
                val dataArray = receivedData.split(",") // 쉼표로 데이터를 분리
                for (data in dataArray) {
                    val value = data.trim().toFloatOrNull() // 문자열을 float로 변환
                    if (value != null) {
                        receivedDataList.add(value)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return receivedDataList // 데이터를 리스트로 반환
    }

    // Bluetooth 데이터 처리
    private fun processBluetoothData(data: List<Float>) {
        if (data.size < 4) return

        val firstData = data[0]
        val thirdData = data[2]
        val fourthData = data[3]

        // 첫 번째 데이터 표시
        binding.chart1.value = firstData
        binding.tvInverterPer.text = "$firstData %"

        // 세 번째 데이터 표시
        binding.chart2.value = thirdData
        binding.tvCurrentPowerOutputPer.text = "$thirdData kW"
        binding.tvCurrentOutPut.text = "$thirdData"

        // 네 번째 데이터 비율 계산
        val efficiency = if (firstData != 0f) {
            minOf(fourthData / firstData * 100, 100f) // 최대 100%로 제한
        } else {
            0f
        }
        binding.chart3.value = efficiency
        binding.tvPowerGenerationEfciency.text = "$efficiency %"

        // 네 번째 데이터 평균 계산
        dailyFourthDataList.add(fourthData)
        val averageFourthData = dailyFourthDataList.average().toFloat()
        binding.tvTodayPower.text = "$averageFourthData"

        // 현재 시간 표시
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        binding.tvTodayPowerTime.text = currentTime

        // 버튼 색상 업데이트
        updateButtonColors(firstData)
    }

    // 첫 번째 데이터를 즉시 차트에 추가
    private fun addEntryToChartImmediately(data: List<Float>) {
        val fourthData = data[3]
        val averageFourthData = data.average().toFloat()

        // 차트에 데이터 추가
        addEntryToChart(currentXValue, averageFourthData, fourthData)
        currentXValue += 1f // X축 값 증가
    }

    // 버퍼에 쌓인 데이터를 차트에 추가
    private fun updateChartWithBufferedData() {
        if (dataBuffer.isNotEmpty()) {
            // 버퍼에서 마지막 데이터를 사용하여 차트 업데이트
            val lastFourthData = dataBuffer.last()[3]
            val averageFourthData = dataBuffer.flatten().average().toFloat()

            // 차트에 데이터 추가
            addEntryToChart(currentXValue, averageFourthData, lastFourthData)
            currentXValue += 1f // X축 값 증가 (30초마다 1 증가)
        }
    }

    // 차트에 엔트리 추가 (LineChart와 BarChart 모두 추가)
    private fun addEntryToChart(time: Float, lineValue: Float, barValue: Float) {
        // LineChart 데이터 추가
        lineDataSet.addEntry(Entry(time, lineValue))

        // BarChart 데이터 추가
        barDataSet.addEntry(BarEntry(time, barValue))

        // CombinedData에 LineData와 BarData 추가
        val combinedData = CombinedData()
        combinedData.setData(LineData(lineDataSet))
        combinedData.setData(BarData(barDataSet))

        combinedChart.data = combinedData
        combinedChart.notifyDataSetChanged()
        combinedChart.setVisibleXRangeMaximum(10f) // 최대 10개 데이터 보이게
        combinedChart.moveViewToX(combinedData.entryCount.toFloat())
    }

    // 버튼 색상 업데이트
    private fun updateButtonColors(firstData: Float) {
        if (firstData > 0) {
            binding.gridConnectBtn.setBackgroundResource(R.drawable.gradient_color_01)
            binding.upsStandAloneBtn.setBackgroundResource(R.drawable.gradient_color_02)
        } else {
            binding.gridConnectBtn.setBackgroundResource(R.drawable.gradient_color_02)
            binding.upsStandAloneBtn.setBackgroundResource(R.drawable.gradient_color_01)
        }
    }

    // 자정에 데이터 초기화
    private fun resetDataAtMidnight() {
        val now = Calendar.getInstance()
        val nextMidnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 1)
        }

        val timeUntilMidnight = nextMidnight.timeInMillis - now.timeInMillis

        scope.launch {
            delay(timeUntilMidnight)

            // 데이터 초기화
            binding.tvCurrentOutPut.text = ""
            binding.tvTodayPower.text = ""
            binding.tvTodayPowerTime.text = ""
            binding.chart1.value = 0f
            binding.tvInverterPer.text = "0%"
            binding.chart2.value = 0f
            binding.tvCurrentPowerOutputPer.text = "0%"
            binding.chart3.value = 0f
            binding.tvPowerGenerationEfciency.text = "0%"

            // 리스트도 초기화
            dailyFourthDataList.clear()
        }
    }
}
