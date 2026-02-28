package com.watchwhatwhere.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.watchwhatwhere.app.data.model.ArtistDetail
import com.watchwhatwhere.app.data.model.TitleItem
import com.watchwhatwhere.app.data.repository.TitleRepository
import com.watchwhatwhere.app.ui.components.ErrorScreen
import com.watchwhatwhere.app.ui.components.getErrorMessage
import com.watchwhatwhere.app.ui.components.TitleCarousel
import com.watchwhatwhere.app.ui.theme.CardBackground
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistUiState(
    val isLoading: Boolean = true,
    val artist: ArtistDetail? = null,
    val error: Throwable? = null
)

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val repository: TitleRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ArtistUiState())
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()
    
    fun load(artistId: Long) {
        viewModelScope.launch {
            _uiState.value = ArtistUiState(isLoading = true)
            repository.getArtist(artistId)
                .onSuccess { artist ->
                    _uiState.value = ArtistUiState(
                        isLoading = false,
                        artist = artist
                    )
                }
                .onFailure { e ->
                    _uiState.value = ArtistUiState(
                        isLoading = false,
                        error = e
                    )
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    artistId: Long,
    onTitleClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    viewModel: ArtistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(artistId) {
        viewModel.load(artistId)
    }
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(uiState.artist?.nama ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
                    ErrorScreen(
                        message = getErrorMessage(uiState.error!!),
                        onRetry = { viewModel.load(artistId) }
                    )
                }
                uiState.artist != null -> {
                    val artist = uiState.artist!!
                    
                    LazyColumn {
                        // Profile header
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Profile image
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(artist.data?.profilePath ?: artist.data?.profilePathSmall)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = artist.nama,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(150.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CardBackground)
                                )
                                
                                // Info
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = artist.nama,
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                    
                                    artist.data?.knownForDepartment?.let {
                                        Text(
                                            text = "Known for: $it",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.Gray
                                        )
                                    }
                                    
                                    artist.data?.birthday?.let { birthday ->
                                        val deathday = artist.data?.deathday
                                        Text(
                                            text = if (deathday != null) "$birthday - $deathday" else birthday,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                    
                                    artist.data?.placeOfBirth?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Biography
                        artist.data?.biography?.takeIf { it.isNotBlank() }?.let { bio ->
                            item {
                                var expanded by remember { mutableStateOf(false) }
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Biography",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = bio,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = if (expanded) Int.MAX_VALUE else 5
                                    )
                                    if (bio.length > 300) {
                                        TextButton(onClick = { expanded = !expanded }) {
                                            Text(if (expanded) "Show Less" else "Show More")
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Filmography by category
                        artist.princ?.let { credits ->
                            val groupedCredits = credits
                                .filter { it.data != null }
                                .groupBy { it.category ?: "Other" }
                            
                            groupedCredits.forEach { (category, categoryCredits) ->
                                val titles = categoryCredits.mapNotNull { it.data }
                                    .sortedByDescending { it.rating }
                                
                                if (titles.isNotEmpty()) {
                                    item {
                                        TitleCarousel(
                                            title = category.replaceFirstChar { it.uppercase() },
                                            items = titles,
                                            onTitleClick = onTitleClick,
                                            modifier = Modifier.padding(vertical = 16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        item { Spacer(modifier = Modifier.height(32.dp)) }
                    }
                }
            }
        }
    }
}
