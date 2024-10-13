import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.bluetooth.chart.module.interface_callback.PermissionResultCallback

object CheckPermissionManager {

    private val PERMISSIONS = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

    private val PERMISSIONS_S_ABOVE = arrayOf(
        android.Manifest.permission.BLUETOOTH_SCAN,
        android.Manifest.permission.BLUETOOTH_CONNECT,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

    const val REQUEST_ALL_PERMISSION = 2

    fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (ActivityCompat.checkSelfPermission(context, permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    fun checkAndRequestPermissions(activity: Activity,callback: PermissionResultCallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermissions(activity, PERMISSIONS_S_ABOVE)) {
                ActivityCompat.requestPermissions(activity, PERMISSIONS_S_ABOVE, REQUEST_ALL_PERMISSION)
            }else{
                callback.onPermissionsGranted()

            }
        } else {
            if (!hasPermissions(activity, PERMISSIONS)) {
                ActivityCompat.requestPermissions(activity, PERMISSIONS, REQUEST_ALL_PERMISSION)
            }else{
                callback.onPermissionsGranted()

            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun handlePermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
        activity: Activity,
        callback: PermissionResultCallback
    ) {
        when (requestCode) {
            REQUEST_ALL_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(activity, "Permissions granted!", Toast.LENGTH_SHORT).show()
                    callback.onPermissionsGranted()
                } else {
                    callback.onPermissionsDenied()
                    ActivityCompat.requestPermissions(activity, permissions, REQUEST_ALL_PERMISSION)
                    Toast.makeText(activity, "Permissions must be granted", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
