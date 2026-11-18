package com.clipflow.app.player

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R

object AppTourState {
    const val PREF_TOUR_SEEN = "has_seen_main_app_tour"

    var isTourActive by mutableStateOf(false)

    fun checkFirstTime(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean(PREF_TOUR_SEEN, false)) {
            isTourActive = true
        }
    }

    fun startTour() {
        isTourActive = true
    }

    fun finishTour(context: Context) {
        isTourActive = false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean(PREF_TOUR_SEEN, true).apply()
    }
}

data class TourStep(
    val icon: ImageVector,
    val titleRes: Int,
    val descRes: Int,
    val alignment: Alignment = Alignment.Center
)

@Composable
fun AppSpotlightTour(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(0) }

    val steps = remember {
        listOf(
            TourStep(
                icon = Icons.Rounded.Subscriptions,
                titleRes = R.string.tour_step1_title,
                descRes = R.string.tour_step1_desc,
                alignment = Alignment.TopCenter
            ),
            TourStep(
                icon = Icons.Rounded.Search,
                titleRes = R.string.tour_step2_title,
                descRes = R.string.tour_step2_desc,
                alignment = Alignment.TopCenter
            ),
            TourStep(
                icon = Icons.Rounded.Download,
                titleRes = R.string.tour_step3_title,
                descRes = R.string.tour_step3_desc,
                alignment = Alignment.BottomCenter
            ),
            TourStep(
                icon = Icons.Rounded.Folder,
                titleRes = R.string.tour_step4_title,
                descRes = R.string.tour_step4_desc,
                alignment = Alignment.BottomCenter
            ),
            TourStep(
                icon = Icons.Rounded.Person,
                titleRes = R.string.tour_step5_title,
                descRes = R.string.tour_step5_desc,
                alignment = Alignment.Center
            )
        )
    }

    val step = steps[currentStep]
    val c = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF161616))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${currentStep + 1} / ${steps.size}",
                    color = c.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                TextButton(
                    onClick = {
                        AppTourState.finishTour(context)
                        onDismiss()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.tour_skip),
                        color = c.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(c.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = step.icon,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(step.titleRes),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(step.descRes),
                color = Color(0xFFCCCCCC),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentStep > 0) {
                    TextButton(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        onClick = { currentStep-- }
                    ) {
                        Text(
                            text = stringResource(R.string.ui_back),
                            color = Color.White
                        )
                    }
                }

                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.primary),
                    onClick = {
                        if (currentStep < steps.size - 1) {
                            currentStep++
                        } else {
                            AppTourState.finishTour(context)
                            onDismiss()
                        }
                    }
                ) {
                    Text(
                        text = stringResource(
                            if (currentStep == steps.size - 1) R.string.tour_finish else R.string.tour_next
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
