-- Keep avatars already registered through the admin API; never replace existing choices.
INSERT INTO profile_img (name, img_url, created_at, updated_at)
SELECT '루미', '/avatars/lumi-v1.png', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM profile_img WHERE img_url = '/avatars/lumi-v1.png');
INSERT INTO profile_img (name, img_url, created_at, updated_at)
SELECT '모리', '/avatars/mori-v1.png', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM profile_img WHERE img_url = '/avatars/mori-v1.png');
INSERT INTO profile_img (name, img_url, created_at, updated_at)
SELECT '노바', '/avatars/nova-v1.png', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM profile_img WHERE img_url = '/avatars/nova-v1.png');
INSERT INTO profile_img (name, img_url, created_at, updated_at)
SELECT '오르비', '/avatars/orbi-v1.png', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM profile_img WHERE img_url = '/avatars/orbi-v1.png');
INSERT INTO profile_img (name, img_url, created_at, updated_at)
SELECT '피코', '/avatars/pico-v1.png', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM profile_img WHERE img_url = '/avatars/pico-v1.png');
