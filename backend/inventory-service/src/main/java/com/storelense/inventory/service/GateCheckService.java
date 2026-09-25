package com.storelense.inventory.service;

import com.storelense.inventory.dto.GateCheckDto;
import com.storelense.inventory.dto.GateCheckRequest;
import com.storelense.inventory.dto.GateCheckSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GateCheckService {

    private final JdbcClient jdbcClient;

    @Transactional
    public GateCheckDto record(GateCheckRequest req, UUID guardUserId) {
        String[] matchedArr = req.epcsMatched() != null
                ? req.epcsMatched().toArray(new String[0]) : new String[0];
        String[] extraArr = req.epcsExtra() != null
                ? req.epcsExtra().toArray(new String[0]) : new String[0];

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        var id = jdbcClient.sql("""
                INSERT INTO inventory.gate_checks
                       (store_id, guard_user_id, bill_ref, expected_count, matched_count,
                        extra_count, outcome, epcs_matched, epcs_extra, checked_at, created_at)
                VALUES (CAST(:storeId AS uuid), CAST(:guardId AS uuid), :billRef,
                        :expectedCount, :matchedCount, :extraCount, :outcome,
                        CAST(:matched AS text[]), CAST(:extra AS text[]),
                        :now, :now)
                RETURNING id
                """)
                .param("storeId",        req.storeId().toString())
                .param("guardId",        guardUserId != null ? guardUserId.toString() : null)
                .param("billRef",        req.billRef())
                .param("expectedCount",  req.expectedCount())
                .param("matchedCount",   req.matchedCount())
                .param("extraCount",     req.extraCount())
                .param("outcome",        req.outcome().toUpperCase())
                .param("matched",        matchedArr)
                .param("extra",          extraArr)
                .param("now",            now)
                .query(UUID.class)
                .single();

        // Stamp the outcome onto the bill so it can't be reopened for another scan.
        if (req.billRef() != null && !req.billRef().isBlank()) {
            jdbcClient.sql("""
                    UPDATE inventory.bills
                    SET status = :outcome, gate_checked_at = :now
                    WHERE UPPER(bill_ref) = UPPER(:billRef) AND store_id = CAST(:storeId AS uuid)
                    """)
                    .param("outcome", req.outcome().toUpperCase())
                    .param("now",     now)
                    .param("billRef", req.billRef())
                    .param("storeId", req.storeId().toString())
                    .update();
        }

        log.info("Gate check recorded: id={} store={} outcome={} matched={}/{}",
                id, req.storeId(), req.outcome(), req.matchedCount(), req.expectedCount());

        return new GateCheckDto(id, req.storeId(), req.billRef(), now,
                req.expectedCount(), req.matchedCount(), req.extraCount(),
                req.outcome().toUpperCase(),
                req.epcsMatched() != null ? req.epcsMatched() : List.of(),
                req.epcsExtra()   != null ? req.epcsExtra()   : List.of(),
                null, null, null);
    }

    /**
     * @param fx9600Only false = guard-app bill checks (bill_ref present) — the Guard
     *        Dashboard. true = unattended FX9600 exit-portal alarms (bill_ref is
     *        deliberately null for these — see gate_guard's Reporter) — the separate
     *        Gate Alarms view. The two sources share this table but must never mix
     *        in either UI: an FX9600 alarm isn't a guard decision, and counting it
     *        as one skews flag-rate stats.
     */
    @Transactional(readOnly = true)
    public Page<GateCheckDto> list(UUID storeId, OffsetDateTime from, OffsetDateTime to,
                                    String outcome, Pageable pageable, boolean fx9600Only) {
        String outcomeFilter = (outcome != null && !outcome.isBlank()) ? outcome.toUpperCase() : null;
        String billRefClause = fx9600Only ? " bill_ref IS NULL" : " bill_ref IS NOT NULL";

        // Cast the standalone `:outcome IS NULL` occurrence explicitly — Postgres
        // can't infer a bound-null parameter's type from that check alone, since
        // each occurrence of a named parameter gets its own placeholder.
        String countSql = """
                SELECT COUNT(*) FROM inventory.gate_checks
                WHERE store_id = CAST(:storeId AS uuid)
                  AND checked_at BETWEEN :from AND :to
                  AND (CAST(:outcome AS varchar) IS NULL OR outcome = CAST(:outcome AS varchar))
                  AND """ + billRefClause + """

                """;

        long total = jdbcClient.sql(countSql)
                .param("storeId", storeId.toString())
                .param("from", from)
                .param("to", to)
                .param("outcome", outcomeFilter)
                .query(Long.class).single();

        String listSql = """
                SELECT id, store_id, bill_ref, checked_at, expected_count,
                       matched_count, extra_count, outcome, epcs_matched, epcs_extra,
                       resolution, resolved_by, resolved_at
                FROM inventory.gate_checks
                WHERE store_id = CAST(:storeId AS uuid)
                  AND checked_at BETWEEN :from AND :to
                  AND (CAST(:outcome AS varchar) IS NULL OR outcome = CAST(:outcome AS varchar))
                  AND """ + billRefClause + """

                ORDER BY checked_at DESC
                LIMIT :limit OFFSET :offset
                """;

        List<GateCheckDto> rows = jdbcClient.sql(listSql)
                .param("storeId", storeId.toString())
                .param("from",    from)
                .param("to",      to)
                .param("outcome", outcomeFilter)
                .param("limit",   pageable.getPageSize())
                .param("offset",  pageable.getOffset())
                .query((rs, n) -> new GateCheckDto(
                        rs.getObject("id", UUID.class),
                        rs.getObject("store_id", UUID.class),
                        rs.getString("bill_ref"),
                        rs.getObject("checked_at", OffsetDateTime.class),
                        rs.getInt("expected_count"),
                        rs.getInt("matched_count"),
                        rs.getInt("extra_count"),
                        rs.getString("outcome"),
                        arrayToList((String[]) rs.getArray("epcs_matched").getArray()),
                        arrayToList((String[]) rs.getArray("epcs_extra").getArray()),
                        rs.getString("resolution"),
                        rs.getObject("resolved_by", UUID.class),
                        rs.getObject("resolved_at", OffsetDateTime.class)
                ))
                .list();

        return new PageImpl<>(rows, pageable, total);
    }

    /** Guard Dashboard KPIs — guard-app bill checks only (see {@link #list(UUID, OffsetDateTime, OffsetDateTime, String, Pageable, boolean)}). */
    @Transactional(readOnly = true)
    public GateCheckSummaryDto summary(UUID storeId, LocalDate date) {
        return summarize(storeId, null, date, false);
    }

    /** Same KPI shape as {@link #summary}, scoped to a single guard's own checks. */
    @Transactional(readOnly = true)
    public GateCheckSummaryDto mySummary(UUID storeId, UUID guardUserId, LocalDate date) {
        return summarize(storeId, guardUserId, date, false);
    }

    /** Gate Alarms KPIs — unattended FX9600 exit-portal alarms only. */
    @Transactional(readOnly = true)
    public GateCheckSummaryDto alarmSummary(UUID storeId, LocalDate date) {
        return summarize(storeId, null, date, true);
    }

    private GateCheckSummaryDto summarize(UUID storeId, UUID guardUserId, LocalDate date, boolean fx9600Only) {
        OffsetDateTime start = date.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end   = date.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        String billRefClause = fx9600Only ? " bill_ref IS NULL" : " bill_ref IS NOT NULL";

        record Row(String outcome, long cnt, long extraSum) {}

        List<Row> rows = jdbcClient.sql("""
                SELECT outcome,
                       COUNT(*)          AS cnt,
                       SUM(extra_count)  AS extra_sum
                FROM inventory.gate_checks
                WHERE store_id  = CAST(:storeId AS uuid)
                  AND checked_at BETWEEN :start AND :end
                  AND (CAST(:guardId AS uuid) IS NULL OR guard_user_id = CAST(:guardId AS uuid))
                  AND """ + billRefClause + """

                GROUP BY outcome
                """)
                .param("storeId", storeId.toString())
                .param("start", start)
                .param("end",   end)
                .param("guardId", guardUserId != null ? guardUserId.toString() : null)
                .query((rs, n) -> new Row(
                        rs.getString("outcome"),
                        rs.getLong("cnt"),
                        rs.getLong("extra_sum")))
                .list();

        int total     = rows.stream().mapToInt(r -> (int) r.cnt()).sum();
        int released  = rows.stream().filter(r -> "RELEASED".equals(r.outcome())).mapToInt(r -> (int) r.cnt()).sum();
        int flagged   = rows.stream().filter(r -> "FLAGGED".equals(r.outcome())).mapToInt(r -> (int) r.cnt()).sum();
        int abandoned = rows.stream().filter(r -> "ABANDONED".equals(r.outcome())).mapToInt(r -> (int) r.cnt()).sum();
        int extraItems = rows.stream().mapToInt(r -> (int) r.extraSum()).sum();
        double flagRate = total > 0 ? Math.round((flagged * 100.0 / total) * 10.0) / 10.0 : 0.0;

        // Only FLAGGED rows ever need a resolution — this is the count that should
        // keep nagging a guard/manager until every one of today's FLAGGED rows has
        // been explicitly cleared, not just aged off the list.
        Long unresolved = jdbcClient.sql("""
                SELECT COUNT(*) FROM inventory.gate_checks
                WHERE store_id  = CAST(:storeId AS uuid)
                  AND checked_at BETWEEN :start AND :end
                  AND (CAST(:guardId AS uuid) IS NULL OR guard_user_id = CAST(:guardId AS uuid))
                  AND outcome = 'FLAGGED'
                  AND resolution IS NULL
                  AND """ + billRefClause + """

                """)
                .param("storeId", storeId.toString())
                .param("start", start)
                .param("end",   end)
                .param("guardId", guardUserId != null ? guardUserId.toString() : null)
                .query(Long.class).single();

        return new GateCheckSummaryDto(total, released, flagged, abandoned, extraItems, flagRate,
                unresolved.intValue(), flagged - unresolved.intValue());
    }

    /** Guard's own last N gate checks, most recent first — for a mobile "recent activity" list. */
    @Transactional(readOnly = true)
    public List<GateCheckDto> myRecent(UUID storeId, UUID guardUserId, int limit) {
        return jdbcClient.sql("""
                SELECT id, store_id, bill_ref, checked_at, expected_count,
                       matched_count, extra_count, outcome, epcs_matched, epcs_extra,
                       resolution, resolved_by, resolved_at
                FROM inventory.gate_checks
                WHERE store_id = CAST(:storeId AS uuid)
                  AND guard_user_id = CAST(:guardId AS uuid)
                ORDER BY checked_at DESC
                LIMIT :limit
                """)
                .param("storeId", storeId.toString())
                .param("guardId", guardUserId.toString())
                .param("limit",   limit)
                .query((rs, n) -> new GateCheckDto(
                        rs.getObject("id", UUID.class),
                        rs.getObject("store_id", UUID.class),
                        rs.getString("bill_ref"),
                        rs.getObject("checked_at", OffsetDateTime.class),
                        rs.getInt("expected_count"),
                        rs.getInt("matched_count"),
                        rs.getInt("extra_count"),
                        rs.getString("outcome"),
                        arrayToList((String[]) rs.getArray("epcs_matched").getArray()),
                        arrayToList((String[]) rs.getArray("epcs_extra").getArray()),
                        rs.getString("resolution"),
                        rs.getObject("resolved_by", UUID.class),
                        rs.getObject("resolved_at", OffsetDateTime.class)
                ))
                .list();
    }

    // Guard-app bill checks have an actual customer in front of the guard;
    // FX9600 alarms are an unattended sensor event with no one to verify against —
    // hence the separate REVIEWED_FALSE_ALARM/CONFIRMED_THEFT vocabulary for those.
    private static final List<String> VALID_RESOLUTIONS = List.of(
            "CUSTOMER_VERIFIED", "THEFT_PREVENTED", "ESCALATED",
            "REVIEWED_FALSE_ALARM", "CONFIRMED_THEFT");

    /** Records how a guard/manager closed out a FLAGGED release — customer verified
     *  fine, an item was physically recovered, or it was escalated to a supervisor. */
    @Transactional
    public void resolve(UUID gateCheckId, String resolution, UUID resolvedBy) {
        String normalized = resolution == null ? null : resolution.toUpperCase();
        if (normalized == null || !VALID_RESOLUTIONS.contains(normalized)) {
            throw new IllegalArgumentException("Invalid resolution: " + resolution);
        }

        int updated = jdbcClient.sql("""
                UPDATE inventory.gate_checks
                SET resolution = :resolution, resolved_by = CAST(:resolvedBy AS uuid), resolved_at = :now
                WHERE id = CAST(:id AS uuid)
                """)
                .param("resolution", normalized)
                .param("resolvedBy", resolvedBy != null ? resolvedBy.toString() : null)
                .param("now", OffsetDateTime.now(ZoneOffset.UTC))
                .param("id", gateCheckId.toString())
                .update();

        if (updated == 0) {
            throw new java.util.NoSuchElementException("Gate check not found: " + gateCheckId);
        }
    }

    private List<String> arrayToList(String[] arr) {
        return arr != null ? Arrays.asList(arr) : List.of();
    }
}
