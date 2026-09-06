-- ============================================================
-- BC Construction Services — Sample Workers/Attendance Data (dev profile only)
-- Prices in Philippine Peso (PHP)
-- Assumes app_user rows 1/2 (V21) and project rows 1/2/3 (V28) already exist.
--
-- Numbered to run AFTER every real schema migration (currently up through
-- V29 in db/migration) — see V21's note on why dev-data must always apply
-- against the FINAL schema shape, not whatever existed when this file was
-- written.
--
-- These attendance rows are inserted directly (not through AttendanceService),
-- so nothing here goes through its usual validation — the matching
-- project_expense rows are inserted by hand instead, exactly as that service
-- would have produced them. Only attributed to project 1 (ACTIVE) and
-- project 2 (ON_HOLD), never project 3 (COMPLETED) — a real attendance
-- record could never be created against it (422), so seeding one there
-- would misrepresent what this app can actually do.
-- ============================================================

-- ------------------------------------------------------------
-- worker (one inactive, to demo the active filter)
-- ------------------------------------------------------------
INSERT INTO public.worker (id, name, position, daily_rate, active, created_by, created_at, updated_at) VALUES
(1, 'Ramon Villanueva', 'Mason',    800.00, true,  1, now() - interval '40 days', now() - interval '40 days'),
(2, 'Jun Santos',       'Laborer',  650.00, true,  1, now() - interval '40 days', now() - interval '40 days'),
(3, 'Pedro Reyes',      'Foreman', 1000.00, true,  1, now() - interval '40 days', now() - interval '40 days'),
(4, 'Ariel Cruz',       'Laborer',  650.00, false, 1, now() - interval '90 days', now() - interval '30 days');

-- ------------------------------------------------------------
-- project_expense (LABOR rows matching the attendance below,
-- continuing project_expense's id sequence from V28's expense rows 1-12)
-- ------------------------------------------------------------
INSERT INTO public.project_expense (id, project_id, category, description, amount, expense_date, recorded_by, created_at) VALUES
(13, 1, 'LABOR', 'Ramon Villanueva - 1d on 2026-08-20', 800.00, '2026-08-20', 1, now() - interval '17 days'),
(14, 1, 'LABOR', 'Jun Santos - 1d on 2026-08-20',       650.00, '2026-08-20', 1, now() - interval '17 days'),
(15, 1, 'LABOR', 'Ramon Villanueva - 1d on 2026-08-21', 800.00, '2026-08-21', 1, now() - interval '16 days'),
(16, 2, 'LABOR', 'Pedro Reyes - 0.5d on 2026-06-22',    500.00, '2026-06-22', 1, now() - interval '76 days');

-- ------------------------------------------------------------
-- attendance (traces back to the project_expense rows above via
-- project_expense_id)
-- ------------------------------------------------------------
INSERT INTO public.attendance (id, worker_id, project_id, attendance_date, days_present, rate_snapshot, notes, project_expense_id, recorded_by, created_at) VALUES
(1, 1, 1, '2026-08-20', 1.0, 800.00,  'Formwork, Building A',  13, 1, now() - interval '17 days'),
(2, 2, 1, '2026-08-20', 1.0, 650.00,  'Rebar tying, Building A', 14, 1, now() - interval '17 days'),
(3, 1, 1, '2026-08-21', 1.0, 800.00,  'Formwork, Building A',  15, 1, now() - interval '16 days'),
(4, 3, 2, '2026-06-22', 0.5, 1000.00, 'Site walkthrough',       16, 1, now() - interval '76 days');

-- ------------------------------------------------------------
-- Reset sequences
-- ------------------------------------------------------------
SELECT setval('public.worker_id_seq', (SELECT MAX(id) FROM public.worker));
SELECT setval('public.project_expense_id_seq', (SELECT MAX(id) FROM public.project_expense));
SELECT setval('public.attendance_id_seq', (SELECT MAX(id) FROM public.attendance));
