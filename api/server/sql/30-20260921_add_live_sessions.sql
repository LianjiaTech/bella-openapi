SET NAMES utf8mb4;

INSERT INTO endpoint (endpoint, endpoint_code, endpoint_name, maintainer_code, maintainer_name, status, cuid, cu_name, muid, mu_name)
VALUES ('/v1/live/sessions', 'ep-live-sessions-001', N'多模态实时会话', '0', 'system', 'active', 0, 'system', 0, 'system')
ON DUPLICATE KEY UPDATE endpoint_name=N'多模态实时会话', status='active';

INSERT INTO endpoint_category_rel (endpoint, category_code, cuid, cu_name, muid, mu_name)
VALUES ('/v1/live/sessions', '0002-0003', 0, 'system', 0, 'system')
ON DUPLICATE KEY UPDATE category_code='0002-0003';
