package com.lostf1sh.pixelplayeross.presentation.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.lostf1sh.pixelplayeross.BuildConfig
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.SineWaveLine
import com.lostf1sh.pixelplayeross.ui.theme.ExpTitleTypography
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BetaInfoBottomSheet(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repoUrl = "https://github.com/Eduardocastelan01/PiedPiper"
    val issuesUrl = "https://github.com/Eduardocastelan01/PiedPiper/issues"

    val fabCornerRadius = 18.dp

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Pied Piper",
                        fontFamily = RoundedSans,
                        style = ExpTitleTypography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SineWaveLine(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .padding(horizontal = 8.dp),
                        animate = true,
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f),
                        alpha = 0.95f,
                        strokeWidth = 4.dp,
                        amplitude = 4.dp,
                        waves = 7.6f,
                        phase = 0f
                    )
                }
            }

            item(key = "welcome") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTR = fabCornerRadius,
                        cornerRadiusTL = fabCornerRadius,
                        cornerRadiusBL = fabCornerRadius,
                        cornerRadiusBR = fabCornerRadius,
                        smoothnessAsPercentTR = 60,
                        smoothnessAsPercentTL = 60,
                        smoothnessAsPercentBL = 60,
                        smoothnessAsPercentBR = 60
                    ),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.presentation_batch_g_beta_sheet_welcome_title,
                                    BuildConfig.VERSION_NAME
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(
                                    if (BuildConfig.BUILD_TYPE == "release") {
                                        R.string.presentation_batch_g_beta_sheet_welcome_body_stable
                                    } else {
                                        R.string.presentation_batch_g_beta_sheet_welcome_body
                                    }
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item(key = "features") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTR = fabCornerRadius,
                        cornerRadiusTL = fabCornerRadius,
                        cornerRadiusBL = fabCornerRadius,
                        cornerRadiusBR = fabCornerRadius,
                        smoothnessAsPercentTR = 60,
                        smoothnessAsPercentTL = 60,
                        smoothnessAsPercentBL = 60,
                        smoothnessAsPercentBR = 60
                    ),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Características principales",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        FeatureHighlightItem(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "Playlists Inteligentes con Gemini IA",
                            description = "Crea selecciones musicales acordes a tu estado de ánimo y gustos con Google Gemini."
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        FeatureHighlightItem(
                            icon = Icons.Rounded.Cloud,
                            title = "Integración con Google Drive",
                            description = "Transmite y sincroniza archivos de audio directamente desde tu almacenamiento en la nube."
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        FeatureHighlightItem(
                            icon = Icons.Rounded.Shuffle,
                            title = "Smart Shuffle y Cola Inteligente",
                            description = "Gestión fluida de tu cola de reproducción con historial de pistas ya escuchadas."
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        FeatureHighlightItem(
                            icon = Icons.Rounded.GraphicEq,
                            title = "Material 3 Expressive & Sonido Pro",
                            description = "Ecualizador integrado y diseño adaptativo que se sincroniza con tus colores del sistema."
                        )
                    }
                }
            }

            item(key = "links") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = { launchUrl(context, repoUrl) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text("GitHub", fontWeight = FontWeight.SemiBold)
                    }

                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = { launchUrl(context, issuesUrl) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BugReport,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Reportes", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureHighlightItem(
    icon: ImageVector,
    title: String,
    description: String,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = iconTint.copy(alpha = 0.12f),
            modifier = Modifier
                .padding(top = 2.dp)
                .size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun launchUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}
