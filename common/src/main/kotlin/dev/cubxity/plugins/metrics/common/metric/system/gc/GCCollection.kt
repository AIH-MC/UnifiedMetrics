/*
 *     This file is part of UnifiedMetrics.
 *
 *     UnifiedMetrics is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     UnifiedMetrics is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with UnifiedMetrics.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.cubxity.plugins.metrics.common.metric.system.gc

import dev.cubxity.plugins.metrics.api.metric.collector.Collector
import dev.cubxity.plugins.metrics.api.metric.collector.CollectorCollection
import dev.cubxity.plugins.metrics.api.metric.collector.Gauge
import dev.cubxity.plugins.metrics.api.util.fastForEach
import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory
import java.util.*
import javax.management.NotificationEmitter

class GCCollection : CollectorCollection {
    private val monitors = WeakHashMap<GarbageCollectorMXBean, GCMonitor>()

    override val collectors = ArrayList<Collector>()

    override val isAsync: Boolean
        get() = true

    override fun initialize() {
        ManagementFactory.getGarbageCollectorMXBeans().fastForEach { bean ->
            if (bean is NotificationEmitter) {
                val labels = mapOf("gc" to bean.name)
                val durationGauge = Gauge("jvm_gc_duration_seconds", labels)
                val freedGauge = Gauge("jvm_gc_freed_bytes", labels)

                collectors += durationGauge
                collectors += freedGauge

                val monitor = GCMonitor(durationGauge, freedGauge)
                monitors[bean] = monitor

                bean.addNotificationListener(monitor, null, null)
            }
        }
    }

    override fun dispose() {
        ManagementFactory.getGarbageCollectorMXBeans().fastForEach { bean ->
            if (bean is NotificationEmitter) {
                val monitor = monitors.remove(bean)
                if (monitor !== null) {
                    bean.removeNotificationListener(monitor)
                }
            }
        }
        collectors.clear()
    }
}
