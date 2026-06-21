-- RAI-18 — pairing-code auth.
-- Originally this added users/devices/pairing_codes for RAI-18.
-- Those tables were merged into 0000_merged_schema.sql (RAI-15) when both
-- branches landed in main, so this migration is now a no-op preserved purely
-- to keep the journal stable.

SELECT 1;
