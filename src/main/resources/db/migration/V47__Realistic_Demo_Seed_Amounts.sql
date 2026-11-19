-- Reserved version: cosmetic demo seed changes must not rewrite persisted business data.
-- Seed IDs can already have payments, deposits, checkouts or settlements attached.
-- Keep all amounts and profiles intact, including on fresh installations.
-- If the previous V47 ran anywhere, reconcile that environment before deploying this revision;
-- do not repair its Flyway checksum or guess historical amounts automatically.
SELECT 1;
