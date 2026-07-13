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

        log.info("Gate check recorded: id={} store={} outcome={} matched={}/{}",
                id, req.storeId(), req.outcome(), req.matchedCount(), req.expectedCount());

        return new GateCheckDto(id, req.storeId(), req.billRef(), now,
                req.expectedCount(), req.matchedCount(), req.extraCount(),
                req.outcome().toUpperCase(),
                req.epcsMatched() != null ? req.epcsMatched() : List.of(),
                req.epcsExtra()   != null ? req.epcsExtra()   : List.of());
    }

    @Transactional(readOnly = true)
    public Page<GateCheckDto> list(UUID storeId, OffsetDateTime from, OffsetDateTime to,
                                    String outcome, Pageable pageable) {
        String outcomeFilter = (outcome != null && !outcome.isBlank()) ? outcome.toUpperCase() : null;

        String countSql = """
                SELECT COUNT(*) FROM inventory.gate_checks
                WHERE store_id = CAST(:storeId AS uuid)
                  AND checked_at BETWEEN :from AND :to
                  AND (:outcome IS NULL OR outcome = :outcome)
                """;

        long total = jdbcClient.sql(countSql)
                .param("storeId", storeId.toString())
                .param("from", from)
                .param("to", to)
                .param("outcome", outcomeFilter)
                .query(Long.class).single();

        String listSql = """
                SELECT id, store_id, bill_ref, checked_at, expected_count,
                       matched_count, extra_count, outcome, epcs_matched, epcs_extra
                FROM inventory.gate_checks
                WHERE store_id = CAST(:storeId AS uuid)
                  AND checked_at BETWEEN :from AND :to
                  AND (:outcome IS NULL OR outcome = :outcome)
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
                        arrayToList((String[]) rs.getArray("epcs_extra").getArray())
                ))
                .list();

        return new PageImpl<>(rows, pageable, total);
    }

    @Transactional(readOnly = true)
    public GateCheckSummaryDto summary(UUID storeId, LocalDate date) {
        OffsetDateTime start = date.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end   = date.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        record Row(String outcome, long cnt, long extraSum) {}

        List<Row> rows = jdbcClient.sql("""
                SELECT outcome,
                       COUNT(*)          AS cnt,
                       SUM(extra_count)  AS extra_sum
                FROM inventory.gate_checks
                WHERE store_id  = CAST(:storeId AS uuid)
                  AND checked_at BETWEEN :start AND :end
                GROUP BY outcome
                """)
                .param("storeId", storeId.toString())
                .param("start", start)
                .param("end",   end)
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

        return new GateCheckSummaryDto(total, released, flagged, abandoned, extraItems, flagRate);
    }

    private List<String> arrayToList(String[] arr) {
        return arr != null ? Arrays.asList(arr) : List.of();
    }
}
