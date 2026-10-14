SET NAMES utf8mb4;

INSERT INTO endpoint (endpoint, endpoint_code, endpoint_name, maintainer_code, maintainer_name, status, cuid, cu_name, muid, mu_name)
VALUES ('/v1/alpha/search', 'ep-alpha-search', N'Alpha Search', '0', 'system', 'active', 0, 'system', 0, 'system')
ON DUPLICATE KEY UPDATE endpoint_code='ep-alpha-search', endpoint_name=N'Alpha Search', status='active';

INSERT INTO endpoint_category_rel (endpoint, category_code, cuid, cu_name, muid, mu_name)
VALUES ('/v1/alpha/search', '0006', 0, 'system', 0, 'system')
ON DUPLICATE KEY UPDATE category_code='0006';
