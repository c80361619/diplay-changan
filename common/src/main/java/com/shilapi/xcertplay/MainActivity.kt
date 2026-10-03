package com.shilapi.xcertplay

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.shilapi.xcertplay.mfi.MfiProtocolMajorResult
import com.shilapi.xcertplay.mfi.MfiSelfCheck
import com.shilapi.xcertplay.mfi.MfiSelfCheckResult
import com.shilapi.xcertplay.transport.LinuxI2cTransport
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Board I2C diagnostic, reimplemented with plain views for the KitKat port (no Compose). */
class MainActivity : ComponentActivity() {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var devicePathInput: EditText? = null
    private var statusLabel: TextView? = null
    private var checkButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dip(value: Int): Int = (value * density).toInt()

        val title = TextView(this).apply {
            text = "Board I2C diagnostic"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        }
        val devicePath = EditText(this).apply {
            setText("/dev/i2c-1")
            hint = "Linux I2C device"
            setSingleLine(true)
        }
        devicePathInput = devicePath
        val run = Button(this).apply {
            text = "Run MFi self-check"
        }
        checkButton = run
        val hint = TextView(this).apply {
            text = "CH341 requires deployment-specific VID/PID configuration."
            textSize = 12f
        }
        val status = TextView(this).apply {
            text = DiagnosticStatus.Idle.message()
            textSize = 14f
        }
        statusLabel = status

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dip(24), dip(24), dip(24), dip(24))
            gravity = Gravity.TOP
            addView(title, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(devicePath, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.topMargin = dip(12)
            })
            addView(run, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.topMargin = dip(12)
            })
            addView(status, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.topMargin = dip(16)
            })
            addView(hint, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.topMargin = dip(4)
            })
        }
        run.setOnClickListener { runSelfCheck(devicePath.text.toString()) }
        setContentView(root)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun setBusy(busy: Boolean) {
        checkButton?.isEnabled = !busy
        devicePathInput?.isEnabled = !busy
    }

    private fun runSelfCheck(devicePath: String) {
        setBusy(true)
        statusLabel?.text = DiagnosticStatus.Running.message()
        executor.execute {
            val next = try {
                LinuxI2cTransport.open(devicePath).use { MfiSelfCheck(it).run() }
                    .let { DiagnosticStatus.Result(it) }
            } catch (error: LinkageError) {
                DiagnosticStatus.Failure(error.message ?: "I2C native library is unavailable")
            } catch (error: Exception) {
                DiagnosticStatus.Failure(error.message ?: error.javaClass.simpleName)
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    setBusy(false)
                    statusLabel?.text = next.message()
                }
            }
        }
    }
}

private sealed class DiagnosticStatus {
    data object Idle : DiagnosticStatus()
    data object Running : DiagnosticStatus()
    data class Result(val selfCheck: MfiSelfCheckResult) : DiagnosticStatus()
    data class Failure(val message: String) : DiagnosticStatus()

    fun message(): String = when (this) {
        Idle -> "Idle"
        Running -> "Running…"
        is Failure -> "Failed: $message"
        is Result -> {
            val chip = selfCheck.chip ?: return if (selfCheck.discovery.interrupted) {
                "MFi scan interrupted"
            } else {
                "Found: none"
            }
            val major = when (val result = chip.protocolMajor) {
                is MfiProtocolMajorResult.Value -> "%d".format(result.major)
                is MfiProtocolMajorResult.MfiFailure -> result.error.message ?: result.error.javaClass.simpleName
                is MfiProtocolMajorResult.TransportFailure -> result.error.message ?: result.error.javaClass.simpleName
            }
            "Found: 0x%02X; device version: 0x%02X; protocol major (raw): %s".format(
                chip.address7Bit,
                chip.deviceVersion,
                major,
            )
        }
    }
}
