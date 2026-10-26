package com.example.floatingoverlay

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * MainActivity:
 * 1. Automatically verifies `android.permission.SYSTEM_ALERT_WINDOW` on launch.
 * 2. Prompts the user to grant overlay permission if not yet granted.
 * 3. Immediately starts `FloatingMenuService` and finishes `MainActivity` so only
 *    the floating overlay window remains on screen.
 */
class MainActivity : AppCompatActivity() {

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            startFloatingOverlayAndClose()
        } else {
            Toast.makeText(
                this,
                "Cần cấp quyền 'Hiển thị trên ứng dụng khác' (SYSTEM_ALERT_WINDOW) để mở Menu nổi!",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Settings.canDrawOverlays(this)) {
            startFloatingOverlayAndClose()
        } else {
            requestOverlayPermission()
        }
    }

    private fun requestOverlayPermission() {
        Toast.makeText(
            this,
            "Vui lòng cấp quyền 'Hiển thị trên ứng dụng khác' cho Game Assistant.",
            Toast.LENGTH_LONG
        ).show()

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun startFloatingOverlayAndClose() {
        val serviceIntent = Intent(this, FloatingMenuService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        // Close MainActivity immediately so only the floating window remains visible
        finish()
    }
}
