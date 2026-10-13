import * as fs from 'fs';
import * as path from 'path';

const authDir = path.join(__dirname, '..', '.auth');
const authFile = path.join(authDir, 'user.json');

export function ensureAuthDir(): void {
  if (!fs.existsSync(authDir)) {
    fs.mkdirSync(authDir, { recursive: true });
  }
}

export function getAuthFilePath(): string {
  return authFile;
}
