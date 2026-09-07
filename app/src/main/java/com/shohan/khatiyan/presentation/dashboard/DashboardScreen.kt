package com.shohan.khatiyan.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.shohan.khatiyan.core.BnDates
import com.shohan.khatiyan.core.Money
import com.shohan.khatiyan.di.AppContainer
import com.shohan.khatiyan.domain.model.DueItem
import com.shohan.khatiyan.domain.model.DueStatus
import com.shohan.khatiyan.domain.model.InsightTone
import com.shohan.khatiyan.domain.model.ObligationKind
import com.shohan.khatiyan.presentation.common.QuickAction
import com.shohan.khatiyan.presentation.common.QuickAddSheet
import com.shohan.khatiyan.presentation.common.QuickPaymentSheet
import com.shohan.khatiyan.presentation.navigation.Routes
import com.shohan.khatiyan.ui.charts.BarGroup
import com.shohan.khatiyan.ui.charts.ChartSlice
import com.shohan.khatiyan.ui.charts.DonutChart
import com.shohan.khatiyan.ui.charts.GroupedBarChart
import com.shohan.khatiyan.ui.components.AppCard
import com.shohan.khatiyan.ui.components.KhatiyanScaffold
import com.shohan.khatiyan.ui.components.PillTone
import com.shohan.khatiyan.ui.components.SectionTitle
import com.shohan.khatiyan.ui.components.StatTile
import com.shohan.khatiyan.ui.components.StatusPill
import com.shohan.khatiyan.ui.theme.ChartPalette
import com.shohan.khatiyan.ui.theme.KhatiyanBrand
import com.shohan.khatiyan.utilities.containerFactory

@Composable
fun DashboardScreen(navController: NavController, container: AppContainer) {
    val vm: DashboardViewModel = viewModel(factory = containerFactory { DashboardViewModel(it) })
    val state by vm.state.collectAsStateWithLifecycle()
    var showQuickAdd by remember { mutableStateOf(false) }
    var showQuickPayment by remember { mutableStateOf(false) }
    val snackbar = androidx.compose.material3.SnackbarHostState()
    val snap = state.snapshot
    var message by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); message = null }
    }

    KhatiyanScaffold(
        title = "খতিয়ান",
        snackbarHostState = snackbar,
        actions = { com.shohan.khatiyan.ui.components.SearchIconButton { navController.navigate(Routes.SEARCH) } },
        bottomBar = {},
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showQuickAdd = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large,
            ) { Icon(Icons.Filled.Add, contentDescription = "দ্রুত যোগ করুন") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { GreetingRow(snap?.userName.orEmpty()) }

            item {
                HeroDebtCard(
                    snapshot = snap,
                    onOpenHisab = { navController.navigate(Routes.HISAB) },
                )
            }

            item {
                Column {
                    SectionTitle("আজকের সারাংশ")
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(
                                "আজকের আয়",
                                money(snap?.todayIncomePaisa ?: 0),
                                Modifier.weight(1f),
                                valueColor = KhatiyanBrand.Success,
                            )
                            StatTile(
                                "আজকের ব্যয়",
                                money(snap?.todayExpensePaisa ?: 0),
                                Modifier.weight(1f),
                                valueColor = KhatiyanBrand.Danger,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(
                                "আজ পরিশোধ",
                                money(snap?.todayRepaidPaisa ?: 0),
                                Modifier.weight(1f),
                            )
                            StatTile(
                                "আজ দিতে হবে",
                                money(snap?.todayDuePaisa ?: 0),
                                Modifier.weight(1f),
                                valueColor = if ((snap?.todayDuePaisa ?: 0) > 0) KhatiyanBrand.Warning else KhatiyanBrand.Ink,
                            )
                        }
                    }
                }
            }

            item {
                UpcomingCard(
                    items = snap?.upcoming ?: emptyList(),
                    dueTodayCount = snap?.dueTodayCount ?: 0,
                    dueWeekCount = snap?.dueWeekCount ?: 0,
                    dueWeekPaisa = snap?.dueWeekPaisa ?: 0,
                    overdueCount = snap?.overdueCount ?: 0,
                    overduePaisa = snap?.overduePaisa ?: 0,
                    symbol = snap?.symbol ?: "৳",
                    onOpen = { item -> openDueItem(navController, item) },
                )
            }

            item {
                Column {
                    SectionTitle("আর্থিক চিত্র — এই মাস")
                    AppCard {
                        val income = snap?.monthIncomePaisa ?: 0L
                        val expense = snap?.monthExpensePaisa ?: 0L
                        val net = income - expense
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("নেট ক্যাশ ফ্লো", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    money(net),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (net >= 0) KhatiyanBrand.Success else KhatiyanBrand.Danger,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "আয় ${money(income)} · ব্যয় ${money(expense)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(
                                        if (net >= 0) KhatiyanBrand.SuccessBg else KhatiyanBrand.DangerBg,
                                        MaterialTheme.shapes.medium,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (net >= 0) com.shohan.khatiyan.ui.icons.KhatiyanIcons.TrendingUp else com.shohan.khatiyan.ui.icons.KhatiyanIcons.TrendingDown,
                                    contentDescription = null,
                                    tint = if (net >= 0) KhatiyanBrand.Success else KhatiyanBrand.Danger,
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "গত ৬ মাসের ধারা",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        val points = snap?.trend ?: emptyList()
                        GroupedBarChart(
                            groups = points.map {
                                BarGroup(
                                    BnDates.MONTHS_SHORT[it.yearMonth.monthValue - 1],
                                    listOf(it.incomePaisa, it.expensePaisa, it.repaidPaisa),
                                )
                            },
                            seriesColors = listOf(
                                KhatiyanBrand.Primary,
                                Color(0xFFC05746),
                                KhatiyanBrand.GoldBright,
                            ),
                            height = 140.dp,
                        )
                        Row(
                            modifier = Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LegendDot("আয়", KhatiyanBrand.Primary)
                            LegendDot("ব্যয়", Color(0xFFC05746))
                            LegendDot("পরিশোধ", KhatiyanBrand.GoldBright)
                        }
                    }
                }
            }

            item {
                Column {
                    SectionTitle("বকেয়ার বণ্টন")
                    AppCard {
                        val slices = snap?.debtSlices ?: emptyList()
                        if (slices.isEmpty() || (snap?.totalOutstandingPaisa ?: 0) <= 0) {
                            Text(
                                "এখন কোনো বকেয়া নেই — নিশ্চিত থাকুন। 👌",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DonutChart(
                                    slices = slices.mapIndexed { i, s -> ChartSlice(s.name, s.outstandingPaisa, ChartPalette[i % ChartPalette.size]) },
                                    modifier = Modifier.size(128.dp),
                                    centerTitle = money(snap?.totalOutstandingPaisa ?: 0),
                                    centerSubtitle = "মোট বকেয়া",
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    slices.forEachIndexed { i, s ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                Modifier
                                                    .size(10.dp)
                                                    .background(ChartPalette[i % ChartPalette.size], MaterialTheme.shapes.small),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                s.kind.labelBn,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(money(s.outstandingPaisa), style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val insights = snap?.insights ?: emptyList()
            if (insights.isNotEmpty()) {
                item {
                    Column {
                        SectionTitle("আপনার জন্য")
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            insights.forEach { insight ->
                                val tone = when (insight.tone) {
                                    InsightTone.DANGER -> PillTone.DANGER
                                    InsightTone.WARN -> PillTone.WARNING
                                    InsightTone.GOOD -> PillTone.SUCCESS
                                    InsightTone.INFO -> PillTone.INFO
                                }
                                AppCard {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .background(tone.bg, MaterialTheme.shapes.small),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(tone.glyph, color = tone.fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(insight.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                QuickActionsGrid(
                    onAction = { action ->
                        when (action) {
                            QuickAction.PAYMENT -> showQuickPayment = true
                            else -> handleQuickAction(navController, action)
                        }
                    },
                )
            }
        }
    }

    QuickAddSheet(
        visible = showQuickAdd,
        onDismiss = { showQuickAdd = false },
        onAction = { action ->
            if (action == QuickAction.PAYMENT) showQuickPayment = true else handleQuickAction(navController, action)
        },
    )
    QuickPaymentSheet(
        container = container,
        visible = showQuickPayment,
        onDismiss = { showQuickPayment = false },
        onDone = { msg ->
            showQuickPayment = false
            vm.refresh()
            message = msg
        },
    )
}

private fun money(paisa: Long): String = Money.format(paisa)

private fun handleQuickAction(navController: NavController, action: QuickAction) {
    when (action) {
        QuickAction.SHOP_CREDIT -> navController.navigate(Routes.SHOPS)
        QuickAction.LOAN -> navController.navigate(Routes.loanEdit())
        QuickAction.EMI -> navController.navigate(Routes.emiEdit())
        QuickAction.PERSONAL_DEBT -> navController.navigate(Routes.PEOPLE)
        QuickAction.INCOME -> navController.navigate(Routes.incomeEdit())
        QuickAction.EXPENSE -> navController.navigate(Routes.expenseEdit())
        QuickAction.PAYMENT -> Unit
    }
}

private fun openDueItem(navController: NavController, item: DueItem) {
    when (item.kind) {
        ObligationKind.SHOP -> navController.navigate(Routes.shopDetail(item.entityId))
        ObligationKind.LOAN -> navController.navigate(Routes.loanDetail(item.entityId))
        ObligationKind.EMI -> navController.navigate(Routes.emiDetail(item.entityId))
        ObligationKind.PERSONAL -> navController.navigate(Routes.personDetail(item.entityId))
    }
}

@Composable
private fun GreetingRow(name: String) {
    val hour = remember { java.time.LocalTime.now().hour }
    val greet = remember(hour) {
        when {
            hour < 5 -> "রাত জেগে হিসাব? 🌙"
            hour < 12 -> "সুপ্রভাত"
            hour < 17 -> "শুভ দুপুর"
            hour < 20 -> "শুভ সন্ধ্যা"
            else -> "শুভ রাত্রি"
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (name.isBlank()) "$greet!" else "$greet, $name",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                BnDates.formatLong(BnDates.today()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(KhatiyanBrand.Deep, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.shohan.khatiyan.R.drawable.ic_launcher_foreground),
                contentDescription = "খতিয়ান",
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(color, MaterialTheme.shapes.small))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeroDebtCard(
    snapshot: com.shohan.khatiyan.domain.model.DashboardSnapshot?,
    onOpenHisab: () -> Unit,
) {
    androidx.compose.material3.Surface(
        onClick = onOpenHisab,
        shape = MaterialTheme.shapes.large,
        color = KhatiyanBrand.Deep,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
        Text(
            "বর্তমান মোট বাকি",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFCFE8D9),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            money(snapshot?.totalOutstandingPaisa ?: 0),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ObligationKind.entries.forEach { kind ->
                val paisa = snapshot?.outstandingByKind?.get(kind) ?: 0L
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.10f), MaterialTheme.shapes.small)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Text(
                        shortKind(kind),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD9E9DF),
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        money(paisa),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "বিস্তারিত হিসাব দেখতে চাপুন →",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFD9E9DF),
        )
        }
    }
}

private fun shortKind(kind: ObligationKind): String = when (kind) {
    ObligationKind.SHOP -> "দোকান"
    ObligationKind.LOAN -> "লোন"
    ObligationKind.EMI -> "EMI"
    ObligationKind.PERSONAL -> "ধার"
}

@Composable
private fun UpcomingCard(
    items: List<DueItem>,
    dueTodayCount: Int,
    dueWeekCount: Int,
    dueWeekPaisa: Long,
    overdueCount: Int,
    overduePaisa: Long,
    symbol: String,
    onOpen: (DueItem) -> Unit,
) {
    Column {
        SectionTitle("সামনের পেমেন্ট")
        AppCard {
            if (items.isEmpty()) {
                Text(
                    "সামনের ৪৫ দিনে কোনো নির্ধারিত পেমেন্ট নেই।",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniStat("আজ", dueTodayCount, KhatiyanBrand.Danger, Modifier.weight(1f))
                        MiniStat("৭ দিনে", dueWeekCount, KhatiyanBrand.Warning, Modifier.weight(1f))
                        MiniStat("ওভারডিউ", overdueCount, if (overdueCount > 0) KhatiyanBrand.Danger else KhatiyanBrand.InkMuted, Modifier.weight(1f))
                    }
                    items.take(6).forEach { item ->
                        DueItemRow(item, onOpen)
                    }
                }
            }
            if (dueWeekCount > 0 || overdueCount > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "আগামী ৭ দিনে মোট ${Money.format(dueWeekPaisa, symbol)} · ওভারডিউ ${Money.format(overduePaisa, symbol)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.08f), MaterialTheme.shapes.small)
            .padding(8.dp),
    ) {
        Text(com.shohan.khatiyan.utilities.BnText.toBnDigits(count.toString()), style = MaterialTheme.typography.titleMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DueItemRow(item: DueItem, onOpen: (DueItem) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(item) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(40.dp)
                .background(
                    when (item.status) {
                        DueStatus.OVERDUE -> KhatiyanBrand.Danger
                        DueStatus.DUE_TODAY -> KhatiyanBrand.Warning
                        DueStatus.UPCOMING -> KhatiyanBrand.Primary
                    },
                    MaterialTheme.shapes.small,
                ),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.entityLabel, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${item.detailLabel} · ${BnDates.relativeCompact(item.dueDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            Money.format(item.amountPaisa),
            style = MaterialTheme.typography.titleMedium,
            color = when (item.status) {
                DueStatus.OVERDUE -> KhatiyanBrand.Danger
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun QuickActionsGrid(onAction: (QuickAction) -> Unit) {
    Column {
        SectionTitle("দ্রুত কাজ")
        val actions = listOf(
            QuickAction.SHOP_CREDIT, QuickAction.LOAN, QuickAction.EMI,
            QuickAction.PERSONAL_DEBT, QuickAction.INCOME, QuickAction.EXPENSE, QuickAction.PAYMENT,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            actions.chunked(3).forEach { rowActions ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowActions.forEach { action ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onAction(action) }
                                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(action.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                action.labelBn.removePrefix("নতুন ").removeSuffix(" যোগ করুন"),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                    repeat(3 - rowActions.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
