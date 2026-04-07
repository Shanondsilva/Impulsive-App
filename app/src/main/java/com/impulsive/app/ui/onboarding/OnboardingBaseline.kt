package com.impulsive.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos

private val values = (1..20).toList() + listOf(21)
private val itemHeightDp = 56.dp

// Number of visible items on each side of the selected item
private const val VISIBLE_ITEMS = 3

@Composable
fun OnboardingBaseline(
    baseline: Int,
    onBaselineChange: (Int) -> Unit,
    onNext: () -> Unit
) {
    val initialIndex = (values.indexOf(baseline)).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val density = LocalDensity.current.density

    val snapBehavior = rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = SnapPosition.Center
    )

    // Derived selected index — reacts to scroll changes automatically
    val selectedIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCenter =
                (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f

            layoutInfo.visibleItemsInfo
                .minByOrNull { abs((it.offset + it.size / 2f) - viewportCenter) }
                ?.index
                ?: listState.firstVisibleItemIndex
        }
    }

    // Notify parent only when scroll settles
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val value = values.getOrNull(selectedIndex) ?: baseline
            onBaselineChange(value)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        // ── Header ──────────────────────────────────────────────────────────
        Column {
            Text(
                text = "How many times a week is this happening?",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Quantifying the impulse is the first step toward reclaiming your focus.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Wheel Picker ─────────────────────────────────────────────────────
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Text(
                text = "OCCURRENCES PER WEEK",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(20.dp))

            // Picker height = 1 selected + VISIBLE_ITEMS above + VISIBLE_ITEMS below
            val pickerHeight = itemHeightDp * (1 + VISIBLE_ITEMS * 2)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(pickerHeight)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {

                // ── Selection indicator ──────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(itemHeightDp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        )
                )

                // ── Scrollable drum ──────────────────────────────────────────
                LazyColumn(
                    state = listState,
                    flingBehavior = snapBehavior,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top padding so first real item can reach center
                    items(VISIBLE_ITEMS) { Box(Modifier.height(itemHeightDp)) }

                    items(values.size) { index ->
                        val label =
                            if (values[index] == 21) "20+" else values[index].toString()

                        // Real item index in the full list (offset by top padding items)
                        val realIndex = index + VISIBLE_ITEMS

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(itemHeightDp)
                                .graphicsLayer {
                                    val layoutInfo = listState.layoutInfo
                                    val viewportHeight =
                                        layoutInfo.viewportSize.height.toFloat()
                                    if (viewportHeight == 0f) return@graphicsLayer

                                    val viewportCenter = viewportHeight / 2f

                                    val itemInfo = layoutInfo.visibleItemsInfo
                                        .find { it.index == realIndex }

                                    if (itemInfo != null) {
                                        val itemCenter =
                                            itemInfo.offset + itemInfo.size / 2f

                                        // Normalised distance: -1 at top edge, 0 at center, 1 at bottom
                                        val fraction =
                                            ((itemCenter - viewportCenter) / (viewportHeight / 2f))
                                                .coerceIn(-1f, 1f)

                                        // --- iOS cylinder maths ---
                                        // Map fraction → angle on a cylinder (max ±90°)
                                        val angleDeg = fraction * 90f
                                        val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()

                                        // Camera distance: the further away, the less extreme the perspective
                                        cameraDistance = 14f * density

                                        // Rotate around X axis like a drum cylinder
                                        rotationX = angleDeg

                                        // Scale down items at the edges (depth illusion)
                                        val scale = cos(angleRad).coerceIn(0f, 1f)
                                        scaleX = scale
                                        scaleY = scale

                                        // Fade out items toward the edge
                                        // cos gives a smooth bell curve: 1 at center, 0 at ±90°
                                        alpha = (cos(angleRad) * cos(angleRad))
                                            .coerceIn(0f, 1f)

                                        // No manual translationY — perspective projection from
                                        // rotationX already compresses items naturally.
                                        // Adding translationY on top creates a double-move artefact.
                                    } else {
                                        // Off-screen: hide completely
                                        alpha = 0f
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Highlight selected item text
                            val isSelected = realIndex == selectedIndex
                            Text(
                                text = label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = if (isSelected) 26.sp else 20.sp,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.graphicsLayer {
                                    // Subtle extra scale on the selected label for punch
                                    scaleX = if (isSelected) 1.05f else 1f
                                    scaleY = if (isSelected) 1.05f else 1f
                                }
                            )
                        }
                    }

                    // Bottom padding mirror of top
                    items(VISIBLE_ITEMS) { Box(Modifier.height(itemHeightDp)) }
                }

                // ── Edge fades (draw on top of content) ─────────────────────
                val fadeColor = MaterialTheme.colorScheme.surfaceContainer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeightDp * VISIBLE_ITEMS)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                0f to fadeColor,
                                0.6f to fadeColor.copy(alpha = 0.85f),
                                1f to Color.Transparent
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeightDp * VISIBLE_ITEMS)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.4f to fadeColor.copy(alpha = 0.85f),
                                1f to fadeColor
                            )
                        )
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "This baseline helps us tailor Impulsive to your current behavioral patterns.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        // ── CTA ──────────────────────────────────────────────────────────────
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("Next", style = MaterialTheme.typography.labelLarge)
        }
    }
}