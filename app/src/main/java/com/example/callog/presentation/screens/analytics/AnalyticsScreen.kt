package com.example.callog.presentation.screens.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.callog.core.extensions.toDurationString
import com.example.callog.presentation.components.ContactAvatar
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.AnalyticsViewModel

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Slate300
                    )
                }
            } else if (onMenuClick != null) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = Slate300
                    )
                }
            }

            Text(
                text = "Call Analytics",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Slate50
            )
        }

        // General stats card row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassyCard(modifier = Modifier.weight(1f)) {
                Column {
                    Text("Average Duration", color = Slate400, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.avgDurationSeconds.toDurationString(),
                        color = Green500,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            GlassyCard(modifier = Modifier.weight(1f)) {
                Column {
                    Text("Longest Call", color = Slate400, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.longestCallSeconds.toDurationString(),
                        color = Teal300,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "With ${state.longestCallName.take(12)}",
                        color = Slate400,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }
        }

        // 1. Call Distribution Donut (Pie) Chart
        if (state.totalCalls > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Call Distribution",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )
                GlassyCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DonutChart(
                            percentages = state.callTypePercentages,
                            colors = listOf(Green500, Teal300, Red500, Amber500),
                            modifier = Modifier
                                .size(130.dp)
                                .padding(8.dp)
                        )
                        Spacer(modifier = Modifier.width(20.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            val keys = listOf("Incoming", "Outgoing", "Missed", "Rejected/Spam")
                            val colors = listOf(Green500, Teal300, Red500, Amber500)
                            val counts = listOf(state.incomingCount, state.outgoingCount, state.missedCount, state.rejectedCount)
                            
                            keys.forEachIndexed { index, key ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(colors[index])
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$key: ${counts[index]}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Slate400,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Weekly Calling volume (Bar Chart)
        if (state.totalCalls > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Weekly Call Volume",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )
                GlassyCard(modifier = Modifier.fillMaxWidth()) {
                    WeeklyBarChart(
                        data = state.callsByDayOfWeek,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                }
            }
        }

        // 3. Daily trends (Bezier curve Line Chart)
        if (state.totalCalls > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Hourly Call Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )
                GlassyCard(modifier = Modifier.fillMaxWidth()) {
                    HourlyActivityLineChart(
                        data = state.callsByHourOfDay,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                }
            }
        }

        // 4. Most Contacted People Table
        if (state.topContacts.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Top Contacted People",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )
                
                GlassyCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.topContacts.forEach { contact ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ContactAvatar(
                                    name = contact.name,
                                    initials = contact.name.take(1).uppercase(),
                                    photoUri = contact.photoUri,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Slate50
                                    )
                                    Text(
                                        text = "${contact.count} calls • ${contact.totalDuration.toDurationString()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Slate400
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DonutChart(
    percentages: Map<String, Float>,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 24.dp.toPx()
        val radiusSize = size.minDimension - strokeWidth
        val topLeftOffset = strokeWidth / 2

        var startAngle = -90f
        val keys = percentages.keys.toList()

        keys.forEachIndexed { index, key ->
            val percentage = percentages[key] ?: 0f
            val sweepAngle = percentage * 360f
            if (sweepAngle > 0f) {
                drawArc(
                    color = colors[index],
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(topLeftOffset, topLeftOffset),
                    size = Size(radiusSize, radiusSize),
                    style = Stroke(width = strokeWidth)
                )
                startAngle += sweepAngle
            }
        }
    }
}

@Composable
fun WeeklyBarChart(
    data: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val maxVal = (data.values.maxOrNull() ?: 1).coerceAtLeast(1)

    // Resolve composable color getters outside canvas draw scope
    val colorTeal500 = Teal500
    val colorTeal300 = Teal300

    Canvas(modifier = modifier) {
        val textHeight = 24.dp.toPx()
        val canvasHeight = size.height - textHeight
        val barWidth = 14.dp.toPx()
        
        val gap = (size.width - (barWidth * days.size)) / (days.size + 1)

        days.forEachIndexed { index, day ->
            val count = data[day] ?: 0
            val barHeight = (count.toFloat() / maxVal) * (canvasHeight - 20.dp.toPx())
            
            val x = gap + index * (barWidth + gap)
            val y = canvasHeight - barHeight

            // Draw bar
            drawRoundRect(
                color = colorTeal500,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // Draw label
            // In canvas, drawing text requires NativeCanvas/Paint, so we will construct a clean visualization layout.
            // As a nice fallback, we'll draw a small dot under the bar if count > 0, indicating value presence
            if (count > 0) {
                drawCircle(
                    color = colorTeal300,
                    radius = 3.dp.toPx(),
                    center = Offset(x + barWidth / 2, y - 6.dp.toPx())
                )
            }
        }
    }
    
    // Label Row underneath canvas
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        days.forEach { day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = (data[day] ?: 0).toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Slate400
                )
                Text(
                    text = day,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400
                )
            }
        }
    }
}

@Composable
fun HourlyActivityLineChart(
    data: Map<Int, Int>,
    modifier: Modifier = Modifier
) {
    val blocks: List<String> = listOf("Night", "Morning", "Afternoon", "Evening")
    val blockValues: List<Int> = remember(data) {
        val vals = mutableListOf(0, 0, 0, 0)
        data.forEach { (hour, count) ->
            when (hour) {
                in 0..5 -> vals[0] += count
                in 6..11 -> vals[1] += count
                in 12..17 -> vals[2] += count
                else -> vals[3] += count
            }
        }
        vals
    }
    
    val maxVal = blockValues.maxOrNull()?.coerceAtLeast(1) ?: 1
    
    // Resolve dynamic colors outside Canvas drawing scope
    val colorTeal500 = Teal500
    val colorTeal300 = Teal300
    val colorAmber500 = Amber500
    val colorSlate800 = Slate800

    Canvas(modifier = modifier) {
        val points: List<Offset> = blockValues.mapIndexed { index: Int, value: Int ->
            val x = (index.toFloat() / (blockValues.size - 1)) * size.width
            val y = size.height - ((value.toFloat() / maxVal) * (size.height - 24.dp.toPx())) - 8.dp.toPx()
            Offset(x, y)
        }

        val path = Path().apply {
            if (points.isNotEmpty()) {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    val pre = points[i - 1]
                    val curr = points[i]
                    // Bezier cubic curve coordinates
                    val conX1 = (pre.x + curr.x) / 2
                    val conY1 = pre.y
                    val conX2 = (pre.x + curr.x) / 2
                    val conY2 = curr.y
                    cubicTo(conX1, conY1, conX2, conY2, curr.x, curr.y)
                }
            }
        }

        // Draw background gradient fill under path
        val fillPath = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(colorTeal500.copy(alpha = 0.35f), Color.Transparent)
            )
        )

        // Draw Line path
        drawPath(
            path = path,
            color = colorTeal300,
            style = Stroke(width = 3.dp.toPx())
        )

        // Draw points
        points.forEach { point: Offset ->
            drawCircle(
                color = colorAmber500,
                radius = 5.dp.toPx(),
                center = point
            )
            drawCircle(
                color = colorSlate800,
                radius = 3.dp.toPx(),
                center = point
            )
        }
    }

    // X-axis block labels row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        blocks.forEachIndexed { index: Int, block: String ->
            Column(
                horizontalAlignment = when (index) {
                    0 -> Alignment.Start
                    blocks.size - 1 -> Alignment.End
                    else -> Alignment.CenterHorizontally
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = blockValues[index].toString() + " calls",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )
                Text(
                    text = block,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400
                )
            }
        }
    }
}