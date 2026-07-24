function requireEnv(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new Error(`缺少环境变量: ${name}`);
  }
  return value;
}

export function getCasCredentials() {
  return {
    username: requireEnv('CAS_USERNAME'),
    password: requireEnv('CAS_PASSWORD'),
  };
}

export function getCasSuccessUrl(): string | undefined {
  const value = process.env.CAS_SUCCESS_URL?.trim();
  return value || undefined;
}
