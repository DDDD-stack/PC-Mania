-- The games a custom-build customer means to play: asked on the form and sent in the admin notification.
ALTER TABLE build_request ADD COLUMN games VARCHAR(300);
