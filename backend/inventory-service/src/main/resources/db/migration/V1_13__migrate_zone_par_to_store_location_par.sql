-- Phase 3 cutover: seed store_location_par_levels from existing zone_par_levels so
-- SOH-based replenishment has par data on day one, without requiring admins to
-- re-enter every par level by hand.
--
-- Zones whose zone_type indicates the shopping floor ('floor','display','entrance',
-- 'fitting_room') are summed into a single Sales Floor par per store+product.
-- Backroom/stockroom-type zones are intentionally excluded — Backroom is the buffer,
-- not a par-tracked location in the new model.
INSERT INTO inventory.store_location_par_levels (store_id, location_code, product_id, par_qty, min_qty, active)
SELECT
    pl.store_id,
    'SALES_FLOOR',
    pl.product_id,
    SUM(pl.par_qty) AS par_qty,
    SUM(pl.min_qty) AS min_qty,
    true
FROM inventory.zone_par_levels pl
JOIN stores.zones z
     ON z.id = pl.zone_id
    AND z.zone_type IN ('floor', 'display', 'entrance', 'fitting_room')
WHERE pl.active = true
GROUP BY pl.store_id, pl.product_id
ON CONFLICT (store_id, location_code, product_id) DO NOTHING;
