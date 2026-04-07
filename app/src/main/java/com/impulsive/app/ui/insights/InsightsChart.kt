package com.impulsive.app.ui.insights

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.chart.line.LineChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry

private val Teal     = androidx.compose.ui.graphics.Color(0xFF7CD6CD)
private val Lavender = androidx.compose.ui.graphics.Color(0xFFCDBEF8)

/**
 * 2-line weekly insights chart.
 *
 * Line 1 (teal  #7CD6CD): total interceptions per day (all TriggerLog entries)
 * Line 2 (lavender #CDBEF8): sessions used per day (TriggerLog entries where outcome = Continue)
 *
 * X axis: Mon=0 through Sun=6.
 * Pass 7-element lists, one value per day (Mon..Sun).
 */
@Composable
fun InsightsChart(
    interceptionsByDay: List<Int>,   // 7 values, Mon-Sun
    sessionsByDay: List<Int>,        // 7 values, Mon-Sun
    modifier: Modifier = Modifier
) {
    val producer = remember { ChartEntryModelProducer() }

    LaunchedEffect(interceptionsByDay, sessionsByDay) {
        val series1 = interceptionsByDay.mapIndexed { i, v -> FloatEntry(i.toFloat(), v.toFloat()) }
        val series2 = sessionsByDay.mapIndexed { i, v -> FloatEntry(i.toFloat(), v.toFloat()) }
        producer.setEntries(listOf(series1, series2))
    }

    val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    Chart(
        chart = lineChart(
            lines = listOf(
                LineChart.LineSpec(lineColor = Teal.toArgb()),
                LineChart.LineSpec(lineColor = Lavender.toArgb())
            )
        ),
        chartModelProducer = producer,
        startAxis  = rememberStartAxis(),
        bottomAxis = rememberBottomAxis(
            valueFormatter = { value, _ ->
                dayLabels.getOrElse(value.toInt()) { "" }
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
    )
}
