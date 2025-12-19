'use client';

import { useLanguage } from '@/components/providers/language-provider';
import { useTheme } from '@/components/providers/theme-provider';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/common/dialog';

interface SettingsDialogProps {
  open: boolean;
  onClose: () => void;
}

export function SettingsDialog({ open, onClose }: SettingsDialogProps) {
  const { language, setLanguage, t } = useLanguage();
  const { theme, setTheme } = useTheme();

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('settingsTitle')}</DialogTitle>
          <DialogDescription>{t('settingsDescription')}</DialogDescription>
        </DialogHeader>

        {/* 设置内容 */}
        <div className="space-y-6">
          {/* 语言设置 */}
          <div>
            <h3 className="text-sm font-medium text-card-foreground mb-3">
              {t('language')}
            </h3>
            <div className="space-y-2">
              <label className="flex items-center cursor-pointer group">
                <input
                  type="radio"
                  name="language"
                  checked={language === 'zh-CN'}
                  onChange={() => setLanguage('zh-CN')}
                  className="w-4 h-4 text-primary focus:ring-0 focus:ring-offset-0"
                />
                <span className="ml-3 text-sm text-card-foreground group-hover:text-primary">
                  {t('chinese')}
                </span>
              </label>
              <label className="flex items-center cursor-pointer group">
                <input
                  type="radio"
                  name="language"
                  checked={language === 'en-US'}
                  onChange={() => setLanguage('en-US')}
                  className="w-4 h-4 text-primary focus:ring-0 focus:ring-offset-0"
                />
                <span className="ml-3 text-sm text-card-foreground group-hover:text-primary">
                  {t('english')}
                </span>
              </label>
            </div>
          </div>

          {/* 主题设置 */}
          <div>
            <h3 className="text-sm font-medium text-card-foreground mb-3">
              {t('theme')}
            </h3>
            <div className="space-y-2">
              <label className="flex items-center cursor-pointer group">
                <input
                  type="radio"
                  name="theme"
                  checked={theme === 'light'}
                  onChange={() => setTheme('light')}
                  className="w-4 h-4 text-primary focus:ring-0 focus:ring-offset-0"
                />
                <span className="ml-3 text-sm text-card-foreground group-hover:text-primary">
                  {t('light')}
                </span>
              </label>
              <label className="flex items-center cursor-pointer group">
                <input
                  type="radio"
                  name="theme"
                  checked={theme === 'dark'}
                  onChange={() => setTheme('dark')}
                  className="w-4 h-4 text-primary focus:ring-0 focus:ring-offset-0"
                />
                <span className="ml-3 text-sm text-card-foreground group-hover:text-primary">
                  {t('dark')}
                </span>
              </label>
              <label className="flex items-center cursor-pointer group">
                <input
                  type="radio"
                  name="theme"
                  checked={theme === 'system'}
                  onChange={() => setTheme('system')}
                  className="w-4 h-4 text-primary focus:ring-0 focus:ring-offset-0"
                />
                <span className="ml-3 text-sm text-card-foreground group-hover:text-primary">
                  {t('system')}
                </span>
              </label>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
