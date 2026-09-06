package com.nile.duduapiexplorer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel

object FytToolkitProbe {
    private const val TOOLKIT_DESCRIPTOR = "com.syu.ipc.IRemoteToolkit"
    private const val TRANSACTION_GET_REMOTE_MODULE = 1

    fun probe(context: Context, onDone: () -> Unit) {
        DiscoveryStore.addSection("FYT TOOLKIT READ-ONLY PROBE")
        val app = context.applicationContext
        val intent = Intent("com.syu.ms.toolkit").setPackage("com.syu.ms")
        val resolved = runCatching { app.packageManager.queryIntentServices(intent, 0) }.getOrDefault(emptyList())
        resolved.forEach {
            DiscoveryStore.add("RESOLVED service=${it.serviceInfo?.packageName}/${it.serviceInfo?.name} exported=${it.serviceInfo?.exported} permission=${it.serviceInfo?.permission.orEmpty()}")
        }

        lateinit var connection: ServiceConnection
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                try {
                    DiscoveryStore.add("CONNECTED $name")
                    DiscoveryStore.add("toolkit.alive=${service.isBinderAlive} ping=${service.pingBinder()}")
                    DiscoveryStore.add("toolkit.descriptor=${runCatching { service.interfaceDescriptor }.getOrNull()}")
                    KnownTargets.moduleNames.forEach { (code, label) ->
                        val binder = getRemoteModuleBinder(service, code)
                        if (binder == null) {
                            DiscoveryStore.add("MODULE $code $label = null")
                        } else {
                            val descriptor = runCatching { binder.interfaceDescriptor }.getOrNull()
                            DiscoveryStore.add("MODULE $code $label alive=${binder.isBinderAlive} descriptor=${descriptor.orEmpty()}")
                        }
                    }
                } catch (t: Throwable) {
                    DiscoveryStore.add("TOOLKIT_ERROR ${t.javaClass.name}: ${t.message}")
                } finally {
                    runCatching { app.unbindService(connection) }
                    onDone()
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                DiscoveryStore.add("DISCONNECTED $name")
            }
        }

        val bound = try {
            app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (t: Throwable) {
            DiscoveryStore.add("BIND_ERROR ${t.javaClass.name}: ${t.message}")
            false
        }
        DiscoveryStore.add("bindService=$bound")
        if (!bound) onDone()
    }

    private fun getRemoteModuleBinder(toolkit: IBinder, moduleCode: Int): IBinder? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(TOOLKIT_DESCRIPTOR)
            data.writeInt(moduleCode)
            val ok = toolkit.transact(TRANSACTION_GET_REMOTE_MODULE, data, reply, 0)
            if (!ok) return null
            reply.readException()
            reply.readStrongBinder()
        } catch (_: Throwable) {
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}
