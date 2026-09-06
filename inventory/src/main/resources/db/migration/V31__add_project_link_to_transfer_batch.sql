-- V31: link TransferBatch to a Project (optional), and trace each line's
-- auto-generated MATERIAL ProjectExpense back from the inventory side.
--
-- project_id lives on transfer_batch (this module's own table) referencing
-- projects' project table — legal since inventory now has a real Maven
-- dependency on projects, and project already exists as of V27.
--
-- project_expense_id lives on transfer_line_item (also this module's own
-- table), not as a new column on ProjectExpense — keeps the FK pointing the
-- same direction as the Maven dependency (inventory -> projects) and adds no
-- schema-level dependency from projects back onto inventory, preserving
-- projects' documented independence. Mirrors the workers/Attendance
-- precedent (see CLAUDE.md's "Cross-module write orchestration").

ALTER TABLE transfer_batch
    ADD COLUMN project_id BIGINT REFERENCES project (id);

ALTER TABLE transfer_line_item
    ADD COLUMN project_expense_id BIGINT REFERENCES project_expense (id);
