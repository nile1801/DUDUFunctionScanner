package com.nile.dudufunctionscanner

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import java.util.concurrent.atomic.AtomicBoolean

object FytMonitor {
    private const val TOOLKIT_ACTION = "com.syu.ms.toolkit"
    private const val TOOLKIT_PACKAGE = "com.syu.ms"
    private const val TOOLKIT_CLASS = "app.ToolkitService"
    private const val TOOLKIT_DESCRIPTOR = "com.syu.ipc.IRemoteToolkit"
    private const val MODULE_DESCRIPTOR = "com.syu.ipc.IRemoteModule"
    private const val CALLBACK_DESCRIPTOR = "com.syu.ipc.IModuleCallback"

    private const val TRANS_GET_REMOTE_MODULE = 1
    private const val TRANS_REGISTER = 3
    private const val TRANS_CALLBACK_UPDATE = 1

    private const val MODULE_MAIN = 0
    private const val MODULE_BT = 2
    private const val MODULE_CANBUS = 7

    private val binding = AtomicBoolean(false)
    @Volatile private var bound = false
    @Volatile private var appContext: Context? = null
    @Volatile private var toolkit: IBinder? = null

    private val callbacks = linkedMapOf(
        MODULE_MAIN to moduleCallback("MAIN"),
        MODULE_BT to moduleCallback("BT"),
        MODULE_CANBUS to moduleCallback("CANBUS")
    )

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            toolkit = service
            binding.set(false)
            bound = true
            ScanStore.add("FYT IPC: connected $name descriptor=${safeDescriptor(service)}")
            Thread({ subscribeAll() }, "DUDU-FYT-Subscribe").apply {
                isDaemon = true
                start()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            toolkit = null
            bound = false
            binding.set(false)
            ScanStore.add("FYT IPC: disconnected $name")
        }

        override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
        override fun onNullBinding(name: ComponentName) = onServiceDisconnected(name)
    }

    fun start(context: Context) {
        appContext = context.applicationContext
        if (bound || !binding.compareAndSet(false, true)) return
        try {
            val intent = Intent(TOOLKIT_ACTION).apply {
                component = ComponentName(TOOLKIT_PACKAGE, TOOLKIT_CLASS)
            }
            val ok = appContext?.bindService(intent, connection, Context.BIND_AUTO_CREATE) == true
            if (!ok) {
                binding.set(false)
                ScanStore.add("FYT IPC: bindService returned false")
            }
        } catch (t: Throwable) {
            binding.set(false)
            ScanStore.add("FYT IPC ERROR: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    fun stop() {
        val context = appContext
        if (bound && context != null) runCatching { context.unbindService(connection) }
        toolkit = null
        bound = false
        binding.set(false)
    }

    private fun subscribeAll() {
        val main = getRemoteModule(MODULE_MAIN)
        val bt = getRemoteModule(MODULE_BT)
        val can = getRemoteModule(MODULE_CANBUS)

        val mainIndexes = ArrayList<Int>().apply {
            addAll(0..76)
            addAll(78..200)
        }
        val btIndexes = (0..100).toList()
        val canIndexes = ArrayList<Int>().apply {
            addAll(0..200)
            addAll(500..600)
            addAll(1000..1200)
        }

        subscribeModule("MAIN", main, callbacks.getValue(MODULE_MAIN), mainIndexes)
        subscribeModule("BT", bt, callbacks.getValue(MODULE_BT), btIndexes)
        subscribeModule("CANBUS", can, callbacks.getValue(MODULE_CANBUS), canIndexes)
    }

    private fun subscribeModule(name: String, module: IBinder?, callback: IBinder, indexes: List<Int>) {
        if (module == null) {
            ScanStore.add("FYT $name: module unavailable")
            return
        }
        var success = 0
        var failed = 0
        for (index in indexes) {
            if (!bound || toolkit == null) break
            if (register(module, callback, index, 1)) success++ else failed++
        }
        ScanStore.add("FYT $name: subscribed=$success failed=$failed descriptor=${safeDescriptor(module)}")
    }

    private fun getRemoteModule(code: Int): IBinder? {
        val tk = toolkit ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(TOOLKIT_DESCRIPTOR)
            data.writeInt(code)
            tk.transact(TRANS_GET_REMOTE_MODULE, data, reply, 0)
            reply.readException()
            reply.readStrongBinder()
        } catch (t: Throwable) {
            ScanStore.add("FYT module $code ERROR: ${t.javaClass.simpleName}: ${t.message}")
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun register(module: IBinder, callback: IBinder, updateId: Int, p: Int): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(MODULE_DESCRIPTOR)
            data.writeStrongBinder(callback)
            data.writeInt(updateId)
            data.writeInt(p)
            module.transact(TRANS_REGISTER, data, reply, 0)
            reply.readException()
            true
        } catch (_: Throwable) {
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun moduleCallback(moduleName: String): IBinder = object : Binder() {
        init {
            attachInterface(null, CALLBACK_DESCRIPTOR)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == TRANS_CALLBACK_UPDATE) {
                return try {
                    data.enforceInterface(CALLBACK_DESCRIPTOR)
                    val updateId = data.readInt()
                    val ints = data.createIntArray()
                    val floats = data.createFloatArray()
                    val strings = data.createStringArray()
                    ScanStore.recordFyt(moduleName, updateId, ints, floats, strings)
                    reply?.writeNoException()
                    true
                } catch (_: Throwable) {
                    false
                }
            }
            return super.onTransact(code, data, reply, flags)
        }
    }

    fun probeModules(): List<String> {
        val names = linkedMapOf(
            0 to "MAIN", 1 to "RADIO", 2 to "BT", 3 to "DVD", 4 to "SOUND", 5 to "IPOD",
            6 to "TV", 7 to "CANBUS", 8 to "TPMS", 9 to "DVR", 10 to "STEER", 11 to "CUSTOMER",
            12 to "OBD", 13 to "TEST", 14 to "CAN_UP", 15 to "AMP", 16 to "EMITTER",
            17 to "GSENSOR", 18 to "GESTURE", 19 to "SENSOR", 20 to "ADAS"
        )
        return names.mapNotNull { (code, name) ->
            val mod = getRemoteModule(code) ?: return@mapNotNull null
            "$code/$name -> ${safeDescriptor(mod)}"
        }
    }

    private fun safeDescriptor(binder: IBinder): String = runCatching {
        binder.interfaceDescriptor ?: "?"
    }.getOrDefault("?")
}
