ALTER TABLE customer_profiles
    ADD COLUMN cccd_front_image_key VARCHAR(512),
    ADD COLUMN cccd_back_image_key VARCHAR(512),
    ADD COLUMN selfie_image_key VARCHAR(512),
    ADD COLUMN address VARCHAR(512);