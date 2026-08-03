package com.tharunbirla.librecuts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tharunbirla.librecuts.R

class ErrorDisplayActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val errorCode = intent.getStringExtra("ERROR_CODE") ?: "LC-500"
        val errorLog = intent.getStringExtra("ERROR_LOG") ?: "No log available"

        showCrashDialog(errorCode, errorLog)
    }

    private fun showCrashDialog(code: String, log: String) {
        val appVersion = try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            "${pInfo.versionName} (${pInfo.versionCode})"
        } catch (e: Exception) {
            "1.0-beta"
        }

        val fullDiagnosticLog = """
            ==================================================
                        LIBRECUTS CRASH REPORT               
            ==================================================
            App Version : $appVersion
            Device      : ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE}, API ${android.os.Build.VERSION.SDK_INT})
            Error Code  : $code
            
            --------------------------------------------------
            STACK TRACE:
            --------------------------------------------------
            $log
            ==================================================
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle("Unexpected Error")
            .setMessage("The app encountered a critical issue ($code). Would you like to report it?")
            .setCancelable(false)
            .setNeutralButton("Copy Log") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Crash Log", fullDiagnosticLog))
                Toast.makeText(this, R.string.toast_log_copied, Toast.LENGTH_SHORT).show()
                showCrashDialog(code, log)
            }
            .setPositiveButton("GitHub Report") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Crash Log", fullDiagnosticLog))

                val issueTitle = Uri.encode("[Crash Report] $code: Application Crash")
                val logSnippet = if (fullDiagnosticLog.length <= 3500) fullDiagnosticLog else fullDiagnosticLog.take(3500) + "\n... (Full log copied to clipboard)"
                val issueBody = Uri.encode(
                    "## Application Crash Report ($code)\n\n" +
                    "### Technical Stack Trace\n```\n" +
                    logSnippet +
                    "\n```\n\n" +
                    "*Note: The complete stack trace has been copied to your clipboard.*"
                )
                val url = "https://github.com/tharunbirla/librecuts/issues/new?title=$issueTitle&body=$issueBody"
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    Toast.makeText(this, "Full crash log copied to clipboard!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open browser. Log copied to clipboard.", Toast.LENGTH_LONG).show()
                }
                finish()
            }
            .setNegativeButton("Restart App") { _, _ ->
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .show()
    }
}