package com.rockyhelper

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import android.view.View
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private lateinit var btnToggleFloat: MaterialButton
    private lateinit var tvPermissionHint: TextView
    private lateinit var btnGoSettings: MaterialButton
    private lateinit var statusDot: View
    private lateinit var statusText: TextView

    private var isServiceRunning = false

    // 通知权限请求
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startFloatService()
        } else {
            Toast.makeText(this, "需要通知权限来保持服务运行", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggleFloat = findViewById(R.id.btnToggleFloat)
        tvPermissionHint = findViewById(R.id.tvPermissionHint)
        btnGoSettings = findViewById(R.id.btnGoSettings)
        statusDot = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)

        btnToggleFloat.setOnClickListener { onToggleClick() }
        btnGoSettings.setOnClickListener { openFloatPermissionSettings() }

        // 检查服务状态
        updateServiceStatus()

        // 检查通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                // 延迟请求，不要在启动时立即弹出
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        checkFloatPermission()
    }

    private fun onToggleClick() {
        if (isServiceRunning) {
            stopFloatService()
        } else {
            checkAndStartService()
        }
    }

    private fun checkAndStartService() {
        // 1. 检查通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        // 2. 检查悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            requestFloatPermission()
            return
        }

        // 3. 权限都满足，启动服务
        startFloatService()
    }

    private fun startFloatService() {
        val intent = Intent(this, FloatService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateServiceStatus()
    }

    private fun stopFloatService() {
        val intent = Intent(this, FloatService::class.java)
        stopService(intent)
        isServiceRunning = false
        updateServiceStatus()
    }

    private fun requestFloatPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${packageName}")
        )
        // 检查是否能 resolve（某些设备可能没有这个设置页）
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, REQUEST_CODE_FLOAT_PERMISSION)
        } else {
            openFloatPermissionSettings()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_FLOAT_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                // 权限已获取，继续启动
                checkAndStartService()
            } else {
                // 权限被拒绝
                checkFloatPermission()
            }
        }
    }

    private fun openFloatPermissionSettings() {
        try {
            // 尝试打开应用详情页
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${packageName}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开设置页面，请手动在设置中授权", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkFloatPermission() {
        if (Settings.canDrawOverlays(this)) {
            tvPermissionHint.visibility = View.GONE
            btnGoSettings.visibility = View.GONE
        } else {
            tvPermissionHint.visibility = View.VISIBLE
            btnGoSettings.visibility = View.VISIBLE
        }
    }

    private fun updateServiceStatus() {
        isServiceRunning = FloatService.isRunning
        if (isServiceRunning) {
            statusDot.setBackgroundResource(R.color.success)
            statusText.text = "悬浮窗服务运行中"
            btnToggleFloat.text = getString(R.string.stop_float)
            btnToggleFloat.icon?.setTint(getColor(R.color.white))
        } else {
            statusDot.setBackgroundResource(R.color.danger)
            statusText.text = "悬浮窗服务未运行"
            btnToggleFloat.text = getString(R.string.start_float)
        }
    }

    companion object {
        private const val REQUEST_CODE_FLOAT_PERMISSION = 1001

        /** 静态方法：检查并启动悬浮窗服务（可用于从其他地方调用） */
        @JvmStatic
        fun startFloatServiceIfNeeded(context: Context) {
            if (!FloatService.isRunning && Settings.canDrawOverlays(context)) {
                val intent = Intent(context, FloatService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }
}
