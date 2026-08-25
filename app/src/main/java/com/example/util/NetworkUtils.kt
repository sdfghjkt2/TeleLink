package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.example.data.model.FileCategory
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NetworkUtils {

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val addresses = intf.inetAddresses ?: continue
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (!host.isNullOrBlank() && !host.startsWith("127.")) return host
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    fun getAllIpAddresses(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return listOf("Localhost" to "127.0.0.1")
            for (intf in interfaces) {
                if (!intf.isUp) continue
                val addresses = intf.inetAddresses ?: continue
                for (addr in addresses) {
                    if (addr is Inet4Address) {
                        val name = intf.displayName.ifBlank { intf.name }
                        val ip = addr.hostAddress ?: ""
                        if (ip.isNotBlank()) {
                            result.add(name to ip)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        if (result.isEmpty()) {
            result.add("Localhost" to "127.0.0.1")
        }
        return result
    }

    fun getDeviceIpAddress(context: Context): String {
        try {
            // Try Wi-Fi Manager first
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null && wifiManager.isWifiEnabled) {
                val wifiInfo = wifiManager.connectionInfo
                val ipInt = wifiInfo.ipAddress
                if (ipInt != 0) {
                    val ip = String.format(
                        Locale.US,
                        "%d.%d.%d.%d",
                        ipInt and 0xff,
                        ipInt shr 8 and 0xff,
                        ipInt shr 16 and 0xff,
                        ipInt shr 24 and 0xff
                    )
                    if (ip != "0.0.0.0") return ip
                }
            }

            // Fallback to Network Interfaces (for hotspots, ethernet, etc.)
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddress = addr.hostAddress
                        if (hostAddress != null && !hostAddress.startsWith("127.")) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // ignore
        }
        return "127.0.0.1"
    }

    fun getNetworkTypeName(context: Context): String {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork ?: return "Offline"
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return "Disconnected"
            return when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi LAN"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular Mobile Data"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet LAN"
                else -> "Local Network"
            }
        } catch (_: Exception) {
            return "Localhost"
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val df = DecimalFormat("#,##0.#")
        val groupIndex = digitGroups.coerceIn(0, units.size - 1)
        return "${df.format(bytes / Math.pow(1024.0, groupIndex.toDouble()))} ${units[groupIndex]}"
    }

    fun formatSpeed(bytesPerSec: Long): String {
        return "${formatBytes(bytesPerSec)}/s"
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatUptime(seconds: Long): String {
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    fun detectCategory(fileName: String, mimeType: String): FileCategory {
        val lowerName = fileName.lowercase(Locale.ROOT)
        val lowerMime = mimeType.lowercase(Locale.ROOT)

        return when {
            lowerMime.startsWith("video/") || lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") ||
                    lowerName.endsWith(".avi") || lowerName.endsWith(".webm") || lowerName.endsWith(".mov") -> FileCategory.VIDEO

            lowerMime.startsWith("audio/") || lowerName.endsWith(".mp3") || lowerName.endsWith(".m4a") ||
                    lowerName.endsWith(".flac") || lowerName.endsWith(".wav") || lowerName.endsWith(".ogg") || lowerName.endsWith(".opus") -> FileCategory.AUDIO

            lowerMime.startsWith("image/") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                    lowerName.endsWith(".png") || lowerName.endsWith(".webp") || lowerName.endsWith(".gif") -> FileCategory.IMAGE

            lowerName.endsWith(".zip") || lowerName.endsWith(".rar") || lowerName.endsWith(".7z") ||
                    lowerName.endsWith(".tar") || lowerName.endsWith(".gz") -> FileCategory.ARCHIVE

            lowerMime.startsWith("text/") || lowerMime.contains("pdf") || lowerName.endsWith(".pdf") ||
                    lowerName.endsWith(".doc") || lowerName.endsWith(".docx") || lowerName.endsWith(".txt") ||
                    lowerName.endsWith(".epub") || lowerName.endsWith(".apk") -> FileCategory.DOCUMENT

            else -> FileCategory.OTHER
        }
    }
}
