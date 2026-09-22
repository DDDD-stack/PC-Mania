-- Supabase publishes every table in an exposed schema through PostgREST, reachable with the
-- "anon" API key. That key is designed to be public - it ships inside browser and phone apps -
-- so a table in an exposed schema with row level security switched off is readable, and often
-- writable, by anyone who has ever seen the site. That is what Supabase's Security Advisor
-- reports as "RLS Disabled in Public".
--
-- PCMania does not use PostgREST at all: the Spring app talks to Postgres over JDBC with a
-- database password. So there are two independent defences, and this file is the second one.
--
--   1. The tables are not in "public". They live in the schema Flyway is pointed at (DB_SCHEMA,
--      default "pcmania"), and Supabase only exposes "public" and "graphql_public" unless
--      someone adds a schema to the API settings by hand. Nothing here is on the API surface.
--
--   2. Row level security is on anyway, with no policies. A table with RLS enabled and no
--      policy returns nothing to everyone except the table owner. So even if this schema were
--      exposed one day, or the anon key were pointed at it, every query would come back empty.
--
-- This does not affect the application. Flyway runs as the role that creates these tables, and
-- in Postgres a table's owner bypasses RLS unless FORCE ROW LEVEL SECURITY is set, which it is
-- not. Connect the app with that same owner role and nothing changes.
--
-- Any table added in a later migration needs its own ENABLE ROW LEVEL SECURITY line here.

ALTER TABLE category          ENABLE ROW LEVEL SECURITY;
ALTER TABLE brand             ENABLE ROW LEVEL SECURITY;
ALTER TABLE product           ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_spec      ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_image     ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders            ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_item        ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_sequence    ENABLE ROW LEVEL SECURITY;
ALTER TABLE build_request     ENABLE ROW LEVEL SECURITY;
ALTER TABLE admin_user        ENABLE ROW LEVEL SECURITY;
ALTER TABLE api_token         ENABLE ROW LEVEL SECURITY;
ALTER TABLE upcoming_product  ENABLE ROW LEVEL SECURITY;
ALTER TABLE upcoming_interest ENABLE ROW LEVEL SECURITY;

-- Take the grants away as well, so the PostgREST roles cannot even see that the schema exists.
-- Guarded by a role check: these roles only exist on Supabase, and the same migration has to
-- keep working against a plain Postgres.
DO $$
DECLARE
    target_schema text := current_schema();
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA %I FROM anon, authenticated', target_schema);
        EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA %I FROM anon, authenticated', target_schema);
        EXECUTE format('REVOKE ALL ON SCHEMA %I FROM anon, authenticated', target_schema);
        EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I REVOKE ALL ON TABLES FROM anon, authenticated', target_schema);
        RAISE NOTICE 'Supabase roles found: revoked anon/authenticated access to schema %', target_schema;
    END IF;
END $$;
