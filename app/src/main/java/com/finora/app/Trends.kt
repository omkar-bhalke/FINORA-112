package com.finora.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.temporal.ChronoUnit

@Composable
fun Trends(rows: List<Entry>, start: LocalDate, end: LocalDate) {
    val sum = rows.filterNot { it.credit }.groupBy { it.date() }.mapValues { it.value.spent() }
    val monthly = ChronoUnit.DAYS.between(start, end) > 35
    val labels =
        if (monthly) (1..end.monthValue).map { Month.of(it).name.take(3) }
        else
            (0..ChronoUnit.DAYS.between(start, end).toInt()).map {
                start.plusDays(it.toLong()).dayOfMonth.toString()
            }
    val values =
        if (monthly)
            (1..end.monthValue).map { m -> sum.filterKeys { it.monthValue == m }.values.sum() }
        else labels.indices.map { sum[start.plusDays(it.toLong())] ?: 0 }
    var graph by rememberSaveable { mutableStateOf("Bars") }
    val color = MaterialTheme.colorScheme.primary
    Panel {
        Heading("Spending trend")
        Choice("Chart", graph, listOf("Bars", "Line", "Area", "Donut")) { graph = it }
        if (values.any { it > 0 })
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val max = values.maxOrNull()!!.toFloat()
                val step = size.width / values.size
                val points =
                    values.mapIndexed { i, v ->
                        Offset(step * (i + .5f), size.height * (1 - v / max))
                    }
                when (graph) {
                    "Bars" ->
                        points.forEach { pt ->
                            drawRect(
                                color,
                                Offset(pt.x - step * .32f, pt.y),
                                Size(step * .64f, size.height - pt.y),
                            )
                        }
                    "Donut" -> {
                        val side = size.minDimension * .75f
                        val top = Offset((size.width - side) / 2, (size.height - side) / 2)
                        var angle = -90f
                        val cats =
                            rows.filterNot { it.credit }.groupBy { it.category }.toSortedMap()
                        cats.values.forEachIndexed { i, list ->
                            val sweep = 360f * list.spent() / rows.spent()
                            drawArc(
                                Color.hsv((155f + i * 48) % 360, .58f, .68f),
                                angle,
                                sweep,
                                false,
                                top,
                                Size(side, side),
                                style = Stroke(side * .25f),
                            )
                            angle += sweep
                        }
                    }
                    else -> {
                        val path =
                            Path().apply {
                                moveTo(points.first().x, points.first().y)
                                points.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                        drawPath(path, color, style = Stroke(3.dp.toPx()))
                        points.forEach { drawCircle(color, 3.dp.toPx(), it) }
                        if (graph == "Area") {
                            path.lineTo(points.last().x, size.height)
                            path.lineTo(points.first().x, size.height)
                            path.close()
                            drawPath(path, color.copy(alpha = .18f))
                        }
                    }
                }
            }
        else Text("No recorded spending for this period")
        if (graph == "Donut") {
            rows
                .filterNot { it.credit }
                .groupBy { it.category }
                .toSortedMap()
                .forEach { (cat, items) -> Text(cat + " · " + money(items.spent())) }
        } else Text("$start – $end")
        if (sum.isNotEmpty()) {
            Text("Highest · " + sum.maxBy { it.value }.key + " · " + money(sum.values.max()))
            Text(
                "Average per day · " +
                    money(rows.spent() / (ChronoUnit.DAYS.between(start, end) + 1))
            )
        }
    }
}
