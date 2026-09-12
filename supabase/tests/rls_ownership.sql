-- RLS ownership regression test for journal_entries / habits / habit_check_ins.
--
-- Proves a real authenticated request from one user is blocked from reading,
-- writing, or deleting another user's rows -- not just that the policy SQL
-- text looks correct. Covers all 3 tables x all 4 operations (12 checks).
-- Testing one direction (user A blocked from user B) is sufficient: the
-- `user_id = auth.uid()` predicate is a symmetric equality check with no
-- asymmetry to expose.
--
-- HOW TO RUN: paste this entire file into the Supabase MCP `execute_sql` tool
-- (or the SQL editor) against the linked project, whenever RLS policies on
-- these tables change. A clean run produces no output. Any regression raises
-- a specific exception naming exactly which table/operation broke.
--
-- SAFETY: everything happens inside one transaction that always rolls back --
-- no real data is created, modified, or read. Two existing `auth.users` rows
-- are borrowed as fixture owners (`user_id` has a live FK to `auth.users`, so
-- synthetic UUIDs are not usable), but neither their real data nor any other
-- user's data is ever touched or read.

BEGIN;

DO $$
DECLARE
  user_a uuid;
  user_b uuid;
  entry_b_id uuid := gen_random_uuid();
  habit_b_id uuid := gen_random_uuid();
  checkin_b_id uuid := gen_random_uuid();
  rows_affected int;
BEGIN
  SELECT id INTO user_a FROM auth.users ORDER BY created_at LIMIT 1 OFFSET 0;
  SELECT id INTO user_b FROM auth.users ORDER BY created_at LIMIT 1 OFFSET 1;

  IF user_a IS NULL OR user_b IS NULL THEN
    RAISE EXCEPTION 'SETUP FAILURE: need at least 2 rows in auth.users to run this test';
  END IF;

  -- Fixtures, all owned by user B. Runs as the elevated role executing this
  -- script, which owns the tables, so RLS does not apply to these inserts.
  -- Must happen before the role switch below -- afterward, this session is
  -- itself subject to the policies being tested.
  INSERT INTO journal_entries (id, user_id, date, text, created_at, updated_at)
    VALUES (entry_b_id, user_b, '2026-01-01', 'user B entry', now(), now());
  INSERT INTO habits (id, user_id, name, type, created_at, updated_at)
    VALUES (habit_b_id, user_b, 'user B habit', 'BINARY', now(), now());
  INSERT INTO habit_check_ins (id, habit_id, user_id, date, value, created_at, updated_at)
    VALUES (checkin_b_id, habit_b_id, user_b, '2026-01-01', 1, now(), now());

  -- Simulate user A's authenticated session -- exactly what PostgREST sets up
  -- per-request based on the caller's JWT before running any query.
  PERFORM set_config(
    'request.jwt.claims',
    json_build_object('sub', user_a, 'role', 'authenticated')::text,
    true
  );
  SET LOCAL ROLE authenticated;

  IF auth.uid() IS DISTINCT FROM user_a THEN
    RAISE EXCEPTION 'SETUP FAILURE: auth.uid() = %, expected %', auth.uid(), user_a;
  END IF;

  -- === journal_entries ===

  IF EXISTS (SELECT 1 FROM journal_entries WHERE id = entry_b_id) THEN
    RAISE EXCEPTION 'RLS FAILURE: user A could SELECT a journal_entries row owned by user B';
  END IF;

  BEGIN
    INSERT INTO journal_entries (id, user_id, date, text, created_at, updated_at)
      VALUES (gen_random_uuid(), user_b, '2026-01-02', 'attack', now(), now());
    RAISE EXCEPTION 'RLS FAILURE: user A could INSERT a journal_entries row claiming to be owned by user B';
  EXCEPTION
    WHEN insufficient_privilege THEN NULL; -- expected: WITH CHECK correctly rejected it
  END;

  UPDATE journal_entries SET text = 'hacked' WHERE id = entry_b_id;
  GET DIAGNOSTICS rows_affected = ROW_COUNT;
  IF rows_affected != 0 THEN
    RAISE EXCEPTION 'RLS FAILURE: user A could UPDATE a journal_entries row owned by user B';
  END IF;

  DELETE FROM journal_entries WHERE id = entry_b_id;
  GET DIAGNOSTICS rows_affected = ROW_COUNT;
  IF rows_affected != 0 THEN
    RAISE EXCEPTION 'RLS FAILURE: user A could DELETE a journal_entries row owned by user B';
  END IF;

  -- === habits ===

  IF EXISTS (SELECT 1 FROM habits WHERE id = habit_b_id) THEN
    RAISE EXCEPTION 'RLS FAILURE: user A could SELECT a habits row owned by user B';
  END IF;

  BEGIN
    INSERT INTO habits (id, user_id, name, type, created_at, updated_at)
      VALUES (gen_random_uuid(), user_b, 'attack', 'BINARY', now(), now());
    RAISE EXCEPTION 'RLS FAILURE: user A could INSERT a habits row claiming to be owned by user B';
  EXCEPTION
    WHEN insufficient_privilege THEN NULL; -- expected
  END;

  UPDATE habits SET name = 'hacked' WHERE id = habit_b_id;
  GET DIAGNOSTICS rows_affected = ROW_COUNT;
  IF rows_affected != 0 THEN
    RAISE EXCEPTION 'RLS FAILURE: user A could UPDATE a habits row owned by user B';
  END IF;

  DELETE FROM habits WHERE id = habit_b_id;
  GET DIAGNOSTICS rows_affected = ROW_COUNT;
  IF rows_affected != 0 THEN
    RAISE EXCEPTION 'RLS FAILURE: user A could DELETE a habits row owned by user B';
  END IF;

  -- === habit_check_ins ===

  IF EXISTS (SELECT 1 FROM habit_check_ins WHERE id = checkin_b_id) THEN
    RAISE EXCEPTION 'RLS FAILURE: user A could SELECT a habit_check_ins row owned by user B';
  END IF;

  BEGIN
    INSERT INTO habit_check_ins (id, habit_id, user_id, date, value, created_at, updated_at)
      VALUES (gen_random_uuid(), habit_b_id, user_b, '2026-01-03', 1, now(), now());
    RAISE EXCEPTION 'RLS FAILURE: user A could INSERT a habit_check_ins row claiming to be owned by user B';
  EXCEPTION
    WHEN insufficient_privilege THEN NULL; -- expected
  END;

  UPDATE habit_check_ins SET value = 0 WHERE id = checkin_b_id;
  GET DIAGNOSTICS rows_affected = ROW_COUNT;
  IF rows_affected != 0 THEN
    RAISE EXCEPTION 'RLS FAILURE: user A could UPDATE a habit_check_ins row owned by user B';
  END IF;

  DELETE FROM habit_check_ins WHERE id = checkin_b_id;
  GET DIAGNOSTICS rows_affected = ROW_COUNT;
  IF rows_affected != 0 THEN
    RAISE EXCEPTION 'RLS FAILURE: user A could DELETE a habit_check_ins row owned by user B';
  END IF;

  RAISE NOTICE 'RLS ownership test passed: all 12 checks correctly blocked cross-user access';
END $$;

ROLLBACK;
