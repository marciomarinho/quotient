-- The three canonical demo tenants. These ids are fixed constants shared with
-- the Keycloak realm (tenant_id claim) and the seed/demo scripts, so they live
-- in the schema as structural anchors rather than in an ad-hoc seed step.
-- Per-tenant pricing plans and meter catalogs are loaded by `make seed`.
INSERT INTO tenant (id, name, plan_code) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Acme',    'standard'),
    ('22222222-2222-2222-2222-222222222222', 'Globex',  'growth'),
    ('33333333-3333-3333-3333-333333333333', 'Initech', 'standard')
ON CONFLICT (id) DO NOTHING;
