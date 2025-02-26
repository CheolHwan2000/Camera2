package com.example.camera

import android.content.ContentValues
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.RggbChannelVector
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.SurfaceHolder
import android.view.View
import android.view.animation.TranslateAnimation
import android.widget.SeekBar
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.camera.databinding.ActivityCameraBinding

class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraBinding
    private lateinit var surfaceViewHolder: SurfaceHolder
    private lateinit var cameraManager: CameraManager
    private var cameraId = ""
    private lateinit var previewSize: Size
    private val cameraHandler = Handler(Looper.getMainLooper())
    private var cameraDevice: CameraDevice? = null
    private lateinit var previewRequestBuilder: CaptureRequest.Builder
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var isFrontCamera = false
    private var frameCount = 0
    private lateinit var imageReader: ImageReader
    private var focusDistance: Int = 50
    private var redLevel: Int = 0
    private var greenLevel: Int = 0
    private var blueLevel: Int = 0
    private var resolution: String = ""
    private var whiteBalanceSwitchCheckValue: Boolean = false
    private var focusSwitchCheckValue: Boolean = false
    private var resolutionSwitchCheckValue: Boolean = false
    private lateinit var sharedPreferences: SharedPreferences
    private var isSettingsVisible = false

    // 스위치 상태 업데이트 맵
    private val switchCheckValues = mutableMapOf(
        "화이트 밸런스" to { value: Boolean -> whiteBalanceSwitchCheckValue = value },
        "초점도" to { value: Boolean -> focusSwitchCheckValue = value },
        "해상도" to { value: Boolean -> resolutionSwitchCheckValue = value }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // SharedPreference 초기화
        initSharedPreferences()

        surfaceViewHolder = binding.sfvMain.holder
        setupCamera()

        binding.btnReverse.setOnClickListener { toggleCamera() }
        binding.btnShot.setOnClickListener { takePicture() }

        // 카메라 특성 조회
        setupCameraCharacteristics()

        // 설정 버튼 클릭 처리
        setupSettingsButtons()

        // SeekBar 리스너 설정
        setupSeekBars()

        // 해상도 버튼 클릭 처리
        setupResolutionButtons()

        // 스위치 초기화
        initializeSwitchButtons()
    }

    private fun initSharedPreferences() {
        sharedPreferences = getSharedPreferences("CameraSettings", MODE_PRIVATE)
        redLevel = sharedPreferences.getInt("red", 255)
        greenLevel = sharedPreferences.getInt("green", 255)
        blueLevel = sharedPreferences.getInt("blue", 255)
        focusDistance = sharedPreferences.getInt("focusDistance", 50)
        resolution = sharedPreferences.getString("resolution", "").toString()
        whiteBalanceSwitchCheckValue = sharedPreferences.getBoolean("whiteBalanceSwitchCheckValue", false)
        focusSwitchCheckValue = sharedPreferences.getBoolean("focusSwitchCheckValue", false)
        resolutionSwitchCheckValue = sharedPreferences.getBoolean("resolutionSwitchCheckValue", false)

        Log.e("CameraActivity_Settings", "red : $redLevel")
        Log.e("CameraActivity_Settings", "green : $greenLevel")
        Log.e("CameraActivity_Settings", "blue : $blueLevel")
        Log.e("CameraActivity_Settings", "focus : $focusDistance")
        Log.e("CameraActivity_Settings", "focus : $resolution")
        Log.e("CameraActivity_Settings", "focus : $whiteBalanceSwitchCheckValue")
        Log.e("CameraActivity_Settings", "focus : $focusSwitchCheckValue")
    }

    private fun setupCameraCharacteristics() {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val focusModes = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
        Log.e("CameraActivity", "최소 초점 거리: $focusModes")
    }

    private fun setupSettingsButtons() {
        val layoutSettingsMap = mapOf(
            binding.btnWb to binding.layoutSettingsWb,
            binding.btnFocus to binding.layoutSettingsFocus,
            binding.btnResolution to binding.layoutSettingsResolution,
            binding.btnAuto to binding.layoutSettingsAuto
        )

        var currentOpenPanel: View? = null

        layoutSettingsMap.forEach { (buttonId, layout) ->
            buttonId.setOnClickListener {
                if (isSettingsVisible) {
                    currentOpenPanel?.let { hideSettingsPanel(it) }
                }
                if (currentOpenPanel != layout) {
                    showSettingsPanel(layout)
                    currentOpenPanel = layout
                    isSettingsVisible = true
                } else {
                    hideSettingsPanel(layout)
                    currentOpenPanel = null
                    isSettingsVisible = false
                }
            }
        }

        binding.root.setOnClickListener {
            if (isSettingsVisible) {
                hideSettingsPanel(binding.layoutSettingsWb)
                hideSettingsPanel(binding.layoutSettingsFocus)
                hideSettingsPanel(binding.layoutSettingsResolution)
                hideSettingsPanel(binding.layoutSettingsAuto)
                isSettingsVisible = false
            }
            true
        }
    }

    private fun setupSeekBars() {
        binding.redSeekBar.setOnSeekBarChangeListener(createWhiteBalanceSeekBarListener { progress ->
            redLevel = progress
            applyWhiteBalance()
        })

        binding.greenSeekBar.setOnSeekBarChangeListener(createWhiteBalanceSeekBarListener { progress ->
            greenLevel = progress
            applyWhiteBalance()
        })

        binding.blueSeekBar.setOnSeekBarChangeListener(createWhiteBalanceSeekBarListener { progress ->
            blueLevel = progress
            applyWhiteBalance()
        })

        binding.focusSeekBar.setOnSeekBarChangeListener(createFocusSeekBarListener { progress ->
            focusDistance = progress
            applyFocus()
        })
    }

    private fun createWhiteBalanceSeekBarListener(onProgressChanged: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            onProgressChanged(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            // 스위치 상태 업데이트 (예시: focus)
            updateSwitchState(binding.btnSwitchWhiteBalance, "화이트 밸런스", false)
        }
    }
    private fun createFocusSeekBarListener(onProgressChanged: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            onProgressChanged(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            // 스위치 상태 업데이트 (예시: focus)
            updateSwitchState(binding.btnSwitchFocus, "초점도", false)
        }
    }

    private fun setupResolutionButtons() {
        binding.btn34.setOnClickListener {
            resolution = "3:4"
            setupCamera()
            updateSwitchState(binding.btnSwitchResolution, "해상도", false)
        }
        binding.btn916.setOnClickListener {
            resolution = "9:16"
            setupCamera()
            updateSwitchState(binding.btnSwitchResolution, "해상도", false)
        }
        binding.btn11.setOnClickListener {
            resolution = "1:1"
            setupCamera()
            updateSwitchState(binding.btnSwitchResolution, "해상도", false)
        }
        binding.btnFull.setOnClickListener {
            resolution = "FullScreen"
            setupCamera()
            updateSwitchState(binding.btnSwitchResolution, "해상도", false)
        }
    }

    private fun initializeSwitchButtons() {
        updateSwitchState(binding.btnSwitchWhiteBalance, "화이트 밸런스", whiteBalanceSwitchCheckValue)
        updateSwitchState(binding.btnSwitchFocus, "초점도", focusSwitchCheckValue)
        updateSwitchState(binding.btnSwitchResolution, "해상도", resolutionSwitchCheckValue)
        setupSwitchButton(binding.btnSwitchWhiteBalance, "화이트 밸런스")
        setupSwitchButton(binding.btnSwitchFocus, "초점도")
        setupSwitchButton(binding.btnSwitchResolution, "해상도")
    }

    override fun onPause() {
        super.onPause()
        closeCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveSettings()
    }

    private fun saveSettings() {
        val editor = sharedPreferences.edit()
        editor.putInt("focusDistance", binding.focusSeekBar.progress)
        editor.putInt("red", binding.redSeekBar.progress)
        editor.putInt("green", binding.greenSeekBar.progress)
        editor.putInt("blue", binding.blueSeekBar.progress)
        editor.putString("resolution", resolution)
        editor.putBoolean("whiteBalanceSwitchCheckValue", whiteBalanceSwitchCheckValue)
        editor.putBoolean("focusSwitchCheckValue", focusSwitchCheckValue)
        editor.putBoolean("resolutionSwitchCheckValue", resolutionSwitchCheckValue)
        editor.apply()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.e("CameraActivity", "회전 감지, 카메라 재설정")
        setupCamera()
    }

    private fun setupCamera() {
        if (resolutionSwitchCheckValue) {
            val devicePixel = getDevicePixel()
            setAutoSurfaceViewSize(devicePixel)

        } else {
            setPreviewAspectRatio(resolution)

        }
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        cameraId = getCameraId() ?: cameraManager.cameraIdList[0]


        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        previewSize = map!!.getOutputSizes(SurfaceHolder::class.java)[0]



        surfaceViewHolder.removeCallback(surfaceCallback)
        surfaceViewHolder.addCallback(surfaceCallback)

        imageReader = ImageReader.newInstance(
            previewSize.width, previewSize.height,
            ImageFormat.JPEG, 1
        )
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            image?.let {
                saveImage(it) // 이미지 저장 함수 호출
                it.close()
            }
        }, cameraHandler)
    }

    private fun getCameraId(): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            (isFrontCamera && lensFacing == CameraCharacteristics.LENS_FACING_FRONT) ||
                    (!isFrontCamera && lensFacing == CameraCharacteristics.LENS_FACING_BACK)
        }
    }

    private fun toggleCamera() {
        closeCamera()
        isFrontCamera = !isFrontCamera
        setupCamera()
        openCamera()
    }

    private fun closeCamera() {
        try {
            cameraCaptureSession?.close()
            cameraCaptureSession = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startPreview()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    closeCamera()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    closeCamera()
                }
            }, cameraHandler)
        }
    }


    private fun startPreview() {
        val surface = surfaceViewHolder.surface
        previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        previewRequestBuilder.addTarget(surface)

        val maxFpsRange = getMaxFpsRange()
        if (maxFpsRange != null) {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, maxFpsRange)
        }

        // Surface 목록에 ImageReader 추가
        val surfaces = listOf(surface, imageReader.surface)

        cameraDevice?.createCaptureSession(
            surfaces,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    onNewFrame()
                    cameraCaptureSession = session
                    session.setRepeatingRequest(
                        previewRequestBuilder.build(),
                        object : CameraCaptureSession.CaptureCallback() {
                            override fun onCaptureCompleted(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                result: TotalCaptureResult
                            ) {
                                super.onCaptureCompleted(session, request, result)
                                onNewFrame()
                                Log.e("CameraActivity", "onCaptureCompleted 호출됨")
                            }
                        },
                        cameraHandler
                    )
                    if (whiteBalanceSwitchCheckValue) {
                        setAutoWhiteBalance()
                    } else {
                        applyWhiteBalance()

                    }
                    if (focusSwitchCheckValue) {
                        setAutoFocusMode()
                        Log.e("focusSwitchCheckValue", "true 실행 됨")
                    } else {
                        applyFocus()
                        Log.e("focusSwitchCheckValue", "false 실행 됨")
                    }

                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e("CameraActivity", "카메라 세션 설정 실패")
                }
            },
            cameraHandler
        )
    }

    // fps 구하기
    private fun getMaxFpsRange(): Range<Int>? {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        return characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.maxByOrNull { it.upper }
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            openCamera()
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
        override fun surfaceDestroyed(holder: SurfaceHolder) {}
    }

    private fun getDevicePixel(): Array<Int> {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        return arrayOf(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }

//    private fun setSurfaceViewSize(devicePixel: Array<Int>) {
//        runOnUiThread {
//            val layoutParams = binding.sfvMain.layoutParams
//            val displayMetrics = resources.displayMetrics
//            val screenWidth = displayMetrics.widthPixels
//            val screenHeight = displayMetrics.heightPixels
//
//            val cameraAspectRatio = devicePixel[0].toFloat() / devicePixel[0].toFloat()
//            val screenAspectRatio = screenWidth.toFloat() / screenHeight.toFloat()
//
//            if (screenAspectRatio > cameraAspectRatio) {
//                layoutParams.height = screenHeight
//                layoutParams.width = (screenHeight * cameraAspectRatio).toInt()
//            } else {
//                layoutParams.width = screenWidth
//                layoutParams.height = (screenWidth / cameraAspectRatio).toInt()
//            }
//            binding.sfvMain.layoutParams = layoutParams
//        }
//    }


    private fun onNewFrame() {
        runOnUiThread {
            frameCount++
            // 여기서 FPS 콜백을 받을 수 있음
            Log.e("FPS CallBack", "New frame received! $frameCount")
        }
    }

    private fun setAutoSurfaceViewSize(devicePixel: Array<Int>) {
        runOnUiThread {
            val layoutParams = binding.sfvMain.layoutParams
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            val cameraAspectRatio = devicePixel[0].toFloat() / devicePixel[0].toFloat()
            val screenAspectRatio = screenWidth.toFloat() / screenHeight.toFloat()

            if (screenAspectRatio > cameraAspectRatio) {
                layoutParams.height = screenHeight
                layoutParams.width = (screenHeight * cameraAspectRatio).toInt()
            } else {
                layoutParams.width = screenWidth
                layoutParams.height = (screenWidth / cameraAspectRatio).toInt()
            }
            binding.sfvMain.layoutParams = layoutParams
        }
    }

    private fun setAutoWhiteBalance() {
        previewRequestBuilder.set(
            CaptureRequest.CONTROL_AWB_MODE,
            CaptureRequest.CONTROL_AWB_MODE_AUTO
        )
        cameraCaptureSession?.setRepeatingRequest(
            previewRequestBuilder.build(),
            null,
            cameraHandler
        )
    }

    private fun setAutoFocusMode() {
        previewRequestBuilder.set(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_AUTO
        )
        cameraCaptureSession?.setRepeatingRequest(
            previewRequestBuilder.build(),
            null,
            cameraHandler
        )
    }


    private fun applyWhiteBalance() {
        if (cameraCaptureSession == null || previewRequestBuilder == null) {
            Log.e("CameraActivity", "Camera session or request builder is null")
            return
        }

        try {
            // 카메라의 화이트 밸런스 모드를 지원하는지 확인
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val availableAwbModes =
                characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)

            // 수동 화이트 밸런스를 지원하는지 체크
            if (availableAwbModes?.contains(CameraMetadata.CONTROL_AWB_MODE_OFF) == true) {
                Log.d("CameraActivity", "Manual white balance is supported")
            } else {
                Log.e("CameraActivity", "Manual white balance is not supported on this camera")
                return
            }

            // 자동 화이트 밸런스를 끄고 수동 화이트 밸런스를 설정
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AWB_MODE,
                CaptureRequest.CONTROL_AWB_MODE_OFF
            )

            // 색상 보정값을 설정 (redLevel, greenLevel, blueLevel을 float으로 변환)
            val gains = RggbChannelVector(
                redLevel.toFloat(),
                greenLevel.toFloat(),
                blueLevel.toFloat(),
                greenLevel.toFloat()
            )

            // 색상 보정값을 CaptureRequest에 설정
            previewRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_GAINS, gains)

            // 반복적인 캡처 요청을 보내어 화이트 밸런스를 적용
            cameraCaptureSession!!.setRepeatingRequest(
                previewRequestBuilder.build(),
                null,
                cameraHandler
            )

            Log.d(
                "CameraActivity",
                "White balance applied: red=$redLevel, green=$greenLevel, blue=$blueLevel"
            )

        } catch (e: CameraAccessException) {
            // 예외 발생 시 로그 출력
            Log.e("CameraActivity", "Error applying white balance", e)
        }
    }

    private fun applyFocus() {
        if (cameraDevice == null || cameraCaptureSession == null) {
            Log.e("CameraActivity", "CameraDevice or CameraCaptureSession is null")
            return
        }

        try {

            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_OFF
            )

            // Surface 추가 (필수)
            previewRequestBuilder.addTarget(surfaceViewHolder.surface)

            // 저장된 초점 거리 적용
            previewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance.toFloat())

            cameraCaptureSession!!.setRepeatingRequest(
                previewRequestBuilder.build(),
                null,
                cameraHandler
            )
            Log.e("CameraActivity", "Focus 적용 완료! : ${focusDistance.toFloat()}")
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }


    private fun takePicture() {
        if (cameraDevice == null) {
            Log.e("CameraActivity", "카메라가 열려 있지 않음")
            return
        }

        try {
            val captureBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(imageReader.surface) // 반드시 ImageReader의 Surface 추가

            captureBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            captureBuilder.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
            )

            cameraCaptureSession?.capture(
                captureBuilder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        super.onCaptureCompleted(session, request, result)
                        Toast.makeText(applicationContext, "사진 촬영 완료!", Toast.LENGTH_SHORT).show()
                        startPreview() // 촬영 후 프리뷰 다시 시작
                    }
                },
                cameraHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }


    private fun setPreviewAspectRatio(ratio: String) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val selectedSize = when (ratio) {
            "3:4" -> getBestPreviewSize(3, 4, screenWidth, screenHeight)
            "9:16" -> getBestPreviewSize(9, 16, screenWidth, screenHeight)
            "1:1" -> getBestPreviewSize(1, 1, screenWidth, screenHeight)
            else -> Size(screenWidth, screenHeight)  // Full Screen
        }

        setSurfaceViewSize(selectedSize)
    }

    private fun getBestPreviewSize(
        widthRatio: Int,
        heightRatio: Int,
        screenWidth: Int,
        screenHeight: Int
    ): Size {
        val aspectRatio = widthRatio.toFloat() / heightRatio.toFloat()
        val targetWidth: Int
        val targetHeight: Int

        // 화면 비율에 맞는 크기를 설정
        if (screenWidth.toFloat() / screenHeight.toFloat() > aspectRatio) {
            targetHeight = screenHeight
            targetWidth = (screenHeight * aspectRatio).toInt()
        } else {
            targetWidth = screenWidth
            targetHeight = (screenWidth / aspectRatio).toInt()
        }

        return Size(targetWidth, targetHeight)
    }

    private fun setSurfaceViewSize(size: Size) {
        runOnUiThread {
            val layoutParams = binding.sfvMain.layoutParams
            layoutParams.width = size.width
            layoutParams.height = size.height
            binding.sfvMain.layoutParams = layoutParams
        }
    }


    // 이미지 저장
    private fun saveImage(image: Image) {
        saveImageToGallery(image) // 갤러리에 저장

    }

    // 갤러리에 저장
    private fun saveImageToGallery(image: Image) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val fileName = "IMG_${System.currentTimeMillis()}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera") // 📁 DCIM/Camera 폴더에 저장
        }

        val resolver = contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        imageUri?.let { uri ->
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(bytes) // 파일 저장
                outputStream.flush()
            }
            Log.d("CameraActivity", "사진 저장됨: $uri")
            runOnUiThread {
                Toast.makeText(applicationContext, "사진이 갤러리에 저장되었습니다!", Toast.LENGTH_SHORT).show()
            }
        } ?: Log.e("CameraActivity", "사진 저장 실패")

        image.close()
    }

    private fun showSettingsPanel(view: View) {
        // 애니메이션 없이 바로 보이도록 설정
        view.visibility = View.VISIBLE
        val animate = TranslateAnimation(0f, 0f, view.height.toFloat(), 0f)
        animate.duration = 300
        view.startAnimation(animate)
    }
//    private fun showSettingsPanel(view: View) {
//        view.visibility = View.VISIBLE
//        val animate = TranslateAnimation(0f, 0f, view.height.toFloat(), 0f)
//        animate.duration = 300
//        view.startAnimation(animate)
//    }

    private fun hideSettingsPanel(view: View) {
        // 애니메이션 없이 바로 사라지도록 설정
        view.visibility = View.GONE
    }
//    private fun hideSettingsPanel(view: View) {
//        val animate = TranslateAnimation(0f, 0f, 0f, view.height.toFloat())
//        animate.duration = 300
//        view.startAnimation(animate)
//        view.postDelayed({ view.visibility = View.GONE }, 300)
//    }

    private fun updateSwitchState(switchButton: Switch, label: String, isChecked: Boolean) {
        switchButton.apply {
            text = "$label ${if (isChecked) "자동" else "수동"}"
            this.isChecked = isChecked
        }
    }


    // RGB 값을 가져와서 색온도 조정
    private fun updateColorPreview() {
        val red = binding.redSeekBar.progress
        val green = binding.greenSeekBar.progress
        val blue = binding.blueSeekBar.progress

        // RGB 값으로 색을 설정
        val color = Color.rgb(red, green, blue)
        binding.tvSettingsTitleWb.setBackgroundColor(color)


    }

    private fun setupSwitchButton(switchButton: Switch, buttonLabel: String) {
        switchButton.setOnCheckedChangeListener { _, isChecked ->
            switchButton.text = "$buttonLabel ${if (isChecked) "자동" else "수동"}"
            switchCheckValues[buttonLabel]?.invoke(isChecked)
            when (buttonLabel) {
                "화이트 밸런스" -> startPreview()
                "초점도" -> startPreview()
                "해상도" -> setupCamera()
            }
        }
    }
}


