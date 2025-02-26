package com.example.camera

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.camera.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    val REQUEST_CODE = 100
    val STORAGE_REQUEST_CODE = 101
    var flag = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val permissions = mutableListOf(
            android.Manifest.permission.CAMERA
        )

        // Android 9 이하에서는 저장소 권한 필요
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        checkAndRequestPermissions(permissions.toTypedArray())

        binding.btnSetting.setOnClickListener {
            val intent = Intent(this, SettingActivity::class.java)
            startActivity(intent)
        }
        binding.btnPreview.setOnClickListener {
            // 권한 승인 완료 시
            if (flag) {
                val intent = Intent(this, CameraActivity::class.java)
                startActivity(intent)
            } else {
                // 권한이 없으면 사용자에게 알림
                Toast.makeText(this, "카메라 및 저장소 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkAndRequestPermissions(permissions: Array<String>) {
        val deniedPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (deniedPermissions.isEmpty()) {
            flag = true
        } else {
            ActivityCompat.requestPermissions(this, deniedPermissions.toTypedArray(), REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) {
            flag = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (!flag) {
                // 권한 거부한 경우 안내 메시지
                if (permissions.any { !ActivityCompat.shouldShowRequestPermissionRationale(this, it) }) {
                    Toast.makeText(this, "권한을 영구적으로 거부하셨습니다. 설정에서 권한을 활성화해주세요.", Toast.LENGTH_LONG).show()
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri: Uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "권한을 거부하셨습니다. 권한을 허용해야 앱을 사용할 수 있습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
