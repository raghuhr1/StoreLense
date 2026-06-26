package com.storelense.product.domain.repository;

import com.storelense.product.domain.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findBySku(String sku);
    Page<Product> findByActiveTrue(Pageable pageable);
    boolean existsBySku(String sku);

    @Query("SELECT p FROM Product p WHERE p.active = true AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%',:q,'%')))")
    Page<Product> search(@Param("q") String query, Pageable pageable);

    // Store-scoped queries: return only products that belong to a specific store,
    // determined by inventory_state (ERP-imported) or epc_registry (RFID-tracked).
    // since: if non-null, only return products updated after that timestamp (delta sync).
    @Query(value = """
            SELECT DISTINCT p.* FROM products.products p
            WHERE p.is_active = true
              AND (:since IS NULL OR p.updated_at > :since)
              AND p.id IN (
                  SELECT product_id FROM inventory.inventory_state
                   WHERE store_id = CAST(:storeId AS uuid)
                  UNION
                  SELECT et.product_id FROM inventory.epc_registry er
                  JOIN products.epc_tags et ON et.epc = er.epc AND et.is_active = true
                   WHERE er.store_id = CAST(:storeId AS uuid)
              )
            """,
            countQuery = """
            SELECT COUNT(DISTINCT p.id) FROM products.products p
            WHERE p.is_active = true
              AND (:since IS NULL OR p.updated_at > :since)
              AND p.id IN (
                  SELECT product_id FROM inventory.inventory_state
                   WHERE store_id = CAST(:storeId AS uuid)
                  UNION
                  SELECT et.product_id FROM inventory.epc_registry er
                  JOIN products.epc_tags et ON et.epc = er.epc AND et.is_active = true
                   WHERE er.store_id = CAST(:storeId AS uuid)
              )
            """,
            nativeQuery = true)
    Page<Product> findByStore(@Param("storeId") String storeId,
                              @Param("since") OffsetDateTime since,
                              Pageable pageable);

    @Query(value = """
            SELECT DISTINCT p.* FROM products.products p
            WHERE p.is_active = true
              AND (:since IS NULL OR p.updated_at > :since)
              AND p.id IN (
                  SELECT product_id FROM inventory.inventory_state
                   WHERE store_id = CAST(:storeId AS uuid)
                  UNION
                  SELECT et.product_id FROM inventory.epc_registry er
                  JOIN products.epc_tags et ON et.epc = er.epc AND et.is_active = true
                   WHERE er.store_id = CAST(:storeId AS uuid)
              )
              AND (LOWER(p.name) LIKE LOWER(CONCAT('%',:q,'%'))
                OR LOWER(p.sku)  LIKE LOWER(CONCAT('%',:q,'%')))
            """,
            countQuery = """
            SELECT COUNT(DISTINCT p.id) FROM products.products p
            WHERE p.is_active = true
              AND (:since IS NULL OR p.updated_at > :since)
              AND p.id IN (
                  SELECT product_id FROM inventory.inventory_state
                   WHERE store_id = CAST(:storeId AS uuid)
                  UNION
                  SELECT et.product_id FROM inventory.epc_registry er
                  JOIN products.epc_tags et ON et.epc = er.epc AND et.is_active = true
                   WHERE er.store_id = CAST(:storeId AS uuid)
              )
              AND (LOWER(p.name) LIKE LOWER(CONCAT('%',:q,'%'))
                OR LOWER(p.sku)  LIKE LOWER(CONCAT('%',:q,'%')))
            """,
            nativeQuery = true)
    Page<Product> searchByStore(@Param("q") String query,
                                @Param("storeId") String storeId,
                                @Param("since") OffsetDateTime since,
                                Pageable pageable);
}
