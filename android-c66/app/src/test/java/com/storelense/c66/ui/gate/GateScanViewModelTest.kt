package com.storelense.c66.ui.gate

import android.content.Context
import app.cash.turbine.test
import com.google.gson.Gson
import com.storelense.c66.data.remote.dto.EpcsByEanResponse
import com.storelense.c66.data.repository.AuthRepository
import com.storelense.c66.data.repository.GateRepository
import com.storelense.c66.data.repository.Result
import com.storelense.c66.rfid.C66RfidReader
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GateScanViewModelTest {

    private val gateRepo: GateRepository = mockk(relaxed = true)
    private val authRepo: AuthRepository = mockk(relaxed = true)
    private val rfid: C66RfidReader = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val gson = Gson()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: GateScanViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = GateScanViewModel(gateRepo, authRepo, rfid, gson, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when QR scanned, it should resolve EANs and update state`() = runTest {
        val rawQr = """{"billRef":"TEST-001","items":[{"ean":"123","qty":2}]}"""
        val mockResponse = EpcsByEanResponse(
            ean = "123",
            sku = "SKU-123",
            productName = "Product 123",
            epcs = listOf("EPC1", "EPC2", "EPC3")
        )

        coEvery { gateRepo.resolveEan("123") } returns Result.Success(mockResponse)

        viewModel.onQrScanned(rawQr)

        viewModel.state.test {
            var state = awaitItem()
            // It might take a few emissions to get to the final state due to the async call
            // We wait until isResolvingBill is false and billRef is set
            while (state.isResolvingBill || state.billRef != "TEST-001") {
                state = awaitItem()
            }
            
            assertEquals("TEST-001", state.billRef)
            assertEquals(1, state.items.size)
            assertEquals("Product 123", state.items[0].productName)
            assertEquals(3, state.items[0].validEpcs.size)
        }
    }

    @Test
    fun `when invalid QR scanned, it should show error`() = runTest {
        viewModel.onQrScanned("invalid-json")

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.error?.contains("Invalid QR") == true)
        }
    }

    @Test
    fun `when EPC scanned, it should match against bill items`() = runTest {
        // Setup a bill with one item
        val rawQr = """{"billRef":"TEST-001","items":[{"ean":"123","qty":1}]}"""
        val mockResponse = EpcsByEanResponse(
            ean = "123",
            sku = "SKU-123",
            productName = "Product 123",
            epcs = listOf("EPC1")
        )
        coEvery { gateRepo.resolveEan("123") } returns Result.Success(mockResponse)
        
        viewModel.onQrScanned(rawQr)
        testDispatcher.scheduler.advanceUntilIdle()

        // Simulate RFID read of the correct EPC
        viewModel.startRfidScan()
        // The ViewModel uses a callback from RFID reader, we need to capture it or just invoke it if we have the mock
        // Since rfid is a relaxed mock, we can capture the lambda passed to startInventory
        val slot = io.mockk.slot<(String) -> Unit>()
        io.mockk.verify { rfid.startInventory(capture(slot)) }
        
        slot.captured.invoke("EPC1")

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(1, state.items[0].matchedEpcs.size)
            assertEquals("EPC1", state.items[0].matchedEpcs[0])
            assertEquals(LineStatus.FULFILLED, state.items[0].status)
        }
    }
}
