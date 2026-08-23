package com.example.util

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * High-reliability DNS resolver with automatic fallback for Telegram API and cloud CDNs.
 * Prevents "Unable to resolve host api.telegram.org: No address associated with hostname"
 * on cellular mobile data (CGNAT), IPv6 DNS misconfigurations, or ISP DNS blockages.
 */
object TelegramDns : Dns {

    private val officialTelegramIps = listOf(
        "149.154.167.220",
        "149.154.167.99",
        "91.108.56.172",
        "91.108.56.165",
        "149.154.175.50"
    ).mapNotNull {
        try {
            InetAddress.getByName(it)
        } catch (_: Exception) {
            null
        }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        // 1. Try standard system DNS lookup first
        try {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isNotEmpty()) {
                return addresses
            }
        } catch (e: Exception) {
            // System DNS failed, fallback below
        }

        // 2. If looking up Telegram API, use known verified Telegram CDN IPs
        if (hostname.equals("api.telegram.org", ignoreCase = true) ||
            hostname.endsWith(".telegram.org", ignoreCase = true) ||
            hostname.endsWith(".t.me", ignoreCase = true)
        ) {
            if (officialTelegramIps.isNotEmpty()) {
                return officialTelegramIps
            }
        }

        // 3. Fallback retry
        try {
            return listOf(InetAddress.getByName(hostname))
        } catch (e: Exception) {
            throw UnknownHostException("Failed to resolve hostname '$hostname': ${e.message}")
        }
    }
}
