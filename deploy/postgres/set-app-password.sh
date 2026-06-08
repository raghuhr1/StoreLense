#!/bin/bash
# Runs after init.sql (alphabetically later: 's' > 'i').
# Sets storelense_app's password to match DB_PASSWORD from the compose env,
# so the application user is always in sync with what Spring Boot expects.
set -e
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "ALTER USER storelense_app WITH PASSWORD '${DB_PASSWORD:-changeme}';"
