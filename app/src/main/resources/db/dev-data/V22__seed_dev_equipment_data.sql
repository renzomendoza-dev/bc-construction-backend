-- ============================================================
-- BC Construction Services — Sample Equipment Data (dev profile only)
-- Prices in Philippine Peso (PHP)
-- Assumes app_user rows 1 and 2 already exist (from V21).
-- ============================================================

-- ------------------------------------------------------------
-- equipment (12 items — mix of statuses so you can exercise the
-- checkout/check-in flow right away)
-- ------------------------------------------------------------
INSERT INTO public.equipment
  (id, asset_tag, name, category, serial_number, status, current_holder_id, current_site, checked_out_at, purchase_price, purchase_date, purchase_vendor, created_by, updated_by, created_at, updated_at)
VALUES
(1,  'EQ-0001', 'Backhoe Loader (Mini)',            'Heavy Equipment', 'BHL-2023-4471', 'IN_USE',      2,    'Sta. Maria Project',   now() - interval '5 days', 1850000.00, '2023-03-14', 'JM Equipment Rentals & Sales',      1, 1, now(), now()),
(2,  'EQ-0002', 'Bar Cutter (Electric, 1in)',       'Power Tools',     'BC-EL-88213',   'AVAILABLE',   NULL, NULL,                    NULL,                        45000.00,  '2024-01-20', 'RSB Steel & Hardware Supply',       1, 1, now(), now()),
(3,  'EQ-0003', 'Bar Bender (Electric, 1in)',       'Power Tools',     'BB-EL-55921',   'AVAILABLE',   NULL, NULL,                    NULL,                        55000.00,  '2024-01-20', 'RSB Steel & Hardware Supply',       1, 1, now(), now()),
(4,  'EQ-0004', 'Concrete Mixer (1-bagger)',        'Heavy Equipment', 'CM1B-2022-330', 'CHECKED_OUT', 2,    'Sta. Maria Project',   now() - interval '2 days', 85000.00,  '2022-09-10', 'Wilcon Depot - Bulacan Branch',     1, 1, now(), now()),
(5,  'EQ-0005', 'Concrete Vibrator (Gas-powered)',  'Power Tools',     'CV-GP-11209',   'AVAILABLE',   NULL, NULL,                    NULL,                        28000.00,  '2023-06-02', 'Wilcon Depot - Bulacan Branch',     1, 1, now(), now()),
(6,  'EQ-0006', 'Welding Machine (Inverter, 200A)', 'Power Tools',     'WM-INV-77410',  'IN_REPAIR',   NULL, NULL,                    NULL,                        35000.00,  '2022-11-18', 'RSB Steel & Hardware Supply',       1, 1, now(), now()),
(7,  'EQ-0007', 'Angle Grinder 4.5in',              'Power Tools',     'AG-45-33012',   'AVAILABLE',   NULL, NULL,                    NULL,                        3500.00,   '2024-05-08', 'Wilcon Depot - Bulacan Branch',     1, 1, now(), now()),
(8,  'EQ-0008', 'Circular Saw 7-1/4in',             'Power Tools',     'CS-714-90876',  'AVAILABLE',   NULL, NULL,                    NULL,                        6500.00,   '2024-05-08', 'Wilcon Depot - Bulacan Branch',     1, 1, now(), now()),
(9,  'EQ-0009', 'Scaffolding Set (H-Frame, 12 sets)','Site Equipment', 'SC-HF-2021-19', 'IN_USE',      2,    'Sta. Maria Project',   now() - interval '10 days', 120000.00, '2021-07-25', 'JM Equipment Rentals & Sales',      1, 1, now(), now()),
(10, 'EQ-0010', 'Portable Generator (5kVA)',        'Heavy Equipment', 'GEN-5KVA-6633', 'AVAILABLE',   NULL, NULL,                    NULL,                        65000.00,  '2023-02-14', 'JM Equipment Rentals & Sales',      1, 1, now(), now()),
(11, 'EQ-0011', 'Plate Compactor',                  'Heavy Equipment', 'PC-2022-8845',  'RETIRED',     NULL, NULL,                    NULL,                        48000.00,  '2020-08-30', 'JM Equipment Rentals & Sales',      1, 1, now(), now()),
(12, 'EQ-0012', 'Total Station (Survey Instrument)', 'Survey Equipment','TS-2023-1150',  'AVAILABLE',   NULL, NULL,                    NULL,                        185000.00, '2023-10-05', 'GeoSurvey Instruments Inc.',        1, 1, now(), now());

-- ------------------------------------------------------------
-- equipment_assignment (history rows for the currently
-- checked-out / in-use equipment above)
-- ------------------------------------------------------------
INSERT INTO public.equipment_assignment
  (id, equipment_id, assigned_to_id, site, checked_out_at, checked_in_at, condition_out, condition_in, created_by, created_at)
VALUES
(1, 1, 2, 'Sta. Maria Project', now() - interval '5 days',  NULL, 'Good condition, full tank on release', NULL, 1, now() - interval '5 days'),
(2, 4, 2, 'Sta. Maria Project', now() - interval '2 days',  NULL, 'Good condition, minor paint wear',      NULL, 1, now() - interval '2 days'),
(3, 9, 2, 'Sta. Maria Project', now() - interval '10 days', NULL, 'Complete set, all pins accounted for',  NULL, 1, now() - interval '10 days');

-- ------------------------------------------------------------
-- Reset sequences
-- ------------------------------------------------------------
SELECT setval('public.equipment_id_seq', (SELECT MAX(id) FROM public.equipment));
SELECT setval('public.equipment_assignment_id_seq', (SELECT MAX(id) FROM public.equipment_assignment));
