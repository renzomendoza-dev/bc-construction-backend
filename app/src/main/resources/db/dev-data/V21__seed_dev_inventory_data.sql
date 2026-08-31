-- ============================================================
-- BC Construction Services — Sample Data (dev profile only)
-- Prices in Philippine Peso (PHP)
--
-- Numbered to run AFTER every real schema migration (currently up through
-- V20 in db/migration) rather than adjacent to them - dev-data must always
-- apply against the FINAL schema shape, not whatever shape existed when
-- this seed file was first written. If a new real schema migration is ever
-- added above V20, bump this file's version number (and V22's) so dev-data
-- keeps running last. (History: this file used to be V14, which broke when
-- V16 later added warehouse.type - V14 ran before V16 and referenced a
-- column that didn't exist yet.)
-- ============================================================

-- ------------------------------------------------------------
-- app_user (needed for created_by / confirmed_by references)
-- ------------------------------------------------------------
INSERT INTO public.app_user (id, keycloak_id, full_name, active, created_at, updated_at) VALUES
(1, 'a1111111-1111-1111-1111-111111111111', 'Renzo Mendoza', true, now(), now()),
(2, 'a2222222-2222-2222-2222-222222222222', 'Warehouse Staff', true, now(), now());

-- ------------------------------------------------------------
-- warehouse
-- ------------------------------------------------------------
INSERT INTO public.warehouse (id, code, name, active, type, created_at, updated_at) VALUES
(1, 'WH-MAIN', 'Main Warehouse - San Jose del Monte', true, 'MAIN', now(), now()),
(2, 'WH-SITE1', 'Site Warehouse - Sta. Maria Project', true, 'SITE', now(), now());

-- ------------------------------------------------------------
-- storage_location
-- ------------------------------------------------------------
INSERT INTO public.storage_location (id, warehouse_id, code, active) VALUES
(1, 1, 'A1', true),
(2, 1, 'A2', true),
(3, 1, 'B1', true),
(4, 2, 'SITE-1', true);

-- ------------------------------------------------------------
-- supplier
-- ------------------------------------------------------------
INSERT INTO public.supplier (id, name, contact_info, active, created_at, updated_at) VALUES
(1, 'Eagle Cement Trading Corp.', 'sales@eaglecement.example.ph | 0917-100-2000', true, now(), now()),
(2, 'Wilcon Depot - Bulacan Branch', 'orders@wilcon.example.ph | 044-815-3000', true, now(), now()),
(3, 'RSB Steel & Hardware Supply', 'rsb.hardware@example.ph | 0928-555-1234', true, now(), now());

-- ------------------------------------------------------------
-- item (10+ construction materials, prices in PHP)
-- ------------------------------------------------------------
INSERT INTO public.item (id, sku, name, category, unit_of_measure, selling_price, default_cost_price, active, created_at, updated_at) VALUES
(1,  'CEM-001', 'Portland Cement 40kg (Type 1)',            'Cement',       'bag',   265.00,  230.00, true, now(), now()),
(2,  'RBR-010', 'Deformed Rebar 10mm x 6m',                 'Steel',        'pc',    185.00,  160.00, true, now(), now()),
(3,  'CHB-004', 'Hollow Block (CHB) 4in',                   'Masonry',      'pc',     15.50,   12.00, true, now(), now()),
(4,  'PLY-034', 'Marine Plywood 3/4in 4x8ft',                'Lumber',       'sheet', 1450.00, 1250.00, true, now(), now()),
(5,  'GIS-008', 'GI Sheet Corrugated 0.4mm 8ft',            'Roofing',      'sheet',  385.00,  330.00, true, now(), now()),
(6,  'NAI-004', 'Common Wire Nail 4in',                     'Hardware',     'kg',      95.00,   78.00, true, now(), now()),
(7,  'PVC-004', 'PVC Pipe 4in x 3m (Sanitary)',              'Plumbing',     'pc',    320.00,  270.00, true, now(), now()),
(8,  'SND-001', 'Sand (Washed)',                            'Aggregates',   'cu.m',   950.00,  800.00, true, now(), now()),
(9,  'GRV-001', 'Gravel G1',                                 'Aggregates',   'cu.m',  1100.00,  950.00, true, now(), now()),
(10, 'PNT-004', 'Latex Paint White 4L (Boysen)',            'Paint',        'can',    895.00,  750.00, true, now(), now()),
(11, 'TWR-016', 'Tie Wire #16 (1kg roll)',                  'Hardware',     'kg',      78.00,   62.00, true, now(), now()),
(12, 'LUM-228', 'Coco Lumber 2x2x8ft',                       'Lumber',       'pc',    145.00,  118.00, true, now(), now());

-- ------------------------------------------------------------
-- item_supplier (link items to suppliers with unit cost in PHP)
-- ------------------------------------------------------------
INSERT INTO public.item_supplier (id, item_id, supplier_id, supplier_sku, unit_cost) VALUES
(1, 1,  1, 'EC-CEM40',   230.00),
(2, 2,  3, 'RSB-10MM6M', 160.00),
(3, 4,  2, 'WIL-PLY34',  1250.00),
(4, 5,  2, 'WIL-GIS08',  330.00),
(5, 6,  3, 'RSB-NAIL4',   78.00),
(6, 3,  2, 'WIL-CHB4',    12.00),
(7, 7,  2, 'WIL-PVC4',   270.00),
(8, 10, 2, 'WIL-PNT4L',  750.00);

-- ------------------------------------------------------------
-- inventory_stock (current stock levels per item/warehouse/location)
-- ------------------------------------------------------------
INSERT INTO public.inventory_stock (id, item_id, warehouse_id, location_id, quantity, reorder_threshold, updated_at) VALUES
(1,  1,  1, 1, 500,  100, now()),
(2,  2,  1, 1, 300,   50, now()),
(3,  3,  1, 2, 2000,  500, now()),
(4,  4,  1, 2, 80,    20, now()),
(5,  5,  1, 2, 120,   30, now()),
(6,  6,  1, 3, 150,   30, now()),
(7,  7,  1, 3, 60,    15, now()),
(8,  8,  1, 3, 40,    10, now()),
(9,  9,  1, 3, 40,    10, now()),
(10, 10, 1, 1, 90,    20, now()),
(11, 11, 1, 3, 100,   25, now()),
(12, 12, 1, 2, 200,   40, now()),
(13, 1,  2, 4, 100,   30, now()),
(14, 2,  2, 4, 60,    20, now());

-- ------------------------------------------------------------
-- purchase_receipt (sample confirmed purchase from a supplier)
-- ------------------------------------------------------------
INSERT INTO public.purchase_receipt (id, supplier_id, warehouse_id, receipt_number, purchase_date, total_amount, image_url, notes, confirmed, confirmed_at, created_at, created_by, confirmed_by) VALUES
(1, 1, 1, 'PR-2026-0001', '2026-08-01', 115000.00, NULL, 'Cement restock for August', true, now(), now(), 1, 1),
(2, 2, 1, 'PR-2026-0002', '2026-08-05', 46000.00,  NULL, 'Plywood and GI sheet restock', true, now(), now(), 1, 1);

-- ------------------------------------------------------------
-- purchase_receipt_line
-- ------------------------------------------------------------
INSERT INTO public.purchase_receipt_line (id, purchase_receipt_id, item_id, quantity, unit_cost, line_total) VALUES
(1, 1, 1, 500, 230.00, 115000.00),
(2, 2, 4, 20,  1250.00, 25000.00),
(3, 2, 5, 60,  330.00,  19800.00);

-- ------------------------------------------------------------
-- stock_movement (IN movements matching the purchase receipts above)
-- ------------------------------------------------------------
INSERT INTO public.stock_movement (id, item_id, warehouse_id, from_location_id, to_location_id, movement_type, quantity, reason, created_at, created_by) VALUES
(1, 1, 1, NULL, 1, 'IN', 500, 'Purchase receipt PR-2026-0001', now(), 1),
(2, 4, 1, NULL, 2, 'IN', 20,  'Purchase receipt PR-2026-0002', now(), 1),
(3, 5, 1, NULL, 2, 'IN', 60,  'Purchase receipt PR-2026-0002', now(), 1);

-- ------------------------------------------------------------
-- Reset sequences so future inserts continue from the right id
-- ------------------------------------------------------------
SELECT setval('public.app_user_id_seq', (SELECT MAX(id) FROM public.app_user));
SELECT setval('public.warehouse_id_seq', (SELECT MAX(id) FROM public.warehouse));
SELECT setval('public.storage_location_id_seq', (SELECT MAX(id) FROM public.storage_location));
SELECT setval('public.supplier_id_seq', (SELECT MAX(id) FROM public.supplier));
SELECT setval('public.item_id_seq', (SELECT MAX(id) FROM public.item));
SELECT setval('public.item_supplier_id_seq', (SELECT MAX(id) FROM public.item_supplier));
SELECT setval('public.inventory_stock_id_seq', (SELECT MAX(id) FROM public.inventory_stock));
SELECT setval('public.purchase_receipt_id_seq', (SELECT MAX(id) FROM public.purchase_receipt));
SELECT setval('public.purchase_receipt_line_id_seq', (SELECT MAX(id) FROM public.purchase_receipt_line));
SELECT setval('public.stock_movement_id_seq', (SELECT MAX(id) FROM public.stock_movement));
