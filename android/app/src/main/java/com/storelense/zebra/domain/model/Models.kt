package com.storelense.zebra.domain.model

data class User(
    val userId:   String,
    val username: String,
    val role:     String,
    val storeId:  String?,
)

data class Store(
    val id:        String,
    val storeCode: String,
    val name:      String,
    val timezone:  String,
)

data class Zone(
    val id:       String,
    val storeId:  String,
    val zoneCode: String,
    val name:     String,
    val zoneType: String,
    val order:    Int,
)

data class SohSession(
    val id:             String,
    val storeId:        String,
    val zoneId:         String?,
    val sessionType:    String,
    val status:         String,
    val startedAt:      String,
    val completedAt:    String?,
    val totalEpcReads:  Int,
    val uniqueEpcCount: Int,
    val notes:          String?,
)

data class SohResult(
    val accuracyPct:     Double,
    val varianceCount:   Int,
    val unitsCounted:    Int,
    val unitsExpected:   Int,
)

data class RefillTask(
    val id:        String,
    val storeId:   String,
    val taskType:  String,
    val status:    String,
    val priority:  Int,
    val source:    String,
    val dueDate:   String?,
    val notes:     String?,
    val createdAt: String,
    val items:     List<RefillTaskItem>,
)

data class RefillTaskItem(
    val id:                String,
    val taskId:            String,
    val productId:         String,
    val zoneId:            String?,
    val requestedQuantity: Int,
    val fulfilledQuantity: Int,
    val status:            String,
)

data class RfidRead(
    val sessionId:   String,
    val epc:         String,
    val rssi:        Double?,
    val antennaPort: Int?,
    val readAt:      String,
)

enum class SessionType(val label: String) {
    FULL_STORE("Full Store"),
    SPOT_CHECK("Spot Check"),
    MANUAL("Manual"),
    SCHEDULED("Scheduled"),
}
