package com.nile.duduapiexplorer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object ToolkitProbe {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "DUDU-FYT-Binder-Probe") }

    fun probeModules(context: Context) {
        DiscoveryStore.section("FYT TOOLKIT + MODULE PROBE (READ-ONLY)")
        withToolkit(context.applicationContext) { result ->
            result.onSuccess { toolkit ->
                val descriptor = safeDescriptor(toolkit)
                DiscoveryStore.add("Toolkit connected: alive=${toolkit.isBinderAlive} ping=${toolkit.pingBinder()} descriptor=$descriptor")
                var live = 0
                KnownTargets.modules.forEach { (code, name) ->
                    try {
                        val module = getRemoteModule(toolkit, code)
                        if (module == null) {
                            DiscoveryStore.addRaw("MODULE $code $name => null")
                        } else {
                            live++
                            DiscoveryStore.addRaw("MODULE $code $name => alive=${module.isBinderAlive} descriptor=${safeDescriptor(module)}")
                        }
                    } catch (t: Throwable) {
                        DiscoveryStore.addRaw("MODULE $code $name => ERROR ${shortError(t)}")
                    }
                }
                DiscoveryStore.add("Module summary: $live/${KnownTargets.modules.size} returned Binder objects")
                DiscoveryStore.add("No cmd/write transaction was sent.")
            }.onFailure {
                DiscoveryStore.add("Toolkit probe failed: ${shortError(it)}")
            }
        }
    }

    fun scanGetCodes(context: Context, moduleCode: Int, startCode: Int, endCode: Int) {
        val safeStart = startCode.coerceAtLeast(0)
        val safeEnd = endCode.coerceAtLeast(safeStart).coerceAtMost(safeStart + 255)
        val moduleName = KnownTargets.modules[moduleCode] ?: "UNKNOWN"
        DiscoveryStore.section("FYT GET PROBE module=$moduleCode/$moduleName codes=$safeStart..$safeEnd")
        DiscoveryStore.add("This probe uses only IRemoteModule.get transaction ${KnownTargets.TX_MODULE_GET}; no cmd transaction exists in this app.")

        withToolkit(context.applicationContext) { result ->
            result.onSuccess { toolkit ->
                val module = try { getRemoteModule(toolkit, moduleCode) } catch (t: Throwable) {
                    DiscoveryStore.add("getRemoteModule failed: ${shortError(t)}")
                    return@onSuccess
                }
                if (module == null) {
                    DiscoveryStore.add("Module $moduleCode returned null")
                    return@onSuccess
                }

                var values = 0
                var nulls = 0
                var errors = 0
                for (code in safeStart..safeEnd) {
                    try {
                        val value = moduleGet(module, code)
                        if (value == null) {
                            nulls++
                        } else {
                            values++
                            DiscoveryStore.addRaw("GET[$code] ints=${fmt(value.ints)} flts=${fmt(value.flts)} strs=${fmt(value.strs)}")
                        }
                    } catch (t: Throwable) {
                        errors++
                        DiscoveryStore.addRaw("GET[$code] ERROR ${shortError(t)}")
                    }
                    if ((code - safeStart) % 32 == 31) Thread.sleep(5)
                }
                DiscoveryStore.add("GET summary: values=$values null=$nulls errors=$errors")
            }.onFailure {
                DiscoveryStore.add("GET probe failed: ${shortError(it)}")
            }
        }
    }

    private fun withToolkit(context: Context, callback: (Result<IBinder>) -> Unit) {
        val completed = AtomicBoolean(false)
        val intent = Intent(KnownTargets.TOOLKIT_ACTION).apply {
            component = ComponentName(KnownTargets.TOOLKIT_PACKAGE, KnownTargets.TOOLKIT_SERVICE)
        }

        lateinit var connection: ServiceConnection
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (service == null) {
                    finish(Result.failure(IllegalStateException("Toolkit service returned null Binder")))
                    return
                }
                executor.execute { finish(Result.success(service)) }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (!completed.get()) finish(Result.failure(IllegalStateException("Toolkit disconnected before probe")))
            }

            override fun onNullBinding(name: ComponentName?) {
                finish(Result.failure(IllegalStateException("Toolkit returned null binding")))
            }

            private fun finish(result: Result<IBinder>) {
                if (!completed.compareAndSet(false, true)) return
                try {
                    callback(result)
                } finally {
                    runCatching { context.unbindService(connection) }
                }
            }
        }

        try {
            val ok = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!ok && completed.compareAndSet(false, true)) {
                callback(Result.failure(IllegalStateException("bindService returned false for ${intent.component}")))
            }
        } catch (t: Throwable) {
            if (completed.compareAndSet(false, true)) callback(Result.failure(t))
        }
    }

    private fun getRemoteModule(toolkit: IBinder, moduleCode: Int): IBinder? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(KnownTargets.TOOLKIT_DESCRIPTOR)
            data.writeInt(moduleCode)
            val handled = toolkit.transact(KnownTargets.TX_TOOLKIT_GET_REMOTE_MODULE, data, reply, 0)
            if (!handled) error("Toolkit transaction not handled")
            reply.readException()
            reply.readStrongBinder()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private data class ModuleValue(
        val ints: IntArray?,
        val flts: FloatArray?,
        val strs: Array<String>?
    )

    private fun moduleGet(module: IBinder, getCode: Int): ModuleValue? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(KnownTargets.MODULE_DESCRIPTOR)
            data.writeInt(getCode)
            data.writeIntArray(null)
            data.writeFloatArray(null)
            data.writeStringArray(null)
            val handled = module.transact(KnownTargets.TX_MODULE_GET, data, reply, 0)
            if (!handled) error("Module GET transaction not handled")
            reply.readException()
            if (reply.readInt() == 0) null else ModuleValue(
                ints = reply.createIntArray(),
                flts = reply.createFloatArray(),
                strs = reply.createStringArray()
            )
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun safeDescriptor(binder: IBinder): String =
        runCatching { binder.interfaceDescriptor ?: "<null>" }
            .getOrElse { "<error:${it.javaClass.simpleName}>" }

    private fun shortError(t: Throwable): String = "${t.javaClass.simpleName}: ${t.message.orEmpty().replace('\n', ' ')}"

    private fun fmt(v: IntArray?): String = v?.take(32)?.joinToString(prefix = "[", postfix = if ((v.size) > 32) ", ...]" else "]") ?: "null"
    private fun fmt(v: FloatArray?): String = v?.take(32)?.joinToString(prefix = "[", postfix = if ((v.size) > 32) ", ...]" else "]") ?: "null"
    private fun fmt(v: Array<String>?): String = v?.take(32)?.joinToString(prefix = "[", postfix = if ((v.size) > 32) ", ...]" else "]") ?: "null"
}
