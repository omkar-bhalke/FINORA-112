package com.finora.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun BrandSurface(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xff082e28), Color(0xff104d40), Color(0xff1d6150))
                )
            )
    ) {
        Canvas(Modifier.matchParentSize()) {
            val center = Offset(size.width * 1.05f, size.height * 1.28f)
            for (i in 1..7) drawCircle(
                Color(0xffa4d8b6).copy(alpha = .19f),
                size.height * (.28f + i * .125f),
                center,
                style = Stroke(1.dp.toPx()),
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.Start,
            content = content,
        )
    }
}

@Composable
fun NavigationGroup(title: String, destinations: List<Pair<String, String>>, go: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Surface(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            destinations.forEachIndexed { index, (route, label) ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(onClickLabel = label) { go(route) }
                        .heightIn(min = 54.dp)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (index < destinations.lastIndex)
                    HorizontalDivider(
                        Modifier.padding(start = 18.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
            }
        }
    }
}
