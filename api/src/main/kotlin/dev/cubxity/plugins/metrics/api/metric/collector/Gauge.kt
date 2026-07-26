/*
 *     This file is part of UnifiedMetrics.
 *
 *     UnifiedMetrics is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package dev.cubxity.plugins.metrics.api.metric.collector

import dev.cubxity.plugins.metrics.api.metric.data.GaugeMetric
import dev.cubxity.plugins.metrics.api.metric.data.Labels
import dev.cubxity.plugins.metrics.api.metric.data.Metric

/** A collector that exposes its most recently supplied value. */
class Gauge(
    private val name: String,
    private val labels: Labels = emptyMap()
) : Collector {
    @Volatile
    private var value = 0.0

    override fun collect(): List<Metric> = listOf(GaugeMetric(name, labels, value))

    fun set(value: Number) {
        this.value = value.toDouble()
    }
}
