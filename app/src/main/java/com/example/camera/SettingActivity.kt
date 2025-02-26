package com.example.camera

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.camera.databinding.ActivitySettingBinding

class SettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingBinding
    private lateinit var sharedPreferences: SharedPreferences

    private var resolution: String = ""
    private var whiteBalanceSwitchCheckValue: Boolean = false
    private var focusSwitchCheckValue: Boolean = false
    private var resolutionSwitchCheckValue: Boolean = false

    // 스위치 상태 업데이트 맵
    private val switchCheckValues = mutableMapOf(
        "화이트 밸런스" to { value: Boolean -> whiteBalanceSwitchCheckValue = value },
        "초점도" to { value: Boolean -> focusSwitchCheckValue = value },
        "해상도" to { value: Boolean -> resolutionSwitchCheckValue = value }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("CameraSettings", MODE_PRIVATE)

        // 설정 초기화
        initSettings()

        // 스위치 버튼 설정
        setupSwitchButton(binding.btnSwitchWhiteBalance, "화이트 밸런스", binding.linearLayout)
        setupSwitchButton(binding.btnSwitchFocus, "초점도", binding.linearLayout2)
        setupSwitchButton(binding.btnSwitchResolution, "해상도", binding.linearLayout3)

        // 해상도 선택 PopupMenu 설정
        setupResolutionMenu()

        // SeekBar 리스너 설정 (RGB 값 변경 시)
        setupSeekBarListeners()

        // 저장 버튼 클릭 시
        binding.btnSave.setOnClickListener {
            saveSettings()
            showSaveToast()
            finish()
        }
    }

    private fun initSettings() {
        // SharedPreferences에서 값 불러오기
        with(sharedPreferences) {
            binding.redSeekBar.progress = getInt("red", 255)
            binding.greenSeekBar.progress = getInt("green", 255)
            binding.blueSeekBar.progress = getInt("blue", 255)
            binding.focusSeekBar.progress = getInt("focusDistance", 50)
            resolution = getString("resolution", "비율 선택").toString()
            binding.tvAspectRatio.text = resolution
            whiteBalanceSwitchCheckValue = getBoolean("whiteBalanceSwitchCheckValue", false)
            focusSwitchCheckValue = getBoolean("focusSwitchCheckValue", false)
            resolutionSwitchCheckValue = getBoolean("resolutionSwitchCheckValue", false)
        }

        // 스위치 초기화
        updateSwitchState(binding.btnSwitchWhiteBalance, "화이트 밸런스", whiteBalanceSwitchCheckValue, binding.linearLayout)
        updateSwitchState(binding.btnSwitchFocus, "초점도", focusSwitchCheckValue, binding.linearLayout2)
        updateSwitchState(binding.btnSwitchResolution, "해상도", resolutionSwitchCheckValue, binding.linearLayout3)
    }

    private fun updateSwitchState(switchButton: Switch, label: String, isChecked: Boolean, linearLayout: LinearLayout) {
        switchButton.apply {
            text = "$label ${if (isChecked) "자동" else "수동"}"
            this.isChecked = isChecked
            linearLayout.isVisible = !isChecked
        }
    }

    private fun setupSwitchButton(switchButton: Switch, buttonLabel: String, linearLayout: LinearLayout) {
        switchButton.setOnCheckedChangeListener { _, isChecked ->
            switchButton.text = "$buttonLabel ${if (isChecked) "자동" else "수동"}"
            linearLayout.isVisible = !isChecked
            switchCheckValues[buttonLabel]?.invoke(isChecked)
        }
    }

    private fun setupResolutionMenu() {
        binding.tvAspectRatio.setOnClickListener {
            val popupMenu = PopupMenu(this, binding.tvAspectRatio).apply {
                menu.add(Menu.NONE, 1, Menu.NONE, "3:4 비율")
                menu.add(Menu.NONE, 2, Menu.NONE, "9:16 비율")
                menu.add(Menu.NONE, 3, Menu.NONE, "1:1 비율")
                menu.add(Menu.NONE, 4, Menu.NONE, "전체 화면")
            }

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> updateResolution("3:4")
                    2 -> updateResolution("9:16")
                    3 -> updateResolution("1:1")
                    4 -> updateResolution("FullScreen")
                }
                true
            }
            popupMenu.show()
        }
    }

    private fun updateResolution(newResolution: String) {
        resolution = newResolution
        binding.tvAspectRatio.text = resolution
    }

    private fun setupSeekBarListeners() {
        val seekBars = listOf(binding.redSeekBar, binding.greenSeekBar, binding.blueSeekBar)

        seekBars.forEach { seekBar ->
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    updateColorPreview()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        binding.focusSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.focusSeekBar.progress = seekBar!!.progress
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateColorPreview() {
        val color = Color.rgb(binding.redSeekBar.progress, binding.greenSeekBar.progress, binding.blueSeekBar.progress)
        binding.colorPreviewText.setBackgroundColor(color)
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

    private fun showSaveToast() {
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }
}