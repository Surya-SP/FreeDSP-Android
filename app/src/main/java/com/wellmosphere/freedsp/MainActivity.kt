package com.wellmosphere.freedsp

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wellmosphere.freedsp.store.CurveStore
import com.wellmosphere.freedsp.ui.EqScreen
import com.wellmosphere.freedsp.ui.theme.FreeDspTheme

class MainActivity : ComponentActivity() {
    private val vm: EqViewModel by viewModels()

    private val usbEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) vm.detached() else vm.refresh()
        }
    }

    /** What the file picker is fetching: an EQ (null), or a measurement or target curve; or a profiles backup. */
    private var importing: CurveStore.Kind? = null
    private var restoring = false
    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val name = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        runCatching { contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }
            .onSuccess { text -> if (restoring) vm.restore(text) else importing?.let { vm.importCurve(it, text, name) } ?: vm.import(text, name) }
            .onFailure { vm.say("Couldn't open that file", true) }
    }
    private val saveFile = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri ?: return@registerForActivityResult
        runCatching { contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(vm.exportText()) } }
            .onSuccess { vm.say("Exported.") }.onFailure { vm.say("Couldn't save that file", true) }
    }
    private val saveBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@registerForActivityResult
        runCatching { contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(vm.backupText()) } }
            .onSuccess { vm.say("Backed up ${vm.state.value.profiles.size} profiles.") }.onFailure { vm.say("Couldn't save that file", true) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val filter = IntentFilter(PERMISSION).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(this, usbEvents, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent {
            FreeDspTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                EqScreen(state, vm, onAllowAccess = ::askForAccess,
                    onImport = { kind -> importing = kind; restoring = false; openFile.launch(arrayOf("text/*", "application/octet-stream")) },
                    onExport = { saveFile.launch(vm.exportName) },
                    onBackup = { saveBackup.launch(vm.backupName) },
                    onRestore = { restoring = true; openFile.launch(arrayOf("application/json", "text/*", "application/octet-stream")) })
            }
        }
    }

    override fun onResume() { super.onResume(); vm.refresh() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); vm.refresh() }
    override fun onDestroy() { unregisterReceiver(usbEvents); super.onDestroy() }

    private fun askForAccess() {
        val device = vm.device ?: return
        // Explicit and mutable so Android can fill in the result extras.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val intent = PendingIntent.getBroadcast(this, 0, Intent(PERMISSION).setPackage(packageName), flags)
        getSystemService(UsbManager::class.java).requestPermission(device, intent)
    }

    private companion object { const val PERMISSION = "com.wellmosphere.freedsp.USB_PERMISSION" }
}
