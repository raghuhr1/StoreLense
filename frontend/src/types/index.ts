// ─── Features ─────────────────────────────────────────────────────────────────
export type Feature =
  | 'INVENTORY' | 'INBOUND' | 'REPLENISHMENT' | 'CYCLE_COUNT'
  | 'TRANSFERS' | 'ANALYTICS' | 'SALES' | 'DEVICES' | 'ERP_INTEGRATION'

export interface StoreFeature { feature: Feature; enabled: boolean }

// ─── Auth ─────────────────────────────────────────────────────────────────────
export type Role = 'ADMIN' | 'STORE_MANAGER' | 'STORE_ASSOCIATE' | 'REFILL_ASSOCIATE'

export interface AuthUser {
  userId: string
  username: string
  role: Role
  storeId: string | null
}

export interface LoginRequest  { username: string; password: string }
export interface LoginResponse {
  accessToken: string; refreshToken: string; tokenType: string
  expiresIn: number; userId: string; username: string; role: Role; storeId: string | null
}

// ─── API wrapper ──────────────────────────────────────────────────────────────
export interface ApiResponse<T> { success: boolean; code: string; message: string | null; data: T }
export interface PageResponse<T> {
  content: T[]; page: number; size: number; totalElements: number; totalPages: number; last: boolean
}

// ─── Stores ───────────────────────────────────────────────────────────────────
export interface Store {
  id: string; storeCode: string; name: string; addressLine1: string | null
  city: string | null; stateProvince: string | null; postalCode: string | null; countryCode: string
  timezone: string; active: boolean; erpStoreCode: string | null; createdAt: string
}

export interface Zone {
  id: string; storeId: string; zoneCode: string; name: string
  zoneType: string; displayOrder: number; active: boolean
}

export interface RfidReader {
  id: string; storeId: string; zoneId: string | null; readerCode: string
  readerType: 'fixed' | 'handheld' | 'bluetooth_sled'
  ipAddress: string | null; firmwareVersion: string | null
  antennaCount: number; txPowerDbm: number | null; active: boolean
  lastHeartbeatAt: string | null
}

// ─── Products ─────────────────────────────────────────────────────────────────
export interface Product {
  id: string; sku: string; name: string; description: string | null
  categoryId: string | null; brand: string | null; unitOfMeasure: string
  rfidEnabled: boolean; active: boolean; primaryEan: string | null; createdAt: string
}

// ─── Users ────────────────────────────────────────────────────────────────────
export interface User {
  id: string; username: string; email: string; firstName: string; lastName: string
  storeId: string | null; active: boolean; roles: Role[]
  lastLoginAt: string | null; createdAt: string
}

// ─── Inventory ───────────────────────────────────────────────────────────────
export interface InventoryState {
  id: string; storeId: string; productId: string; zoneId: string | null
  quantityOnHand: number; quantityExpected: number
  accuracyPct: number | null; lastCountedAt: string | null
}

export interface EpcRegistryEntry {
  id: string; epc: string; storeId: string; productId: string | null; zoneId: string | null
  status: 'in_store' | 'sold' | 'missing' | 'damaged' | 'transferred' | 'inbound' | 'in_transit' | 'unlocated'
  lastSeenAt: string | null; firstSeenAt: string | null
}

export interface InboundEpcRow {
  epc: string; productId: string | null; sku: string | null; productName: string | null
  firstSeenAt: string | null; lastSeenAt: string | null
}

export interface PutawayResponse {
  movedCount: number; skippedCount: number; message: string
}

export interface InboundShipment {
  id: string; storeId: string; dcCode: string | null; referenceNumber: string | null
  status: string; expectedAt: string | null; receivedAt: string | null
  lineCount: number; notes: string | null; createdAt: string
}

export interface ZoneParLevel {
  id: string; storeId: string; zoneId: string; productId: string
  parQty: number; minQty: number; active: boolean
  createdAt: string; updatedAt: string
}

export type RollupStatus = 'critical' | 'low' | 'ok' | 'surplus'

export interface ReplenishmentRule {
  id: string; storeId: string; triggerStatus: 'low' | 'critical'
  priority: number; active: boolean; createdAt: string; updatedAt: string
}

export interface ZoneHealthSummary {
  zoneId: string; zoneName: string | null
  criticalCount: number; lowCount: number; okCount: number; surplusCount: number
  totalProducts: number
}

export interface ProductFrequencyRow {
  productId: string; sku: string | null; productName: string | null
  refillCount: number; totalUnitsRequested: number; lastRefillAt: string | null
}

export interface ZoneTrendPoint {
  day: string; criticalCount: number; lowCount: number; okCount: number; surplusCount: number
}

export interface ReplenishmentSuggestion {
  storeId: string; zoneId: string; zoneName: string | null
  productId: string; sku: string | null; productName: string | null
  scannedQty: number; parQty: number; shortage: number
  status: 'critical' | 'low'; priority: number; hasOpenTask: boolean
}

export interface ZoneScanRollupRow {
  id: string | null; storeId: string; zoneId: string; zoneName: string | null
  productId: string; sku: string | null; productName: string | null
  sessionId: string | null
  scannedQty: number; parQty: number; minQty: number; variance: number
  status: RollupStatus; computedAt: string
}

// ─── SOH / Cycle Count ────────────────────────────────────────────────────────
export type SessionStatus = 'created' | 'in_progress' | 'completed' | 'cancelled' | 'failed'
                          | 'paused' | 'uploaded' | 'reconciled' | 'closed'
export type SessionType   = 'manual' | 'scheduled' | 'full_store' | 'spot_check' | 'cycle_count'

export interface SohSession {
  id: string; storeId: string; zoneId: string | null; sessionType: SessionType
  status: SessionStatus; startedBy: string; startedAt: string
  completedAt: string | null; totalEpcReads: number; uniqueEpcCount: number; notes: string | null
  cycleCountId: string | null
  locationCode: string | null
  sectionCode: string | null
  pausedAt: string | null
  resumedAt: string | null
  uploadedAt: string | null
  reconciledAt: string | null
  closedAt: string | null
  result: SohResult | null
}

export interface SohResult {
  id: string; sessionId: string; storeId: string
  totalProductsCounted: number; totalUnitsCounted: number; totalUnitsExpected: number
  accuracyPct: number; varianceCount: number; overcountItems: number; undercountItems: number
  resultGeneratedAt: string
  floorUnitsCounted: number
  floorUnitsExpected: number
  floorVariance: number
  backroomUnitsCounted: number
  backroomUnitsExpected: number
  backroomVariance: number
  totalStoreVariance: number
}

export type CycleCountStatus = 'DRAFT' | 'RUNNING' | 'COMPLETED' | 'UPLOADED' | 'RECONCILED' | 'CLOSED'

export interface CycleCount {
  id: string
  storeId: string
  countDate: string
  status: CycleCountStatus
  createdBy: string
  notes: string | null
  createdAt: string
  updatedAt: string
  sessions: SohSession[]
}

export interface StoreLocation {
  id: string
  storeId: string
  locationCode: string
  sectionCode: string | null
  displayName: string
  sortOrder: number
  isActive: boolean
  createdAt: string
}

export interface AntennaLocationMapping {
  id: string
  storeId: string
  readerId: string
  antennaPort: number
  locationCode: string
  sectionCode: string | null
  displayName: string | null
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface CycleCountReconciliation {
  id: string
  cycleCountId: string
  sessionId: string | null
  batchId: string
  storeId: string
  runAt: string
  totalExpected: number
  totalScanned: number
  matchedCount: number
  missingCount: number
  extraCount: number
  accuracyPct: number | null
  status: 'RUNNING' | 'COMPLETED' | 'FAILED' | 'PENDING_APPROVAL' | 'APPROVED'
  floorExpected: number
  floorScanned: number
  floorMissing: number
  backroomExpected: number
  backroomScanned: number
  backroomMissing: number
  reviewerId: string | null
  approvedAt: string | null
  createdAt: string
}

export interface ReconciliationItemWithLocation extends ReconciliationItem {
  locationCode: string | null
  sectionCode: string | null
}

// ─── Refill / Receiving ───────────────────────────────────────────────────────
export type TaskStatus = 'pending' | 'assigned' | 'in_progress' | 'completed' | 'cancelled'
export type TaskType   = 'replenishment' | 'urgency' | 'cycle_count'
export type TaskSource = 'manual' | 'soh_trigger' | 'scheduled' | 'erp'

export interface TaskItem {
  id: string; productId: string; zoneId: string | null
  requestedQuantity: number; fulfilledQuantity: number
  status: 'pending' | 'partial' | 'fulfilled' | 'skipped'
}

export interface RefillTask {
  id: string; storeId: string; taskType: TaskType; status: TaskStatus
  priority: number; source: TaskSource; dueDate: string | null; notes: string | null
  createdBy: string; createdAt: string; completedAt: string | null; items: TaskItem[]
}

// ─── Transfers ────────────────────────────────────────────────────────────────
export interface Transfer {
  id: string; fromStoreId: string; toStoreId: string
  productId: string; epc: string; quantity: number
  status: 'pending' | 'in_transit' | 'received' | 'cancelled'
  initiatedAt: string; receivedAt: string | null; notes: string | null
}

// ─── ERP Import ───────────────────────────────────────────────────────────────
export interface ErpImportBatch {
  id: string
  storeId: string
  sourceType: 'FILE' | 'S3'
  filePath: string | null
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  totalRows: number
  resolvedRows: number
  unresolvedRows: number
  importedAt: string | null
  errorMessage: string | null
  createdAt: string
}

export interface ErpSohSnapshot {
  id: string
  ean: string
  expectedQty: number
  zoneRegion: string | null
  resolutionStatus: 'RAW' | 'RESOLVED' | 'PARTIAL' | 'UNRESOLVED'
  createdAt: string
}

export interface ErpBatchDetail {
  batch: ErpImportBatch
  unresolvedCount: number
}

// ─── Reconciliation ───────────────────────────────────────────────────────────
export interface ReconciliationSession {
  id: string
  sessionId: string | null
  cycleCountId: string | null
  batchId: string | null
  storeId: string
  runAt: string
  totalExpected: number
  totalScanned: number
  matchedCount: number
  missingCount: number
  extraCount: number
  accuracyPct: number | null
  status: 'RUNNING' | 'COMPLETED' | 'FAILED' | 'PENDING_APPROVAL' | 'APPROVED'
  createdAt: string
}

export interface ReconciliationItem {
  id: string
  epc: string
  ean: string | null
  status: 'MATCH' | 'MISSING' | 'EXTRA'
  expectedQty: number
  scannedQty: number
}

// ─── Reports / KPI ────────────────────────────────────────────────────────────
export interface KpiDaily {
  id: string; storeId: string; kpiDate: string
  inventoryAccuracyPct: number | null; sohSessionsCount: number
  refillTasksCreated: number; refillTasksCompleted: number
  refillCompletionRatePct: number | null; avgRefillTimeMinutes: number | null
  totalEpcReads: number; uniqueSkusCounted: number; varianceItemsCount: number
}


// ─── RFID Ledger ──────────────────────────────────────────────────────────────
export interface SkuLedgerRow {
  productId:   string
  inStore:     number
  sold:        number
  missing:     number
  damaged:     number
  transferred: number
  total:       number
  lastSeenAt:  string | null
}

export interface EpcLedgerRow {
  epc:         string
  productId:   string | null
  sku:         string | null
  productName: string | null
  zoneName:    string | null
  status:      'in_store' | 'sold' | 'missing' | 'damaged' | 'transferred' | 'inbound' | 'in_transit' | 'unlocated'
  lastSeenAt:  string | null
  firstSeenAt: string | null
}
