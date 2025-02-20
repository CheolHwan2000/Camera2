package com.example.camera

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
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
    var flag = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val permissions = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        // 카메라 사용 권한 승인
        val checkCameraFlag = ContextCompat.checkSelfPermission(this, permissions[0])
        if (checkCameraFlag == PackageManager.PERMISSION_GRANTED) {
            flag = true
        } else {
            // 승인 요청
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE)
        }

        binding.btnPreview.setOnClickListener {
            // 권한 승인 완료 시
            if (flag) {
                val intent = Intent(this, CameraActivity::class.java)
                startActivity(intent)
            } else {
                // 권한이 없으면 사용자에게 알림
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                flag = true
            } else {
                // 권한을 거절한 경우, 다시 권한을 요청하거나 앱 종료하지 않고 알림만 띄운다
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])) {
                    // 권한 요청을 거절했으므로 사용자에게 권한 요청을 다시 할 수 있는지 안내
                    Toast.makeText(this, "권한을 거부하셨습니다. 권한을 허용해야 앱을 사용할 수 있습니다.", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    // 권한 요청을 영구적으로 거부한 경우, 설정에서 수동으로 권한을 활성화하도록 유도
                    Toast.makeText(this, "권한을 영구적으로 거부하셨습니다. 설정에서 권한을 활성화해주세요.", Toast.LENGTH_LONG)
                        .show()
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri: Uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }
            }
        }
    }
}