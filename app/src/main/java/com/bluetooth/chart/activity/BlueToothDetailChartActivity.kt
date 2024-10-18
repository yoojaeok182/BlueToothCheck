package com.bluetooth.chart.activity

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bluetooth.chart.R
import com.bluetooth.chart.databinding.ActivityBluetoothChartBinding
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class BlueToothDetailChartActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBluetoothChartBinding
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var combinedChart: CombinedChart
    private lateinit var lineDataSet: LineDataSet
    private lateinit var barDataSet: BarDataSet // 막대형 데이터셋 추가

    private val scope = CoroutineScope(Dispatchers.Main)
    private var isReceivingData = false

    // 네 번째 데이터 평균 계산을 위한 리스트
    private val dailyFourthDataList = mutableListOf<Float>()
    private var currentXValue: Float = 0f // X축 값

    private var dataBuffer = mutableListOf<List<Float>>() // 30초 동안 데이터를 버퍼에 저장
    private val chartUpdateInterval = 30_000L // 30초
    private var isFirstDataReceived = false // 첫 번째 데이터 수신 확인 플래그

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
//        enableEdgeToEdge()

        binding = ActivityBluetoothChartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        init()
        resetDataAtMidnight() // 자정에 데이터 초기화
    }

    private fun init() {
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

        // X축을 30초 단위로 표시되도록 설정
        xAxis.granularity = 3600f // 30초 간격으로 X축에 데이터 추가
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                // 30초 단위의 시간을 "MM:ss" 형식으로 표시
                val timeInSeconds = value * 3600
                val minutes = (timeInSeconds / 3600).toInt()
                val seconds = (timeInSeconds % 3600 / 60).toInt()
                return String.format("%02d:%02d", minutes, seconds)
            }
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

    // Bluetooth 데이터 받아오는 메소드 (임의로 리스트 반환)
    private fun getBluetoothDataList(): List<Float> {
        // 실제 구현에서는 BluetoothDevice로부터 데이터를 받는 로직 추가
        // 랜덤값 생성 (0.0f에서 100.0f 사이의 float 값)
        val random = Random()
        val random1 = Random()

        val randomData1 = random.nextFloat() * 100 // 0 ~ 100
        val randomData4 = random1.nextFloat() * 100 // 0 ~ 100

        return listOf(randomData1, 25.0f, 65.0f, randomData4)
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

        // 두 번째 데이터 표시
        binding.chart2.value = thirdData
        binding.tvCurrentPowerOutputPer.text = "$thirdData kW"
        binding.tvCurrentOutPut.text = "$thirdData"

        // 세 번째 데이터: 네 번째 데이터 / 첫 번째 데이터 비율 계산 및 표시
        val efficiency = if (firstData != 0f) {
            minOf(fourthData / firstData * 100, 100f) // 최대 100%로 제한
        } else {
            0f
        }
        binding.chart3.value = efficiency
        binding.tvPowerGenerationEfciency.text = "$efficiency %"

        // 네 번째 데이터 평균 계산
        dailyFourthDataList.add(fourthData)
        val averageFourthData = dailyFourthDataList.sum() / dailyFourthDataList.size
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
