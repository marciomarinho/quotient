#!/usr/bin/env bash
# Seed data for the demo. Tenants are seeded by Flyway (V4), pricing plans by the
# rating-engine config, and the meter catalog by the gateway config — so seeding
# is already baked into the services' startup. This script just reports that.
set -euo pipefail
echo "Demo tenants (Flyway V4): Acme, Globex, Initech"
echo "Pricing plans (rating-engine config): standard (tiered), growth (volume)"
echo "Meter catalog (gateway config): llm.tokens.input/output, llm.requests, storage.gb"
echo "Nothing to seed manually — start the stack with 'make up' + apps profile."
