package com.storelense.mobile.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.storelense.mobile.ui.cyclecount.CycleCountDetailScreen
import com.storelense.mobile.ui.cyclecount.CycleCountListScreen
import com.storelense.mobile.ui.home.HomeScreen
import com.storelense.mobile.ui.home.ExpertHomeScreen
import com.storelense.mobile.ui.home.WorkflowsScreen
import com.storelense.mobile.ui.inbound.InboundListScreen
import com.storelense.mobile.ui.inbound.InboundResultScreen
import com.storelense.mobile.ui.inbound.InboundScanScreen
import com.storelense.mobile.ui.locator.GeigerLocatorScreen
import com.storelense.mobile.ui.locator.ItemLocatorScreen
import com.storelense.mobile.ui.login.LoginScreen
import com.storelense.mobile.ui.products.InventoryEpcsScreen
import com.storelense.mobile.ui.products.ProductFinderScreen
import com.storelense.mobile.ui.products.ProductSearchScreen
import com.storelense.mobile.ui.replenish.ReplenishListScreen
import com.storelense.mobile.ui.exceptions.ExceptionsListScreen
import com.storelense.mobile.ui.exceptions.ExceptionsScreen
import com.storelense.mobile.ui.exceptions.GhostAnalysisScreen
import com.storelense.mobile.ui.exceptions.MissingEpcScreen
import com.storelense.mobile.ui.transfer.TransferOutScreen
import com.storelense.mobile.ui.transfer.TransferReceiveScreen
import com.storelense.mobile.ui.replenish.ReplenishResultScreen
import com.storelense.mobile.ui.replenish.ReplenishTaskScreen
import com.storelense.mobile.ui.settings.DeviceInfoScreen
import com.storelense.mobile.ui.settings.ReaderSettingsScreen
import com.storelense.mobile.ui.settings.SettingsScreen
import com.storelense.mobile.ui.settings.SyncSettingsScreen
import com.storelense.mobile.ui.soh.ScanModeScreen
import com.storelense.mobile.ui.soh.ScanScreen
import com.storelense.mobile.ui.soh.SessionListScreen
import com.storelense.mobile.ui.soh.SohResultScreen
import com.storelense.mobile.ui.spotcount.QuickSpotCountScreen
import com.storelense.mobile.ui.sync.SyncStatusScreen
import com.storelense.mobile.ui.tagitems.TagItemsScreen

object Routes {
    const val LOGIN            = "login"
    const val HOME             = "home"
    const val WORKFLOWS        = "workflows"
    const val SOH_LIST         = "soh_list"
    const val SOH_SCAN         = "soh_scan/{sessionId}"
    const val SOH_RESULT       = "soh_result/{sessionId}"
    const val INBOUND_LIST     = "inbound_list"
    const val INBOUND_SCAN     = "inbound_scan/{shipmentId}"
    const val INBOUND_RESULT   = "inbound_result/{received}/{expected}/{shortage}"
    const val REPLENISH_LIST   = "replenish_list"
    const val REPLENISH_TASK   = "replenish_task/{taskId}"
    const val REPLENISH_DONE   = "replenish_done/{taskId}"
    const val PRODUCT_SEARCH   = "product_search"
    const val PRODUCT_FINDER   = "product_finder"
    const val ITEM_LOCATOR     = "item_locator"
    const val ITEM_LOCATOR_EPC = "item_locator/{epc}"
    const val SPOT_COUNT       = "spot_count"
    const val SCAN_MODE        = "scan_mode"

    // ── Block 14 ─────────────────────────────────────────────────────────
    const val GEIGER_LOCATE    = "geiger_locate/{epc}"
    const val TRANSFER_OUT     = "transfer_out"
    const val TRANSFER_RECEIVE = "transfer_receive/{transferId}"
    const val EXCEPTIONS       = "exceptions"
    const val EXCEPTIONS_LIST  = "exceptions_list/{type}"
    const val GHOST_ANALYSIS   = "ghost_analysis/{epc}"
    const val MISSING_EPC      = "missing_epc/{epc}"
    const val INVENTORY_EPCS   = "inventory_epcs/{sku}"
    const val SYNC_STATUS      = "sync_status"
    const val SETTINGS         = "settings"
    const val SETTINGS_READER  = "settings/reader"
    const val SETTINGS_DEVICE  = "settings/device"
    const val SETTINGS_SYNC    = "settings/sync"
    const val TAG_ITEMS        = "tag_items"

    // ── Cycle Count ───────────────────────────────────────────────────────
    const val CYCLE_COUNT_LIST   = "cycle_count_list"
    const val CYCLE_COUNT_DETAIL = "cycle_count_detail/{countId}"
    fun cycleCountDetail(countId: String)  = "cycle_count_detail/$countId"

    // ── Route builder helpers ─────────────────────────────────────────────
    fun sohScan(sessionId: String)         = "soh_scan/$sessionId"
    fun sohResult(sessionId: String)       = "soh_result/$sessionId"
    fun inboundScan(shipmentId: String)    = "inbound_scan/$shipmentId"
    fun inboundResult(received: Int, expected: Int, shortage: Int) =
        "inbound_result/$received/$expected/$shortage"
    fun replenishTask(taskId: String)      = "replenish_task/$taskId"
    fun replenishDone(taskId: String)      = "replenish_done/$taskId"
    fun itemLocator(epc: String)           = "item_locator/$epc"
    fun geigerLocate(epc: String)          = "geiger_locate/$epc"
    fun transferReceive(transferId: String) = "transfer_receive/$transferId"
    fun exceptionsList(type: String)       = "exceptions_list/$type"
    fun ghostAnalysis(epc: String)         = "ghost_analysis/$epc"
    fun missingEpc(epc: String)            = "missing_epc/$epc"
    fun inventoryEpcs(sku: String)         = "inventory_epcs/$sku"
}

@Composable
fun AppNavigation() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.LOGIN) {

        composable(Routes.LOGIN) {
            LoginScreen(onLoginSuccess = {
                nav.navigate(Routes.HOME) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }

        composable(Routes.HOME) {
            // Switch to ExpertHomeScreen for a better UX, or keep HomeScreen for the old version
            ExpertHomeScreen(
                onSoh         = { nav.navigate(Routes.SCAN_MODE) },
                onCycleCount  = { nav.navigate(Routes.CYCLE_COUNT_LIST) },
                onReplenish   = { nav.navigate(Routes.REPLENISH_LIST) },
                onTransferOut = { nav.navigate(Routes.TRANSFER_OUT) },
                onItemLocator = { nav.navigate(Routes.PRODUCT_FINDER) },
                onExceptions  = { nav.navigate(Routes.EXCEPTIONS) },
                onSettings    = { nav.navigate(Routes.SETTINGS) },
                onWorkflows   = { nav.navigate(Routes.WORKFLOWS) }
            )
        }

        // ── Workflows / Tasks hub ─────────────────────────────────────────
        composable(Routes.WORKFLOWS) {
            WorkflowsScreen(
                onSoh             = { nav.navigate(Routes.SCAN_MODE) },
                onCycleCount      = { nav.navigate(Routes.CYCLE_COUNT_LIST) },
                onInbound         = { nav.navigate(Routes.INBOUND_LIST) },
                onReplenish       = { nav.navigate(Routes.REPLENISH_LIST) },
                onTransferOut     = { nav.navigate(Routes.TRANSFER_OUT) },
                onExceptions      = { nav.navigate(Routes.EXCEPTIONS) },
                onProductSearch   = { nav.navigate(Routes.PRODUCT_FINDER) },
                onTagItems        = { nav.navigate(Routes.TAG_ITEMS) },
                onHome            = { nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } },
                onScan            = { nav.navigate(Routes.SCAN_MODE) },
                onLocate          = { nav.navigate(Routes.PRODUCT_FINDER) },
                onSettings        = { nav.navigate(Routes.SETTINGS) },
                onReaderSettings  = { nav.navigate(Routes.SETTINGS_READER) }
            )
        }

        // ── Cycle Count ───────────────────────────────────────────────────────
        composable(Routes.CYCLE_COUNT_LIST) {
            CycleCountListScreen(
                onCountSelected = { id -> nav.navigate(Routes.cycleCountDetail(id)) },
                onBack          = { nav.popBackStack() }
            )
        }

        composable(
            Routes.CYCLE_COUNT_DETAIL,
            arguments = listOf(navArgument("countId") { type = NavType.StringType })
        ) {
            CycleCountDetailScreen(
                onStartScan = { sessionId ->
                    nav.navigate(Routes.sohScan(sessionId)) {
                        // Keep detail on back stack so user can return to add more locations
                    }
                },
                onBack = { nav.popBackStack() }
            )
        }

        // ── Tag Items ─────────────────────────────────────────────────────────
        composable(Routes.TAG_ITEMS) {
            TagItemsScreen(onBack = { nav.popBackStack() })
        }

        // ── Scan Mode (zone selection → creates session → opens scan) ────
        composable(Routes.SCAN_MODE) {
            ScanModeScreen(
                onBack          = { nav.popBackStack() },
                onSessionReady  = { sessionId ->
                    nav.navigate(Routes.sohScan(sessionId)) {
                        popUpTo(Routes.SCAN_MODE) { inclusive = true }
                    }
                }
            )
        }

        // ── SOH ──────────────────────────────────────────────────────────
        composable(Routes.SOH_LIST) {
            SessionListScreen(
                onSessionSelected = { id -> nav.navigate(Routes.sohScan(id)) },
                onBack            = { nav.popBackStack() }
            )
        }

        composable(
            Routes.SOH_SCAN,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) {
            ScanScreen(
                sessionId  = it.arguments!!.getString("sessionId")!!,
                onComplete = { id -> nav.navigate(Routes.sohResult(id)) { popUpTo(Routes.SOH_LIST) } },
                onBack     = { nav.popBackStack() }
            )
        }

        composable(
            Routes.SOH_RESULT,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) {
            SohResultScreen(
                sessionId = it.arguments!!.getString("sessionId")!!,
                onDone    = { nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } }
            )
        }

        // ── Inbound ───────────────────────────────────────────────────────
        composable(Routes.INBOUND_LIST) {
            InboundListScreen(
                onShipmentSelected = { id -> nav.navigate(Routes.inboundScan(id)) },
                onBack             = { nav.popBackStack() }
            )
        }

        composable(
            Routes.INBOUND_SCAN,
            arguments = listOf(navArgument("shipmentId") { type = NavType.StringType })
        ) {
            InboundScanScreen(
                shipmentId = it.arguments!!.getString("shipmentId")!!,
                onComplete = { r, e, s -> nav.navigate(Routes.inboundResult(r, e, s)) { popUpTo(Routes.INBOUND_LIST) } },
                onBack     = { nav.popBackStack() }
            )
        }

        composable(
            Routes.INBOUND_RESULT,
            arguments = listOf(
                navArgument("received") { type = NavType.IntType },
                navArgument("expected") { type = NavType.IntType },
                navArgument("shortage") { type = NavType.IntType },
            )
        ) {
            InboundResultScreen(
                received = it.arguments!!.getInt("received"),
                expected = it.arguments!!.getInt("expected"),
                shortage = it.arguments!!.getInt("shortage"),
                onDone   = { nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } }
            )
        }

        // ── Replenish ─────────────────────────────────────────────────────
        composable(Routes.REPLENISH_LIST) {
            ReplenishListScreen(
                onTaskSelected = { id -> nav.navigate(Routes.replenishTask(id)) },
                onBack         = { nav.popBackStack() }
            )
        }

        composable(
            Routes.REPLENISH_TASK,
            arguments = listOf(navArgument("taskId") { type = NavType.StringType })
        ) {
            ReplenishTaskScreen(
                taskId     = it.arguments!!.getString("taskId")!!,
                onComplete = { id -> nav.navigate(Routes.replenishDone(id)) { popUpTo(Routes.REPLENISH_LIST) } },
                onBack     = { nav.popBackStack() }
            )
        }

        composable(
            Routes.REPLENISH_DONE,
            arguments = listOf(navArgument("taskId") { type = NavType.StringType })
        ) {
            ReplenishResultScreen(
                taskId = it.arguments!!.getString("taskId")!!,
                onDone = { nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } }
            )
        }

        // ── RFID Tools ────────────────────────────────────────────────────
        composable(Routes.PRODUCT_SEARCH) {
            ProductSearchScreen(
                onBack     = { nav.popBackStack() },
                onViewEpcs = { sku -> nav.navigate(Routes.inventoryEpcs(sku)) },
                onLocate   = { epc -> nav.navigate(Routes.geigerLocate(epc)) }
            )
        }

        composable(Routes.PRODUCT_FINDER) {
            ProductFinderScreen(
                onBack = { nav.popBackStack() }
            )
        }

        composable(Routes.ITEM_LOCATOR) {
            ItemLocatorScreen(
                onBack         = { nav.popBackStack() },
                onGeigerLocate = { epc -> nav.navigate(Routes.geigerLocate(epc)) }
            )
        }

        composable(
            Routes.ITEM_LOCATOR_EPC,
            arguments = listOf(navArgument("epc") { type = NavType.StringType })
        ) {
            ItemLocatorScreen(
                initialEpc     = it.arguments!!.getString("epc") ?: "",
                onBack         = { nav.popBackStack() },
                onGeigerLocate = { epc -> nav.navigate(Routes.geigerLocate(epc)) }
            )
        }

        composable(Routes.SPOT_COUNT) {
            QuickSpotCountScreen(onBack = { nav.popBackStack() })
        }

        // ── Geiger Locate ─────────────────────────────────────────────────
        composable(
            Routes.GEIGER_LOCATE,
            arguments = listOf(navArgument("epc") { type = NavType.StringType })
        ) {
            GeigerLocatorScreen(
                targetEpc = it.arguments!!.getString("epc") ?: "",
                onBack    = { nav.popBackStack() }
            )
        }

        // ── Transfers ─────────────────────────────────────────────────────
        composable(Routes.TRANSFER_OUT) {
            TransferOutScreen(onBack = { nav.popBackStack() })
        }

        composable(
            Routes.TRANSFER_RECEIVE,
            arguments = listOf(navArgument("transferId") { type = NavType.StringType })
        ) {
            TransferReceiveScreen(
                transferId = it.arguments!!.getString("transferId") ?: "",
                onBack     = { nav.popBackStack() }
            )
        }

        // ── Exceptions ────────────────────────────────────────────────────
        composable(Routes.EXCEPTIONS) {
            ExceptionsScreen(
                onBack     = { nav.popBackStack() },
                onCategory = { type -> nav.navigate(Routes.exceptionsList(type)) }
            )
        }

        composable(
            Routes.EXCEPTIONS_LIST,
            arguments = listOf(navArgument("type") { type = NavType.StringType })
        ) {
            ExceptionsListScreen(
                type     = it.arguments!!.getString("type") ?: "",
                onBack   = { nav.popBackStack() },
                onDetail = { itemType, epc ->
                    when (itemType) {
                        "GHOST_TAG"   -> nav.navigate(Routes.ghostAnalysis(epc))
                        "MISSING_EPC" -> nav.navigate(Routes.missingEpc(epc))
                    }
                }
            )
        }

        composable(
            Routes.GHOST_ANALYSIS,
            arguments = listOf(navArgument("epc") { type = NavType.StringType })
        ) {
            GhostAnalysisScreen(
                epc    = it.arguments!!.getString("epc") ?: "",
                onBack = { nav.popBackStack() }
            )
        }

        composable(
            Routes.MISSING_EPC,
            arguments = listOf(navArgument("epc") { type = NavType.StringType })
        ) {
            val missingEpc = it.arguments!!.getString("epc") ?: ""
            MissingEpcScreen(
                epc      = missingEpc,
                onBack   = { nav.popBackStack() },
                onLocate = { nav.navigate(Routes.geigerLocate(missingEpc)) }
            )
        }

        // ── Inventory EPCs ────────────────────────────────────────────────
        composable(
            Routes.INVENTORY_EPCS,
            arguments = listOf(navArgument("sku") { type = NavType.StringType })
        ) {
            InventoryEpcsScreen(
                sku    = it.arguments!!.getString("sku") ?: "",
                onBack = { nav.popBackStack() }
            )
        }

        // ── Sync & Settings ───────────────────────────────────────────────
        composable(Routes.SYNC_STATUS) {
            SyncStatusScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack           = { nav.popBackStack() },
                onReaderSettings = { nav.navigate(Routes.SETTINGS_READER) },
                onDeviceInfo     = { nav.navigate(Routes.SETTINGS_DEVICE) },
                onSyncSettings   = { nav.navigate(Routes.SETTINGS_SYNC) },
                onLogout         = {
                    nav.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.SETTINGS_READER) {
            ReaderSettingsScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.SETTINGS_DEVICE) {
            DeviceInfoScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.SETTINGS_SYNC) {
            SyncSettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderScreen(label: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(label) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text(label)
        }
    }
}
