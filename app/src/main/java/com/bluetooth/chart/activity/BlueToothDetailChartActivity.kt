package com.bluetooth.chart.activity

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
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
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class BlueToothDetailChartActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBluetoothChartBinding
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null
    private lateinit var combinedChart: CombinedChart
    private lateinit var lineDataSet: LineDataSet
    private lateinit var barDataSet: BarDataSet

    private val scope = CoroutineScope(Dispatchers.Main)
    private var isReceivingData = false

    private val dailyFourthDataList = mutableListOf<Float>()
    private var currentXValue: Float = 0f // X축 값
    private var TAG = "TAG_Ble"

    private var dataBuffer = mutableListOf<Float>() // 30초 동안 데이터를 버퍼에 저장
    private val chartUpdateInterval = 3_600_000L // 60분 (1시간) 기준
    private var isFirstDataReceived = false // 첫 번째 데이터 수신 확인 플래그
    private var blueToothInfoModel: BlueToothInfoModel? = null

    private var collectionStartTimeMillis: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var updateTimeRunnable: Runnable
    // 마지막으로 수신한 데이터를 저장할 변수
    private var lastReceivedData: List<Float> = listOf()
    // Runnable이 실행 중인지 확인하는 변수
    private var isUpdateRunnableRunning = false

    private var lastReceivedHour: Int? = null // 이전에 받은 데이터의 시간(시)을 저장하는 변수
    // 현재 시간을 시(hour) 단위로 반환하는 함수
    private fun getCurrentHour(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.HOUR_OF_DAY)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBluetoothChartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        init()
    }

    private fun init() {
        // 페이지가 시작될 때 현재 시간을 저장
        collectionStartTimeMillis = System.currentTimeMillis()


        // BLE 정보 가져오기
        val intent = intent
        if (intent.hasExtra("blueToothData")) {
            blueToothInfoModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("blueToothData", BlueToothInfoModel::class.java)
            } else {
                intent.extras!!.getSerializable("blueToothData") as BlueToothInfoModel?
            }
        }

        // UI 초기화
        setupUI()
        setupCombinedChart()

        // Bluetooth 초기화
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter ?: run {
            Toast.makeText(this, "Bluetooth를 지원하지 않는 장치입니다", Toast.LENGTH_SHORT).show()
            return
        }

//        generateFakeData() //가상의 데이터
        connectToBLEDevice()

        // 주기적으로 시간을 업데이트하는 Runnable 설정
        //2024.11.09 금일 발전 시간
        updateTimeRunnable = Runnable {
            updateElapsedTime()
            // 1초마다 다시 실행
            handler.postDelayed(updateTimeRunnable, 1000*60)
        }

        // Runnable을 시작
        handler.post(updateTimeRunnable)

    }
    private fun updateElapsedTime() {
        // 경과 시간을 계산
        // 경과 시간을 계산
        val elapsedTimeMillis = System.currentTimeMillis() - collectionStartTimeMillis
        // 경과 시간을 0.01 단위로 표시
        val elapsedTimeMinutes = elapsedTimeMillis / (1000 * 60 * 100).toFloat()
        val minutes = (elapsedTimeMillis / (1000 * 60)) % 60
        val hours = (elapsedTimeMillis / (1000 * 60 * 60))

        Log.d(TAG, "time : $elapsedTimeMinutes [${String.format("%.2f", elapsedTimeMinutes)}]")
        // 소수점 두 자리까지 표현하여 UI 업데이트
        binding.tvTodayPowerTime.text = String.format("%02d.%02d",hours.toInt(),minutes.toInt())
        //binding.tvTodayPowerTime.text = String.format("%.2f", elapsedTimeMinutes)
    }

    private fun setupUI() {
       resetDataDisplay()
        //하단 오론쪽 ic_exit 이미지 버튼 이벤트
        binding.ivExst.setOnClickListener {
            finish()
        }
    }

    //발전효율 차트 초기화
    private fun setupCombinedChart() {
        combinedChart = binding.chart4 // 발전량 추이 차트
        val xAxis: XAxis = combinedChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f // X축 단위
        xAxis.labelCount = 24 // 발전량 추이 차트 하단 x축 label 개수 (0:0 ~23:0)
        val yAxisLeft: YAxis = combinedChart.axisLeft
        yAxisLeft.granularity = 10f
        combinedChart.axisRight.isEnabled = false // 오른쪽 Y축 비활성화

        // LineDataSet 설정
        lineDataSet = LineDataSet(mutableListOf(), "Bluetooth 데이터")
        lineDataSet.color = ContextCompat.getColor(this, R.color.color_gauge_chart_03)
        lineDataSet.lineWidth = 1f
        lineDataSet.setDrawCircles(false)
        lineDataSet.setDrawValues(false)
        lineDataSet.valueTextColor = ContextCompat.getColor(this, R.color.white) // 라벨(값) 색상 설정

        // BarDataSet 설정
        barDataSet = BarDataSet(mutableListOf(), "Power Output")
        barDataSet.color = ContextCompat.getColor(this, R.color.color_7)
        barDataSet.setDrawValues(false)
        barDataSet.valueTextColor = ContextCompat.getColor(this, R.color.white) // 라벨(값) 색상 설정


        // X축을 시간 단위로 설정
        xAxis.granularity = 1f
        xAxis.axisMinimum = 0f // 최소값을 0으로 설정
        xAxis.axisMaximum = 23f // 최대값을 23으로 설정 (0~23시)
        // X축 라벨 색상 변경
        xAxis.textColor = ContextCompat.getColor(this, R.color.white) // 원하는 색상으로 변경
        // 차트 왼쪽 세로 라벨 (0~600)kw 값정의
        yAxisLeft.granularity = 100f
        yAxisLeft.axisMinimum = 0f
        yAxisLeft.axisMaximum = 600f
        // X축 라벨 색상 변경
        yAxisLeft.textColor = ContextCompat.getColor(this, R.color.white) // 원하는 색상으로 변경
        //하단 라벨 벨류 정의
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                // value를 0~23 사이의 정수로 제한하여 시간으로 표현
                val hour = value.toInt().coerceIn(0, 23)
                Log.d(TAG, "getFormattedValue: $hour")
                return "${hour}:00"
            }
        }
        combinedChart.setVisibleXRange(24f, 24f) // X축의 보이는 범위를 24시간으로 고정
        combinedChart.isDragEnabled = false // 드래그 비활성화
        combinedChart.isScaleXEnabled = false // X축 스케일 비활성화
        combinedChart.isScaleYEnabled = false // Y축 스케일 비활성화
    }

    //BLE 기기 접속
    private fun connectToBLEDevice() {
        if (blueToothInfoModel == null) {
            Toast.makeText(this, "Bluetooth 연결 실패", Toast.LENGTH_SHORT).show()
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 10)
            return
        }

        val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(blueToothInfoModel!!.address)
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            try {
                Log.d(TAG, " [$status]- [$newState] gatt : $gatt")
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    Log.d(TAG, "BLE 연결 성공")
                    if (ActivityCompat.checkSelfPermission(
                            this@BlueToothDetailChartActivity,
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
                        return
                    }
                    gatt.discoverServices()
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Log.d(TAG, "BLE 연결 해제")
                    gatt.close()
                }
            } catch (e: Exception) {
                TODO("Not yet implemented")
                Log.d(TAG, "msg: ${e.printStackTrace()}")

            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            try {
                Log.d(TAG, "onServicesDiscovered : $status [${status == BluetoothGatt.GATT_SUCCESS}]")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val services = gatt.services
                    services.forEach { service ->
                        Log.d(TAG, "Service UUID: ${service.uuid}")
                        service.characteristics.forEach { characteristic ->
                            Log.d(TAG, "Characteristic UUID: ${characteristic.uuid}")
                            val service: BluetoothGattService? = gatt.getService(service.uuid)
//                            Log.d(TAG, "onServicesDiscovered : $service]")

                            service?.let {
                                val characteristic: BluetoothGattCharacteristic? = service.getCharacteristic(characteristic.uuid)

                                characteristic?.let {
                                    if (ActivityCompat.checkSelfPermission(
                                            this@BlueToothDetailChartActivity,
                                            Manifest.permission.BLUETOOTH_CONNECT
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        ActivityCompat.requestPermissions(this@BlueToothDetailChartActivity, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 10)
                                        return
                                    }

                                    if (it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                                        // 알림 활성화
                                        gatt.setCharacteristicNotification(it, true)

                                        // Client Characteristic Configuration Descriptor 설정
                                        val descriptorUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                                        val descriptor = it.getDescriptor(descriptorUuid)

                                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                                        // Descriptor 작성 요청
                                        gatt.writeDescriptor(descriptor)
                                    }

                                }
                            }

                        }
                    }

                }
            }catch (e:Throwable){
                Log.e(TAG,"e: ${e.printStackTrace()}")
            }
        }
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Descriptor written successfully: ${descriptor.uuid}")

                // Descriptor가 성공적으로 작성된 후 characteristic을 읽습니다.
                val characteristic = descriptor.characteristic
                if (ActivityCompat.checkSelfPermission(
                        this@BlueToothDetailChartActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(this@BlueToothDetailChartActivity, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 10)
                    return
                }
                Log.d(TAG, "Descriptor written successfully: ${characteristic.value}")

                gatt.readCharacteristic(characteristic) // 여기에서 characteristic 값을 읽습니다.
            } else {
                Log.e(TAG, "Failed to write descriptor: $status")
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic read successfully: ${characteristic.uuid} - Value: ${characteristic.value}")
            } else {
                Log.e(TAG, "Failed to read characteristic: $status")
            }
        }
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {


            val data = characteristic.value
            Log.e(TAG, "onCharacteristicChanged : $data")

            Log.e(TAG, "onCharacteristicChanged : $data")

            // 데이터를 변환하여 List<Float> 형태로 저장
            lastReceivedData = parseBLEData(data)

            // 처음에만 Runnable 시작
            if (lastReceivedData.isNotEmpty() && !isUpdateRunnableRunning) {
                isUpdateRunnableRunning = true
                handler.post(updateRunnable)
            }
            addEntryToChartImmediately(lastReceivedData)

        }
    }

    //10초마다 차트 1,2,3  데이터 갱신 (핸들러)
    private val updateRunnable = object : Runnable {
        override fun run() {
            // 데이터를 처리하고 화면에 그리는 함수 호출
            processBluetoothData(lastReceivedData)

            // 10초 후에 다시 실행
            handler.postDelayed(this, 10000)
        }
    }



    // 데이터 초기화 함수
    private fun resetDataDisplay() {
        binding.tvCurrentOutPut.text = "0.0" //현재 출력 textView
        binding.tvTodayPower.text = "0.0" //금일 계통연계 전력량 textView
        binding.tvTodayPowerTime.text = "0" //금일 계통연계 시간 textView
        binding.chart1.value = 0f //GRID VOLTAGE 차트 value값
        binding.tvInverterPer.text = "0 V" //GRID VOLTAGE textView
        binding.chart2.value = 0f //LOAD POWER 차트값
        binding.tvCurrentPowerOutputPer.text = "0 W" //LOAD POWER textView
        binding.chart3.value = 0f  //피크 전력 감지 계통연계 주입 전류 차트
        binding.tvPowerGenerationEfciency.text = "0 A" //피크 전력 감지 계통연계 주입 전류 textView

        dailyFourthDataList.clear()
    }
    @SuppressLint("SetTextI18n")
  /*  private fun processBLEData(data: ByteArray) {
        // 데이터 처리 로직 구현 (예: 파싱, 차트에 추가)
        // 예시: 데이터를 Float 리스트로 변환 후 차트에 추가
        val values = parseBLEData(data)
//값이 비어있으면 데이터를 초기화
        if(values.isEmpty()){
            Log.e(TAG, "불루투스 데이터를 받아올수 없어요")
            // 데이터 초기화
            resetDataDisplay()
            return
        }

        scope.launch {

            // 차트1,차트2,차트3 부분 차트화
            processBluetoothData(values)
            addEntryToChartImmediately(values)
        }
    }*/
    // 첫 번째 데이터를 즉시 차트에 추가
    private fun addEntryToChartImmediately(data: List<Float>) {
        if(data.isEmpty()) return
        val fourthData = data[3]
        dataBuffer.add(fourthData) // 버퍼에 추가

        // 첫 번째 데이터는 즉시 차트에 추가
        if (!isFirstDataReceived) {
            addHourlyAverageToChart()
            isFirstDataReceived = true
            lastReceivedHour = getCurrentHour() // 첫 데이터 수신 시간(시) 기록

        }
// 시간 비교를 통해 시간이 달라졌을 때 업데이트 로직 실행
        val currentHour = getCurrentHour()
        lastReceivedHour?.let { previousHour ->
            if (currentHour != previousHour) { // 시간이 달라졌는지 확인
                addHourlyAverageToChart() // 한 시간 동안 수집한 데이터의 평균을 차트에 추가
                dataBuffer.clear() // 데이터 버퍼 초기화
                lastReceivedHour = currentHour // 시간 갱신
            }
        }
    }
    // 1시간 차트 업데이트

    //받아온 데이터를 String 데이터로 변환
    private fun parseBLEData(data: ByteArray): List<Float> {
        Log.e(TAG, "parseBLEData: $data")

        // ByteArray를 String으로 변환
        val dataString = String(data)
        Log.e(TAG, "parseBLEData 2: $dataString")

        // 문자열을 쉼표로 분리하여 리스트로 변환
        val stringValues = dataString.split(",")
        Log.e(TAG, "parseBLEData3: $stringValues")

        // 각 값을 Float로 변환하여 리스트에 저장
        val floatValues = mutableListOf<Float>()
        for (value in stringValues) {
            // 공백 제거 후 변환
            val trimmedValue = value.trim()
            if (trimmedValue.isNotEmpty()) {
                try {
                    Log.e(TAG,"trimmedValue : $trimmedValue")
                    floatValues.add(trimmedValue.toFloat())
                } catch (e: NumberFormatException) {
                    Log.e(TAG, "Invalid number format: $trimmedValue", e)
                }
            }
        }

        return floatValues
    }

    // Bluetooth 데이터 처리
    @SuppressLint("SetTextI18n")
    private fun processBluetoothData(data: List<Float>) {
        if (data.size < 4) return
        if (data.size  > 4) return
        val firstData = data[0] //1번데이터
        val thirdData = data[2] //3번 데이터
        val fourthData = data[3] //4번 데이터

        // 첫 번째 데이터 표시
        val number = firstData.toDouble()
        val rounded = (number * 10).roundToInt() / 10.0 // 소수 첫째 자리만

        val number2 = thirdData.toDouble()
        val rounded2 = (number2 * 10).roundToInt() / 10.0 //3번데이터 소수 첫째 자리만

        /**
         * 첫번째 그래프
         */
        val maxFirstData = 264.0

        //2024.10.27 데이터값 1번 차트값이 220보다 클경우 그래프 최대치 100%로
        val chart1Value: Float = if (firstData >= maxFirstData) {
            100f
        } else {
            (firstData / maxFirstData * 100).toFloat()
        }


        binding.chart1.value = chart1Value //GRID VOLTAGE 차트값
        binding.tvInverterPer.text = "$rounded V"  //GRID VOLTAGE textView 값, 기획서 1번

        /**
         * 두번째 그래프
         */
        //2024.10.27 데이터값 3번 값  627.12 보다 클경우 그래프 최대치 100%로
// 3번 값에 대한 처리
        val maxChart2Value = 6000.0 // 최대값 6000을 기준으로 설정
        // 3번 값에 대한 처리
        val chart2Value: Float = if (thirdData >= maxChart2Value) {
            100f
        } else {
            // 100% 스케일로 변환
            (thirdData / maxChart2Value * 100).toFloat()
        }
        // 세 번째 데이터 표시
        binding.chart2.value = chart2Value
        binding.tvCurrentPowerOutputPer.text = "$rounded2 W" //LOAD POWER textView 값, 기획서 2번
        binding.tvCurrentOutPut.text = "$rounded2" // LOAD POWER textView값 기획서 2번

        // 네 번째 데이터 비율 계산
        //2024.10.27  차트 3 번관련해서 1번데이터값이 220보다 크냐 작냐에 따라 서 계산하도록 처리

        /**
         * 세번째 그래프
         */
        val maxEfficiency = 16.0 // 최대값 16을 100%로 기준
        var rawEfficiency = 0.0f
        val efficiency = if (firstData != 0f) {
            // 4번째 데이터 / 1번째 데이터
             rawEfficiency = fourthData / firstData
            // 16을 기준으로 100% 스케일로 변환
            val scaledEfficiency = (rawEfficiency / maxEfficiency * 100).toFloat()
            minOf(scaledEfficiency, 100f) // 최대 100%로 제한
        } else {
            0f
        }
        Log.d(TAG,"value 3 : ${fourthData / firstData} | ${((fourthData / firstData) / maxEfficiency * 100).toFloat()} [${minOf(((fourthData / firstData) / maxEfficiency * 100).toFloat(), 100f) }]")

        val rounded3 = (rawEfficiency * 10).roundToInt() / 10.0

        binding.chart3.value = efficiency // 피크전력감지계통연계주입전류 차트값
        binding.tvPowerGenerationEfciency.text = "$rounded3 A" // 피크전력감지계통연계주입전류 텍스트값, 기획서 3번

        // 네 번째 데이터 평균 계산
        dailyFourthDataList.add(fourthData)
        val averageFourthData = dailyFourthDataList.average().toFloat()
        binding.tvTodayPower.text = "$averageFourthData" //금일발전량 평균값 기획서 4번
        Log.d(TAG,"sum::${dailyFourthDataList.sum()} [${dailyFourthDataList.size}] [${dailyFourthDataList.sum() /dailyFourthDataList.size }]")

        Log.d(TAG,"dailyFourthDataList:${dailyFourthDataList.toList()} averageFourthData $averageFourthData")

        // 버튼 색상 업데이트
        updateButtonColors(firstData)
    }


    private fun addHourlyAverageToChart() {
        if (dataBuffer.isNotEmpty()) {
            // 데이터의 평균을 계산
            //시간당 데이터를 dataBuffer에 담고 dataBuffer의 average 값을 추출하여 차트에 전달
            val average = dataBuffer.average().toFloat()

            // 시간 단위의 차트에 평균 데이터 추가
            addEntryToChart(currentXValue, average, average)
            currentXValue += 1f // 다음 시간대로 이동
        }
    }


    // 차트에 엔트리 추가 (LineChart와 BarChart 모두 추가)
    private var collectionStartTime = Calendar.getInstance().get(Calendar.HOUR_OF_DAY).toFloat() // 수집 시작 시간

    // 기존 addEntryToChart 메서드 사용
    private fun addEntryToChart(time: Float, lineValue: Float, barValue: Float) {
        Log.d(TAG, "addEntryToChart: $time, $lineValue, $barValue")
        // 수집 시작 시간 기준으로 X축 값을 설정
        //중요 : collectionStartTime 에 의해 차트가 그려지기 시작하는 시간대가 달라짐
        val adjustedTime = collectionStartTime + time

        // LineChart 데이터 추가
        lineDataSet.addEntry(Entry(adjustedTime, barValue))

        // BarChart 데이터 추가
        barDataSet.addEntry(BarEntry(adjustedTime, barValue))

        val barData = BarData(barDataSet)
        barData.barWidth = 0.2f

        // CombinedData에 LineData와 BarData 추가
        val combinedData = CombinedData()
        combinedData.setData(LineData(lineDataSet))
        combinedData.setData(barData)

        combinedChart.data = combinedData
        combinedChart.notifyDataSetChanged()
        combinedChart.invalidate()

        // X축의 보이는 범위를 조정하여 수집 시작 시간에 맞춰 그래프 이동
        combinedChart.setVisibleXRangeMaximum(10f) // 10시간의 범위를 화면에 보이게 설정
        combinedChart.moveViewToX(adjustedTime) // 현재 데이터에 맞춰 화면 이동
    }

    // 버튼 색상 업데이트
    //Grid Connect, UPS Stand Alone 버튼 색상 변경
    private fun updateButtonColors(firstData: Float) {
        if (firstData > 0) {
            binding.gridConnectBtn.setBackgroundResource(R.drawable.gradient_color_01)
            binding.upsStandAloneBtn.setBackgroundResource(R.drawable.gradient_color_03)
        } else {
            binding.gridConnectBtn.setBackgroundResource(R.drawable.gradient_color_03)
            binding.upsStandAloneBtn.setBackgroundResource(R.drawable.gradient_color_02)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (ActivityCompat.checkSelfPermission(
                this@BlueToothDetailChartActivity,
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
            return
        }
        handler.removeCallbacks(updateRunnable)
        isUpdateRunnableRunning = false

        handler.removeCallbacks(updateTimeRunnable)
        bluetoothGatt?.close()
        isReceivingData = false
        scope.cancel()
    }
}
