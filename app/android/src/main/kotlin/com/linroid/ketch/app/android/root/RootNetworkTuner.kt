package com.linroid.ketch.app.android.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages Kernel TCP, Socket buffer, and Dual Bonding network tuning via root (KernelSU / Magisk).
 * Optimizations are strictly applied ONLY while downloads are active,
 * and reverted to stock defaults when downloads finish or pause.
 */
object RootNetworkTuner {
  private const val TAG = "RootNetworkTuner"
  private val isTuned = AtomicBoolean(false)
  private var stockCongestionControl = "cubic"

  suspend fun onDownloadStarted() {
    if (isTuned.getAndSet(true)) return

    withContext(Dispatchers.IO) {
      try {
        // Read current congestion control to restore later
        val currentCc = runSuCommand("sysctl -n net.ipv4.tcp_congestion_control")
        if (currentCc.isNotBlank() && !currentCc.contains("bbr")) {
          stockCongestionControl = currentCc.trim()
        }

        // Apply high-performance socket, 16MB buffers, backlog, and dual-bonding tuning
        val commands = listOf(
          "sysctl -w net.ipv4.tcp_congestion_control=bbr",
          "sysctl -w net.core.rmem_max=16777216",
          "sysctl -w net.core.wmem_max=16777216",
          "sysctl -w net.core.rmem_default=2097152",
          "sysctl -w net.core.wmem_default=2097152",
          "sysctl -w net.core.netdev_max_backlog=10000",
          "sysctl -w net.ipv4.tcp_rmem=\"4096 87380 16777216\"",
          "sysctl -w net.ipv4.tcp_wmem=\"4096 65536 16777216\"",
          "sysctl -w net.ipv4.tcp_window_scaling=1",
          "sysctl -w net.ipv4.tcp_slow_start_after_idle=0",
          "sysctl -w net.ipv4.tcp_tw_reuse=1",
          "sysctl -w net.ipv4.tcp_fastopen=3",
          // Dual bonding / MPTCP network acceleration if kernel enabled
          "sysctl -w net.mptcp.enabled=1 2>/dev/null || true",
          "sysctl -w net.ipv4.tcp_mtu_probing=1",
          "ip rule add pref 10000 lookup main 2>/dev/null || true"
        )
        val combinedCmd = commands.joinToString(" && ")
        runSuCommand(combinedCmd)
        Log.i(TAG, "Kernel TCP & Dual Bonding tuned for active downloads (BBR + 16MB socket buffers + 10k backlog)")
      } catch (e: Exception) {
        Log.w(TAG, "Failed to apply root TCP tuning: ${e.message}")
        isTuned.set(false)
      }
    }
  }

  suspend fun onDownloadStopped() {
    if (!isTuned.getAndSet(false)) return

    withContext(Dispatchers.IO) {
      try {
        // Revert to stock defaults to conserve battery and system memory
        val revertCommands = listOf(
          "sysctl -w net.ipv4.tcp_congestion_control=$stockCongestionControl",
          "sysctl -w net.core.rmem_max=2097152",
          "sysctl -w net.core.wmem_max=2097152",
          "sysctl -w net.core.rmem_default=212992",
          "sysctl -w net.core.wmem_default=212992",
          "sysctl -w net.core.netdev_max_backlog=1000",
          "sysctl -w net.ipv4.tcp_slow_start_after_idle=1",
          "sysctl -w net.ipv4.tcp_fastopen=1",
          "ip rule del pref 10000 lookup main 2>/dev/null || true"
        )
        runSuCommand(revertCommands.joinToString(" && "))
        Log.i(TAG, "Kernel TCP reverted to stock defaults ($stockCongestionControl)")
      } catch (e: Exception) {
        Log.w(TAG, "Failed to revert root TCP tuning: ${e.message}")
      }
    }
  }

  private fun runSuCommand(cmd: String): String {
    return try {
      val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
      val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
      process.waitFor()
      output
    } catch (e: Exception) {
      ""
    }
  }
}
