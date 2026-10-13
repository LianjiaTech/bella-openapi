export function getBackendOrigin(): string {
  const configuredOrigin =
    process.env.BACKEND_API_URL ||
    process.env.NEXT_PUBLIC_API_ORIGIN ||
    process.env.NEXT_PUBLIC_API_BASE_URL;

  if (configuredOrigin) {
    return configuredOrigin.replace(/\/$/, '');
  }

  const host = process.env.NEXT_PUBLIC_API_HOST || 'localhost:8080';
  return (/^https?:\/\//.test(host) ? host : `http://${host}`).replace(/\/$/, '');
}
