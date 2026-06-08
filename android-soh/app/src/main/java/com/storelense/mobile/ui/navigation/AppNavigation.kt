package com.storelense.mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.storelense.mobile.ui.home.HomeScreen
import com.storelense.mobile.ui.inbound.InboundListScreen
import com.storelense.mobile.ui.inbound.InboundResultScreen
import com.storelense.mobile.ui.inbound.InboundScanScreen
import com.storelense.mobile.ui.locator.ItemLocatorScreen
import com.storelense.mobile.ui.login.LoginScreen
import com.storelense.mobile.ui.products.ProductSearchScreen
import com.storelense.mobile.ui.replenish.ReplenishListScreen
import com.storelense.mobile.ui.replenish.ReplenishResultScreen
import com.storelense.mobile.ui.replenish.ReplenishTaskScreen
import com.storelense.mobile.ui.soh.ScanScreen
import com.storelense.mobile.ui.soh.SessionListScreen
import com.storelense.mobile.ui.soh.SohResultScreen
import com.storelense.mobile.ui.spotcount.QuickSpotCountScreen

object Routes {
    const val LOGIN            = "login"
    const val HOME             = "home"
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
    const val ITEM_LOCATOR     = "item_locator"
    const val ITEM_LOCATOR_EPC = "item_locator/{epc}"
    const val SPOT_COUNT       = "spot_count"

    fun sohScan(sessionId: String)      = "soh_scan/$sessionId"
    fun sohResult(sessionId: String)    = "soh_result/$sessionId"
    fun inboundScan(shipmentId: String) = "inbound_scan/$shipmentId"
    fun inboundResult(received: Int, expected: Int, shortage: Int) =
        "inbound_result/$received/$expected/$shortage"
    fun replenishTask(taskId: String)   = "replenish_task/$taskId"
    fun replenishDone(taskId: String)   = "replenish_done/$taskId"
    fun itemLocator(epc: String)        = "item_locator/$epc"
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
            HomeScreen(
                onSoh           = { nav.navigate(Routes.SOH_LIST) },
                onInbound       = { nav.navigate(Routes.INBOUND_LIST) },
                onReplenish     = { nav.navigate(Routes.REPLENISH_LIST) },
                onProductSearch = { nav.navigate(Routes.PRODUCT_SEARCH) },
                onItemLocator   = { nav.navigate(Routes.ITEM_LOCATOR) },
                onSpotCount     = { nav.navigate(Routes.SPOT_COUNT) },
                onLogout        = {
                    nav.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
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
                sessionId = it.arguments!!.getString("sessionId")!!,
                onComplete = { id -> nav.navigate(Routes.sohResult(id)) { popUpTo(Routes.SOH_LIST) } },
                onBack    = { nav.popBackStack() }
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
            ProductSearchScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.ITEM_LOCATOR) {
            ItemLocatorScreen(onBack = { nav.popBackStack() })
        }

        composable(
            Routes.ITEM_LOCATOR_EPC,
            arguments = listOf(navArgument("epc") { type = NavType.StringType })
        ) {
            ItemLocatorScreen(
                initialEpc = it.arguments!!.getString("epc") ?: "",
                onBack     = { nav.popBackStack() }
            )
        }

        composable(Routes.SPOT_COUNT) {
            QuickSpotCountScreen(onBack = { nav.popBackStack() })
        }
    }
}
