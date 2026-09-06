-- ============================================================
-- BC Construction Services — Sample Projects Data (dev profile only)
-- Prices in Philippine Peso (PHP)
-- Assumes app_user rows 1 and 2 already exist (from V21).
--
-- Numbered to run AFTER every real schema migration (currently up through
-- V27 in db/migration) rather than adjacent to it - dev-data must always
-- apply against the FINAL schema shape, not whatever shape existed when
-- this seed file was first written. If a new real schema migration is ever
-- added above V27, bump this file's version number (see V21's identical note
-- and its own history of getting this wrong once already).
-- ============================================================

-- ------------------------------------------------------------
-- project (one of each status worth demoing: ongoing, on hold,
-- and a fully-closed-out project with expenses near its budget)
-- ------------------------------------------------------------
INSERT INTO public.project (id, code, name, description, status, budget, start_date, end_date, initiated_by, created_at, updated_at) VALUES
(1, 'PRJ-2026-001', 'Sta. Maria Warehouse Expansion', 'New 500sqm warehouse extension at the Sta. Maria site',        'ACTIVE',    2500000.00, '2026-07-01', NULL,         1, now() - interval '60 days', now() - interval '2 days'),
(2, 'PRJ-2026-002', 'Site Access Road - San Jose del Monte', 'Gravel access road connecting the main warehouse to the highway', 'ON_HOLD',   350000.00,  '2026-06-15', NULL,         1, now() - interval '75 days', now() - interval '10 days'),
(3, 'PRJ-2025-014', 'Warehouse Roof Repair', 'Repair of storm-damaged roofing on Warehouse A',                        'COMPLETED', 180000.00,  '2025-11-01', '2025-12-20', 2, now() - interval '300 days', now() - interval '250 days');

-- ------------------------------------------------------------
-- project_expense (labor/material/other rows for each project above)
-- ------------------------------------------------------------
INSERT INTO public.project_expense (id, project_id, category, description, amount, expense_date, recorded_by, created_at) VALUES
-- Sta. Maria Warehouse Expansion (ACTIVE, budget 2,500,000.00)
(1,  1, 'LABOR',    'Weekly payroll - mason & carpenter crew (8 workers)', 160000.00, '2026-07-15', 1, now() - interval '52 days'),
(2,  1, 'LABOR',    'Weekly payroll - mason & carpenter crew (8 workers)', 160000.00, '2026-07-22', 1, now() - interval '45 days'),
(3,  1, 'MATERIAL', 'Portland Cement 40kg - 400 bags',                     92000.00, '2026-07-10', 1, now() - interval '55 days'),
(4,  1, 'MATERIAL', 'Deformed Rebar 10mm x 6m - 1200 pcs',                 192000.00, '2026-07-18', 1, now() - interval '48 days'),
(5,  1, 'MATERIAL', 'Marine Plywood 3/4in 4x8ft - 200 sheets',             290000.00, '2026-08-02', 1, now() - interval '30 days'),
(6,  1, 'MATERIAL', 'Sand and gravel delivery',                            36000.00, '2026-08-10', 1, now() - interval '22 days'),
(7,  1, 'OTHER',    'Equipment rental - mini backhoe (1 week)',            25000.00, '2026-08-12', 1, now() - interval '20 days'),
-- Site Access Road (ON_HOLD, budget 350,000.00)
(8,  2, 'LABOR',    'Site prep and grading crew (3 days)',                 40000.00, '2026-06-20', 1, now() - interval '70 days'),
(9,  2, 'MATERIAL', 'Gravel G1 delivery - 40 cu.m',                        44000.00, '2026-06-25', 1, now() - interval '65 days'),
-- Warehouse Roof Repair (COMPLETED, budget 180,000.00)
(10, 3, 'LABOR',    'Roofing crew (5 days)',                               45000.00, '2025-11-10', 2, now() - interval '295 days'),
(11, 3, 'MATERIAL', 'GI Sheet Corrugated 0.4mm 8ft - 250 sheets',          96250.00, '2025-11-15', 2, now() - interval '290 days'),
(12, 3, 'OTHER',    'Scaffolding rental (2 weeks)',                        12000.00, '2025-11-20', 2, now() - interval '285 days');

-- ------------------------------------------------------------
-- Reset sequences
-- ------------------------------------------------------------
SELECT setval('public.project_id_seq', (SELECT MAX(id) FROM public.project));
SELECT setval('public.project_expense_id_seq', (SELECT MAX(id) FROM public.project_expense));
