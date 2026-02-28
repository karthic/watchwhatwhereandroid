package com.watchwhatwhere.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.watchwhatwhere.app.data.model.TitleItem
import com.watchwhatwhere.app.data.model.WatchUrl
import com.watchwhatwhere.app.ui.theme.CardBackground
import com.watchwhatwhere.app.ui.theme.FreeBadgeGreen

@Composable
fun TitleCard(
    title: TitleItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(130.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(title.posterPathSmall ?: title.posterPath)
                        .crossfade(true)
                        .build(),
                    contentDescription = title.nama,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(195.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(CardBackground)
                )
                
                // Free badge
                if (title.isFree) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .background(FreeBadgeGreen)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "FREE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
            
            // Title text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground)
                    .padding(8.dp)
            ) {
                Text(
                    text = title.displayTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun TitleCarousel(
    title: String,
    items: List<TitleItem>,
    onTitleClick: (Long) -> Unit,
    onSeeAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            if (onSeeAllClick != null) {
                Text(
                    text = "See All",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onSeeAllClick)
                )
            }
        }
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item ->
                TitleCard(
                    title = item,
                    onClick = { onTitleClick(item.id) }
                )
            }
        }
    }
}

@Composable
fun StreamingLogo(
    watchUrl: WatchUrl,
    modifier: Modifier = Modifier,
    isHidden: Boolean = false
) {
    val context = LocalContext.current
    
    // Badge colors matching the website
    val badgeColor = when (watchUrl.viewcost) {
        "free" -> Color(0xFF46D369)   // Green
        "sub" -> Color(0xFFE50914)    // Red
        "rent" -> Color(0xFFF59E0B)   // Amber
        "buy" -> Color(0xFFEF4444)    // Red-light
        "plus" -> Color(0xFF8B5CF6)   // Purple
        else -> Color(0xFF6B7280)     // Gray
    }
    val badgeText = when (watchUrl.viewcost) {
        "free" -> "FREE"
        "sub" -> "SUB"
        "rent" -> "RENT"
        "buy" -> "BUY"
        "plus" -> "PLUS"
        else -> watchUrl.viewcost?.uppercase() ?: ""
    }
    
    Box(
        modifier = modifier
            .width(72.dp)
            .alpha(if (isHidden) 0.4f else 1f)
            .clickable { openWatchUrl(context, watchUrl.pageurl) },
        contentAlignment = Alignment.TopCenter
    ) {
        // Provider logo
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(watchUrl.logo)
                .crossfade(true)
                .build(),
            contentDescription = watchUrl.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(top = 10.dp)
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
        )
        
        // Badge overlay at top center
        if (badgeText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .background(badgeColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }
    }
}

fun openWatchUrl(context: Context, url: String?) {
    url?.let {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
        context.startActivity(intent)
    }
}
