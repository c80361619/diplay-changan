package com.shilapi.xcertplay.network

import java.io.File

/**
 * 找出占用指定 UDP 端口的进程，让绑定失败在诊断报告中可直接归因。
 *
 * 先解析 /proc/net/udp(6) 得到该端口 socket 的 inode，再扫描 /proc/<pid>/fd 链接把 inode
 * 映射回持有它的进程。仅在暴露 /proc 的平台（Android/Linux）上有效；其他平台返回 null。
 */
object UdpPortOwnerProbe {

    fun describe(port: Int): String? {
        val inodes = socketInodes(port) ?: return null
        if (inodes.isEmpty()) return null
        return inodes.joinToString(", ") { inode ->
            "inode=$inode holder=${processFor(inode) ?: "unknown"}"
        }
    }

    /** Returns null when /proc is unavailable; an empty list when the port is free. */
    private fun socketInodes(port: Int): List<String>? {
        val hexPort = "%04X".format(port)
        val inodes = mutableListOf<String>()
        for (table in listOf("/proc/net/udp", "/proc/net/udp6")) {
            val lines = runCatching { File(table).readLines() }.getOrNull() ?: continue
            for (line in lines.drop(1)) {
                val fields = line.trim().split(WHITESPACE)
                val local = fields.getOrNull(1)?.split(":") ?: continue
                if (local.lastOrNull()?.equals(hexPort, ignoreCase = true) == true) {
                    fields.getOrNull(9)?.let(inodes::add)
                }
            }
        }
        return inodes
    }

    private fun processFor(inode: String): String? {
        val procRoot = File("/proc")
        val dirs = procRoot.listFiles { file -> file.name.all { it.isDigit() } } ?: return null
        for (dir in dirs) {
            val fdDir = File(dir, "fd")
            val links = fdDir.listFiles() ?: continue
            for (link in links) {
                // /proc/<pid>/fd/N 指向 "socket:[inode]"；canonicalPath 是 KitKat 上无 NIO 时
                // 读取符号链接目标的唯一途径（目标不存在时原样返回链接文本）。
                val target = runCatching { link.canonicalPath }.getOrNull() ?: continue
                if (target == "socket:[$inode]") {
                    val cmdline = runCatching {
                        File(dir, "cmdline").readBytes().toString(Charsets.UTF_8)
                            .split('\u0000').firstOrNull { it.isNotBlank() }
                    }.getOrNull()
                    return cmdline?.takeIf { it.isNotBlank() } ?: "pid=${dir.name}"
                }
            }
        }
        return null
    }

    private val WHITESPACE = Regex("\\s+")
}
