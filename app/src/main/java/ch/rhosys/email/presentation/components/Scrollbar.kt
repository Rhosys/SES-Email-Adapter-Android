package ch.rhosys.email.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

/**
 * A thin, always-present scroll indicator for a [LazyListState]-backed list.
 * Compose has no built-in scrollbar for LazyColumn, so the Settings sub-tabs
 * gave no visual hint that their content scrolls — this draws a small thumb
 * on the trailing edge, sized and positioned from the list's own state.
 */
@Composable
fun Modifier.verticalScrollbar(state: LazyListState): Modifier {
    val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant
    return composed {
        drawWithContent {
            drawContent()

            val layoutInfo = state.layoutInfo
            val totalCount = layoutInfo.totalItemsCount
            val visible = layoutInfo.visibleItemsInfo
            if (totalCount == 0 || visible.isEmpty() || visible.size >= totalCount) return@drawWithContent

            val firstVisible = visible.first()
            val avgItemSize = visible.sumOf { it.size } / visible.size.toFloat()
            if (avgItemSize <= 0f) return@drawWithContent

            val viewportHeight = layoutInfo.viewportSize.height.toFloat()
            val estimatedTotalHeight = avgItemSize * totalCount
            val thumbHeight = (viewportHeight * (viewportHeight / estimatedTotalHeight))
                .coerceIn(24.dp.toPx(), viewportHeight)

            val scrolledDistance = firstVisible.index * avgItemSize - firstVisible.offset
            val maxScrollDistance = (estimatedTotalHeight - viewportHeight).coerceAtLeast(1f)
            val thumbTravel = (viewportHeight - thumbHeight).coerceAtLeast(0f)
            val thumbOffsetY = (scrolledDistance / maxScrollDistance * thumbTravel).coerceIn(0f, thumbTravel)

            val thumbWidth = 3.dp.toPx()
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(size.width - thumbWidth - 2.dp.toPx(), thumbOffsetY),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = CornerRadius(thumbWidth / 2, thumbWidth / 2),
                alpha = 0.5f,
            )
        }
    }
}
