SET NAMES utf8mb4;

INSERT INTO endpoint (endpoint, endpoint_code, endpoint_name, maintainer_code, maintainer_name, status, cuid, cu_name, muid, mu_name)
VALUES ('/v1/audio/tts/stream', 'ep-7cbbbd29-53a2-4b07-9fb4-0ac1fc75ac0d', N'流式语音合成', '0', 'system', 'active', 0, 'system', 0, 'system')
ON DUPLICATE KEY UPDATE endpoint_name=N'流式语音合成', status='active';

INSERT INTO endpoint_category_rel (endpoint, category_code, cuid, cu_name, muid, mu_name)
VALUES ('/v1/audio/tts/stream', '0002-0001', 0, 'system', 0, 'system')
ON DUPLICATE KEY UPDATE category_code='0002-0001';
