package com.geotagcamera.geotagginglocationonphoto.ui.verify

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.geotagcamera.geotagginglocationonphoto.security.PhotoIntegrity
import com.geotagcamera.geotagginglocationonphoto.ui.common.AppScaffold
import com.geotagcamera.geotagginglocationonphoto.ui.common.ProofCard
import com.geotagcamera.geotagginglocationonphoto.ui.common.rememberPhotoPicker

/**
 * Verify a photo from any source — a share-target file, a gallery pick — with
 * zero captures ever taken here. Reads the embedded proof and checks it against
 * the public key that travels in the file itself (design section 05).
 */
@Composable
fun VerifyScreen(
    uri: String?,
    onBack: () -> Unit,
    viewModel: VerifyViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deviceKey = remember { PhotoIntegrity.devicePublicKeyBase64OrNull() }

    LaunchedEffect(uri) {
        if (!uri.isNullOrBlank()) viewModel.verify(Uri.parse(uri))
    }

    val pickPhoto = com.geotagcamera.geotagginglocationonphoto.ui.common.rememberVerifyPicker { picked -> viewModel.verify(picked) }

    AppScaffold(title = "Verify a photo", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (val s = state) {
                    is VerifyUiState.Empty -> EmptyPrompt()
                    is VerifyUiState.Verifying -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    is VerifyUiState.Done -> Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AsyncImage(
                            model = s.previewUri,
                            contentDescription = "Photo being verified",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        ProofCard(s.outcome, deviceKey)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = pickPhoto, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state is VerifyUiState.Done) "Check another photo" else "Choose a photo")
            }
        }
    }
}

@Composable
private fun EmptyPrompt() {
    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.VerifiedUser, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("Check a photo's proof", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Pick any photo, including one someone sent you. GeoTag Camera checks whether it still matches " +
                    "the signature it was saved with. Nothing is uploaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
