/*
 *     This file is part of UnifiedMetrics.
 *
 *     UnifiedMetrics is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package dev.cubxity.plugins.metrics.bukkit.metric.tick

import dev.cubxity.plugins.metrics.api.metric.collector.Collector
import dev.cubxity.plugins.metrics.api.metric.collector.NANOSECONDS_PER_SECOND
import dev.cubxity.plugins.metrics.api.metric.data.GaugeMetric
import dev.cubxity.plugins.metrics.api.metric.data.Metric
import dev.cubxity.plugins.metrics.common.metric.Metrics
import org.bukkit.Server
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Reads the server's current TPS and MSPT without depending on Paper classes.
 *
 * Paper exposes TPS through its API. Spigot and Bukkit expose the same values
 * on their underlying MinecraftServer, whose names vary between versions, so
 * those values are located by their array type as a final fallback.
 */
class BukkitTickCollector(private val server: Server) : Collector {
    private var paperTpsMethod: Method? = null
    private var minecraftServer: Any? = null
    private var tpsField: Field? = null
    private var tickTimesField: Field? = null
    private var initialized = false

    override fun collect(): List<Metric> = buildList {
        tps()?.takeIf(Double::isFinite)?.let { add(GaugeMetric(Metrics.Server.Tps, value = it)) }
        mspt()?.takeIf(Double::isFinite)?.let {
            add(GaugeMetric(Metrics.Server.TickDurationSeconds, value = it / NANOSECONDS_PER_SECOND))
        }
    }

    private fun tps(): Double? {
        prepare()
        val paperTps = runCatching { paperTpsMethod?.invoke(server) as? DoubleArray }.getOrNull()
        if (paperTps != null && paperTps.isNotEmpty()) return paperTps[0]

        return runCatching { (tpsField?.get(minecraftServer) as? DoubleArray)?.firstOrNull() }.getOrNull()
    }

    private fun mspt(): Double? {
        prepare()
        val tickTimes = runCatching { tickTimesField?.get(minecraftServer) as? LongArray }.getOrNull()
            ?.filter { it > 0L }
            ?: return null
        return tickTimes.average()
    }

    private fun prepare() {
        if (initialized) return
        initialized = true

        paperTpsMethod = server.javaClass.methods.firstOrNull {
            it.name == "getTPS" && it.parameterCount == 0 && it.returnType == DoubleArray::class.java
        }

        minecraftServer = runCatching {
            server.javaClass.methods.firstOrNull { it.name == "getServer" && it.parameterCount == 0 }
                ?.invoke(server)
        }.getOrNull()
        val serverClass = minecraftServer?.javaClass ?: return
        tpsField = findArrayField(serverClass, DoubleArray::class.java, "recentTps", "recentTPS")
        tickTimesField = findArrayField(serverClass, LongArray::class.java, "tickTimes")
    }

    private fun findArrayField(type: Class<*>, arrayType: Class<*>, vararg preferredNames: String): Field? {
        val fields = generateSequence(type) { it.superclass }.flatMap { it.declaredFields.asSequence() }.toList()
        val field = fields.firstOrNull { it.name in preferredNames && it.type == arrayType }
            ?: fields.firstOrNull { it.type == arrayType }
        field?.isAccessible = true
        return field
    }
}
