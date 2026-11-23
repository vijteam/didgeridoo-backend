ALTER TABLE space_departure_requests
    ADD COLUMN allow_automatic_deletion boolean NOT NULL DEFAULT true;
