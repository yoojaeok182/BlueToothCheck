package com.bluetooth.chart.module.data

import java.io.Serializable

class BlueToothInfoModel : Serializable {
    var bondState : String = ""
    var uuid  = ""
    var name =""
    var rssi =""
    var pairingvariant = ""
    var orignName=""
    var deviceClass  = ""
    var address =""
    var deviceMajorClass  = ""
    var isOpen = false

}