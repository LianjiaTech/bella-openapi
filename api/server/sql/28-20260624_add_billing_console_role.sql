UPDATE apikey_role
SET path = JSON_SET(path, '$.included', JSON_ARRAY_APPEND(JSON_EXTRACT(path, '$.included'), '$', '/console/billing/**'))
WHERE role_code IN ('low', 'high')
  AND JSON_VALID(path)
  AND JSON_CONTAINS(JSON_EXTRACT(path, '$.included'), JSON_QUOTE('/console/billing/**')) = 0;
