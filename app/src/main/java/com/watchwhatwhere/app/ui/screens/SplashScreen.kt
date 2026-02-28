package com.watchwhatwhere.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SplashScreen(
    onAnimationComplete: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    // Calculate screen dimensions
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val maxExpandDistance = kotlin.math.max(screenWidthPx, screenHeightPx) * 0.7f
    
    // Animation states
    var animationStarted by remember { mutableStateOf(false) }
    
    // Ring expansion animation (starts after initial display)
    val ringExpansion by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(
            durationMillis = 1000,
            easing = FastOutSlowInEasing
        ),
        label = "ringExpansion"
    )
    
    // Text fade out (starts when rings begin expanding)
    val textAlpha by animateFloatAsState(
        targetValue = if (animationStarted) 0f else 1f,
        animationSpec = tween(
            durationMillis = 400,
            delayMillis = 200,
            easing = LinearEasing
        ),
        label = "textAlpha"
    )
    
    // Overall fade out at the end
    val screenAlpha by animateFloatAsState(
        targetValue = if (animationStarted) 0f else 1f,
        animationSpec = tween(
            durationMillis = 300,
            delayMillis = 800,
            easing = LinearEasing
        ),
        finishedListener = { onAnimationComplete() },
        label = "screenAlpha"
    )
    
    // Start animation after brief delay
    LaunchedEffect(Unit) {
        delay(600) // Show logo briefly before animation
        animationStarted = true
    }
    
    // Ring color
    val ringColor = Color.White
    val strokeWidth = with(density) { 2.5.dp.toPx() }
    
    // Venn diagram parameters
    val circleRadius = with(density) { 44.dp.toPx() } // Size of each circle
    val vennOffset = with(density) { 18.dp.toPx() } // Distance from center - closer for better overlap
    
    // Four circles arranged at 90° intervals (like a 4-circle Venn diagram)
    // Angles: -90° (top), 0° (right), 90° (bottom), 180° (left)
    val ringAngles = listOf(
        Math.toRadians(-90.0),   // Top
        Math.toRadians(0.0),     // Right
        Math.toRadians(90.0),    // Bottom
        Math.toRadians(180.0)    // Left
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(screenAlpha)
            .background(Color(0xFF0D0D0D)), // Very dark background
        contentAlignment = Alignment.Center
    ) {
        // Animated rings
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2 - 40.dp.toPx() // Slightly above center to make room for text
            
            ringAngles.forEachIndexed { index, angle ->
                // Initial position in Venn diagram arrangement
                val initialOffsetX = (cos(angle) * vennOffset).toFloat()
                val initialOffsetY = (sin(angle) * vennOffset).toFloat()
                
                // Each ring expands outward along its angle
                val expandX = (cos(angle) * maxExpandDistance * ringExpansion).toFloat()
                val expandY = (sin(angle) * maxExpandDistance * ringExpansion).toFloat()
                
                // Current position = initial + expansion
                val currentX = centerX + initialOffsetX + expandX
                val currentY = centerY + initialOffsetY + expandY
                
                // Ring grows slightly as it expands
                val expandedRadius = circleRadius * (1 + ringExpansion * 0.3f)
                
                // Ring alpha fades as it expands
                val ringAlpha = 1f - ringExpansion * 0.8f
                
                drawCircle(
                    color = ringColor.copy(alpha = ringAlpha),
                    radius = expandedRadius,
                    center = Offset(currentX, currentY),
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    )
                )
            }
        }
        
        // App name text (below the rings)
        Text(
            text = "WatchWhatWhere",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = Color.White,
            modifier = Modifier
                .alpha(textAlpha)
                .padding(top = 200.dp)
        )
    }
}
