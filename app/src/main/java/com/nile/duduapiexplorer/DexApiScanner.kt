package com.nile.duduapiexplorer

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipFile

object DexApiScanner {
    private val dexName = Regex("classes(\\d*)?\\.dex")

    fun scanAsync(context: Context, packageName: String) {
        val app = context.applicationContext
        Thread({ scan(app, packageName.trim()) }, "DUDU-DEX-API-Scanner").start()
    }

    @Suppress("DEPRECATION")
    private fun scan(context: Context, packageName: String) {
        if (packageName.isBlank()) {
            DiscoveryStore.add("DEX scan: package name is empty")
            return
        }
        DiscoveryStore.section("DEX API SCAN: $packageName")
        val appInfo = try {
            context.packageManager.getApplicationInfo(packageName, 0)
        } catch (t: Throwable) {
            DiscoveryStore.add("Cannot resolve package: ${shortError(t)}")
            return
        }

        val apkPaths = buildList {
            appInfo.sourceDir?.let { add(it) }
            appInfo.splitSourceDirs?.forEach { add(it) }
        }.distinct()
        DiscoveryStore.add("APK parts=${apkPaths.size}; source=${appInfo.sourceDir.orEmpty()}")

        var totalDex = 0
        var totalMethods = 0
        val methods = linkedSetOf<String>()
        val strings = linkedSetOf<String>()

        apkPaths.forEach { path ->
            val apk = File(path)
            if (!apk.exists()) {
                DiscoveryStore.addRaw("APK missing: $path")
                return@forEach
            }
            try {
                ZipFile(apk).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!dexName.matches(entry.name)) continue
                        totalDex++
                        val temp = File.createTempFile("dudu-api-", ".dex", context.cacheDir)
                        try {
                            zip.getInputStream(entry).use { input ->
                                temp.outputStream().buffered().use { output -> input.copyTo(output, 256 * 1024) }
                            }
                            val result = DexReader(temp).use { it.scan() }
                            totalMethods += result.totalMethodIds
                            methods += result.interestingMethods
                            strings += result.interestingStrings
                            DiscoveryStore.addRaw("DEX ${apk.name}!${entry.name}: methodIds=${result.totalMethodIds} interestingMethods=${result.interestingMethods.size} interestingStrings=${result.interestingStrings.size}")
                        } finally {
                            temp.delete()
                        }
                    }
                }
            } catch (t: Throwable) {
                DiscoveryStore.addRaw("APK ERROR $path => ${shortError(t)}")
            }
        }

        DiscoveryStore.addRaw("\n--- METHOD / API CANDIDATES (${methods.size}) ---")
        methods.take(6000).forEach { DiscoveryStore.addRaw(it) }
        if (methods.size > 6000) DiscoveryStore.addRaw("... ${methods.size - 6000} more methods trimmed")

        DiscoveryStore.addRaw("\n--- ACTION / DESCRIPTOR / VENDOR STRINGS (${strings.size}) ---")
        strings.take(6000).forEach { DiscoveryStore.addRaw(it) }
        if (strings.size > 6000) DiscoveryStore.addRaw("... ${strings.size - 6000} more strings trimmed")

        DiscoveryStore.add("DEX summary: dexFiles=$totalDex totalMethodIds=$totalMethods candidates=${methods.size} strings=${strings.size}")
    }

    private data class DexScanResult(
        val totalMethodIds: Int,
        val interestingMethods: Set<String>,
        val interestingStrings: Set<String>
    )

    private class DexReader(file: File) : AutoCloseable {
        private val raf = RandomAccessFile(file, "r")
        private val stringCache = HashMap<Int, String>()
        private var stringIdsSize = 0
        private var stringIdsOff = 0L
        private var typeIdsSize = 0
        private var typeIdsOff = 0L
        private var protoIdsSize = 0
        private var protoIdsOff = 0L
        private var methodIdsSize = 0
        private var methodIdsOff = 0L

        fun scan(): DexScanResult {
            val magic = ByteArray(8)
            raf.seek(0)
            raf.readFully(magic)
            require(String(magic, Charsets.ISO_8859_1).startsWith("dex\n")) { "Not a DEX file" }

            stringIdsSize = intAt(0x38).coerceAtLeast(0)
            stringIdsOff = uintAt(0x3c)
            typeIdsSize = intAt(0x40).coerceAtLeast(0)
            typeIdsOff = uintAt(0x44)
            protoIdsSize = intAt(0x48).coerceAtLeast(0)
            protoIdsOff = uintAt(0x4c)
            methodIdsSize = intAt(0x58).coerceAtLeast(0)
            methodIdsOff = uintAt(0x5c)

            require(stringIdsSize < 2_000_000 && methodIdsSize < 2_000_000) { "DEX table size unreasonable" }

            val interestingStrings = linkedSetOf<String>()
            for (i in 0 until stringIdsSize) {
                val s = runCatching { stringAt(i) }.getOrNull() ?: continue
                if (looksInterestingString(s)) interestingStrings += normalizeString(s)
                if (interestingStrings.size >= 8000) break
            }

            val interestingMethods = linkedSetOf<String>()
            for (i in 0 until methodIdsSize) {
                val off = methodIdsOff + i * 8L
                val classIdx = ushortAt(off)
                val protoIdx = ushortAt(off + 2)
                val nameIdx = intAt(off + 4)
                val classDesc = descriptorForType(classIdx)
                if (!isVendorClass(classDesc)) continue
                val name = stringAt(nameIdx)
                if (!looksInterestingMethod(classDesc, name)) continue
                val proto = protoSignature(protoIdx)
                interestingMethods += "${prettyClass(classDesc)}#$name$proto"
                if (interestingMethods.size >= 8000) break
            }

            return DexScanResult(methodIdsSize, interestingMethods, interestingStrings)
        }

        private fun protoSignature(protoIdx: Int): String {
            if (protoIdx !in 0 until protoIdsSize) return "(?)"
            val off = protoIdsOff + protoIdx * 12L
            val returnTypeIdx = intAt(off + 4)
            val paramsOff = uintAt(off + 8)
            val params = ArrayList<String>()
            if (paramsOff != 0L && paramsOff + 4 <= raf.length()) {
                val size = intAt(paramsOff).coerceIn(0, 128)
                for (i in 0 until size) {
                    params += descriptorForType(ushortAt(paramsOff + 4 + i * 2L))
                }
            }
            return "(${params.joinToString(",") { prettyType(it) }}):${prettyType(descriptorForType(returnTypeIdx))}"
        }

        private fun descriptorForType(typeIdx: Int): String {
            if (typeIdx !in 0 until typeIdsSize) return "?"
            val stringIdx = intAt(typeIdsOff + typeIdx * 4L)
            return stringAt(stringIdx)
        }

        private fun stringAt(index: Int): String {
            if (index !in 0 until stringIdsSize) return "?"
            stringCache[index]?.let { return it }
            val dataOff = uintAt(stringIdsOff + index * 4L)
            if (dataOff <= 0 || dataOff >= raf.length()) return "?"
            raf.seek(dataOff)
            readUleb128()
            val out = ArrayList<Byte>(64)
            while (out.size < 16_384) {
                val b = raf.read()
                if (b <= 0) break
                out += b.toByte()
            }
            val bytes = ByteArray(out.size)
            for (i in out.indices) bytes[i] = out[i]
            val value = runCatching { String(bytes, Charsets.UTF_8) }.getOrDefault("")
            if (stringCache.size < 100_000) stringCache[index] = value
            return value
        }

        private fun readUleb128(): Int {
            var result = 0
            var shift = 0
            repeat(5) {
                val b = raf.read()
                if (b < 0) return result
                result = result or ((b and 0x7f) shl shift)
                if ((b and 0x80) == 0) return result
                shift += 7
            }
            return result
        }

        private fun intAt(offset: Long): Int {
            raf.seek(offset)
            val b0 = raf.read(); val b1 = raf.read(); val b2 = raf.read(); val b3 = raf.read()
            if (b3 < 0) throw java.io.EOFException()
            return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }

        private fun uintAt(offset: Long): Long = intAt(offset).toLong() and 0xffffffffL

        private fun ushortAt(offset: Long): Int {
            raf.seek(offset)
            val b0 = raf.read(); val b1 = raf.read()
            if (b1 < 0) throw java.io.EOFException()
            return b0 or (b1 shl 8)
        }

        override fun close() = raf.close()
    }

    private fun isVendorClass(desc: String): Boolean =
        desc.startsWith("Lcom/syu/") || desc.startsWith("Lcom/dudu/") || desc.startsWith("Lcom/fyt/")

    private fun looksInterestingMethod(classDesc: String, name: String): Boolean {
        val n = name.lowercase()
        val c = classDesc.lowercase()
        val methodKeys = listOf("get", "set", "cmd", "register", "send", "update", "bind", "connect", "air", "climate", "can", "vehicle", "remote", "module", "service", "broadcast", "intent", "open", "close", "start", "stop")
        val classKeys = listOf("ipc", "remote", "canbus", "/air/", "climate", "vehicle", "toolkit", "service")
        return methodKeys.any { it in n } || classKeys.any { it in c }
    }

    private fun looksInterestingString(s: String): Boolean {
        if (s.length !in 3..300) return false
        val lower = s.lowercase()
        return s.startsWith("com.syu.") ||
            s.startsWith("com.dudu.") ||
            s.startsWith("com.fyt.") ||
            s.startsWith("Lcom/syu/") ||
            s.startsWith("Lcom/dudu/") ||
            s.startsWith("Lcom/fyt/") ||
            "com.syu.ipc" in s ||
            (("canbus" in lower || "climate" in lower || "vehicle" in lower || "toolkit" in lower) && ('.' in s || '/' in s))
    }

    private fun normalizeString(s: String): String = s.replace('\n', ' ').replace('\r', ' ').trim()

    private fun prettyClass(desc: String): String = if (desc.startsWith("L") && desc.endsWith(";")) {
        desc.substring(1, desc.length - 1).replace('/', '.')
    } else desc

    private fun prettyType(desc: String): String {
        var d = desc
        var arrays = 0
        while (d.startsWith("[")) { arrays++; d = d.substring(1) }
        val base = when (d) {
            "V" -> "void"; "Z" -> "boolean"; "B" -> "byte"; "S" -> "short"; "C" -> "char"
            "I" -> "int"; "J" -> "long"; "F" -> "float"; "D" -> "double"
            else -> prettyClass(d)
        }
        return base + "[]".repeat(arrays)
    }

    private fun shortError(t: Throwable): String = "${t.javaClass.simpleName}: ${t.message.orEmpty().replace('\n', ' ')}"
}
