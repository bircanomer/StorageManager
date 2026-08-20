package com.storagemanager.ui.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.storagemanager.R
import com.storagemanager.billing.BillingRepository

private val DarkBackground = Color(0xFF0D0D1A)
private val DarkSurface = Color(0xFF1A1A2E)
private val DarkSurfaceVariant = Color(0xFF252540)
private val PrimaryColor = Color(0xFF6C63FF)
private val SecondaryColor = Color(0xFF00BCD4)
private val OnSurfaceColor = Color(0xFFC8C8D8)
private val GoldColor = Color(0xFFFFC107)

/** Compose bağlamından Activity'yi bulur — satın alma akışı Activity gerektirir. */
internal fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    navController: NavController,
    viewModel: PaywallViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedProductId by remember { mutableStateOf<String?>(null) }
    val selected = state.plans.firstOrNull { it.productId == selectedProductId }
        ?: state.plans.firstOrNull { it.productId == BillingRepository.PRODUCT_PRO_YEARLY }
        ?: state.plans.firstOrNull()

    val successText = stringResource(R.string.paywall_purchase_success)
    val pendingText = stringResource(R.string.paywall_purchase_pending)
    val cancelledText = stringResource(R.string.paywall_purchase_cancelled)
    val failedText = stringResource(R.string.paywall_purchase_failed)
    val rewardText = stringResource(R.string.paywall_reward_granted)

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val text = when (message) {
            PaywallMessage.SUCCESS -> successText
            PaywallMessage.PENDING -> pendingText
            PaywallMessage.CANCELLED -> cancelledText
            PaywallMessage.FAILED -> failedText
            PaywallMessage.REWARD_GRANTED -> rewardText
        }
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
        if (message == PaywallMessage.SUCCESS) navController.popBackStack()
    }

    Scaffold(
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { PaywallHeader() }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BenefitRow(stringResource(R.string.paywall_benefit_photo_ai))
                    BenefitRow(stringResource(R.string.paywall_benefit_auto_scan))
                    BenefitRow(stringResource(R.string.paywall_benefit_large_files))
                    BenefitRow(stringResource(R.string.paywall_benefit_treemap))
                    BenefitRow(stringResource(R.string.paywall_benefit_no_ads))
                }
            }

            item { Spacer(Modifier.height(4.dp)) }

            if (state.isPro) {
                item { AlreadyProCard() }
            } else if (state.loading && state.plans.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PrimaryColor)
                    }
                }
            } else if (state.plans.isEmpty()) {
                item { PriceUnavailableCard() }
            } else {
                items(state.plans.size) { index ->
                    val plan = state.plans[index]
                    PlanCard(
                        plan = plan,
                        isSelected = plan.productId == selected?.productId,
                        onClick = { selectedProductId = plan.productId }
                    )
                }

                item {
                    Button(
                        onClick = {
                            val activity = context.findActivity() ?: return@Button
                            selected?.let { viewModel.purchase(activity, it) }
                        },
                        enabled = selected != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryColor,
                            disabledContainerColor = DarkSurfaceVariant
                        )
                    ) {
                        Text(
                            stringResource(R.string.paywall_continue),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (!state.isPro) {
                item { RewardedAdCard(onClick = {
                    val activity = context.findActivity() ?: return@RewardedAdCard
                    viewModel.watchRewardedAd(activity)
                }) }

                item {
                    TextButton(
                        onClick = { viewModel.restore() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.paywall_restore),
                            color = SecondaryColor,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.paywall_terms_note),
                    color = OnSurfaceColor.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                )
            }
        }
    }
}

@Composable
private fun PaywallHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    brush = Brush.linearGradient(listOf(PrimaryColor, SecondaryColor)),
                    shape = RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.paywall_title),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.paywall_subtitle),
            color = OnSurfaceColor.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun BenefitRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = SecondaryColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            color = OnSurfaceColor,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PlanCard(
    plan: Plan,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val titleRes = when (plan.productId) {
        BillingRepository.PRODUCT_PRO_MONTHLY -> R.string.paywall_plan_monthly
        BillingRepository.PRODUCT_PRO_YEARLY -> R.string.paywall_plan_yearly
        else -> R.string.paywall_plan_lifetime
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) DarkSurfaceVariant else DarkSurface
        ),
        border = if (isSelected) BorderStroke(2.dp, PrimaryColor) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(titleRes),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (plan.productId == BillingRepository.PRODUCT_PRO_YEARLY) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.paywall_best_value),
                        color = GoldColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                plan.formattedPrice,
                color = if (isSelected) Color.White else OnSurfaceColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AlreadyProCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = GoldColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.paywall_already_pro),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PriceUnavailableCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Text(
            stringResource(R.string.paywall_price_unavailable),
            color = OnSurfaceColor.copy(alpha = 0.7f),
            fontSize = 13.sp,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun RewardedAdCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PlayCircleOutline,
                contentDescription = null,
                tint = SecondaryColor,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.paywall_watch_ad),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.paywall_watch_ad_desc),
                    color = OnSurfaceColor.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }
    }
}
