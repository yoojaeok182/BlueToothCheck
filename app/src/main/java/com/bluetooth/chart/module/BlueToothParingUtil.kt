package com.bluetooth.chart.module

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import java.io.IOException

object BlueToothParingUtil {
    // 페어링 요청 (버튼 클릭 시 호출)
    fun pairDevice(device: BluetoothDevice) {
        try {
            val method = device.javaClass.getMethod("createBond")
            method.invoke(device)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 장치와 연결하기 (BluetoothSocket)
    fun connectToDevice(activity: Activity,device: BluetoothDevice) {
        // 연결할 UUID를 설정
        if (ActivityCompat.checkSelfPermission(
                activity,
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
            ActivityCompat.requestPermissions(activity,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 11)

            return
        }
        val uuid =
            device.uuids[0].uuid  // 장치가 지원하는 UUID 중 첫 번째 사용
        var bluetoothSocket: BluetoothSocket? = null
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            bluetoothSocket?.connect()

            // 연결 성공 시 처리
            Toast.makeText(activity, "Connected to ${device.name}", Toast.LENGTH_SHORT).show()

            // 데이터를 주고받는 작업을 여기에 구현할 수 있습니다.
            // 예: bluetoothSocket.inputStream, bluetoothSocket.outputStream
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(activity, "Connection failed", Toast.LENGTH_SHORT).show()

            try {
                bluetoothSocket?.close()
            } catch (closeException: IOException) {
                closeException.printStackTrace()
            }
        }
    }
}