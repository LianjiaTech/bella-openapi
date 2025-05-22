import React, {useEffect, useState} from 'react';
import {getApiKeyBalance} from '@/lib/api/apikey';
import {ApiKeyBalance} from '@/lib/types/openapi';
import {Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle,} from "@/components/ui/dialog";
import {Button} from '@/components/ui/button';
import {Badge} from '@/components/ui/badge';
import {AlertCircle, CheckCircle2} from 'lucide-react';

interface ApiKeyBalanceDialogProps {
  code: string;
  isOpen: boolean;
  onClose: () => void;
}

interface ApiKeyBalanceIndicatorProps {
  code: string;
}

export const ApiKeyBalanceIndicator: React.FC<ApiKeyBalanceIndicatorProps> = ({ code }) => {
  const [balance, setBalance] = useState<ApiKeyBalance | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<boolean>(false);
  const [showDialog, setShowDialog] = useState<boolean>(false);

  useEffect(() => {
    if (code) {
      setLoading(true);
      setError(false);
      getApiKeyBalance(code)
        .then((data) => {
          if (data) {
            setBalance(data);
          } else {
            setError(true);
          }
        })
        .catch(() => {
          setError(true);
        })
        .finally(() => {
          setLoading(false);
        });
    }
  }, [code]);

  const handleClick = () => {
    setShowDialog(true);
  };

  if (loading) {
    return <div className="flex justify-center"><div className="h-5 w-5 animate-pulse bg-gray-200 rounded-full"></div></div>;
  }

  if (error || !balance) {
    return <div className="flex justify-center"><Badge variant="outline" className="bg-gray-100 text-gray-500 cursor-default">未知</Badge></div>;
  }

  const usagePercentage = (balance.cost / balance.quota) * 100;

  return (
    <>
      <div className="flex justify-center">
        <Badge
          className={`${usagePercentage >= 80 ? 'bg-red-100 text-red-800' : 'bg-green-100 text-green-800'} flex items-center gap-1 cursor-pointer`}
          onClick={handleClick}
        >
          {usagePercentage >= 80 ? (
            <>
              <AlertCircle className="h-3 w-3" /> 余额紧张({Math.round(usagePercentage)}%)
            </>
          ) : (
            <>
              <CheckCircle2 className="h-3 w-3" /> 余额充足({Math.round(usagePercentage)}%)
            </>
          )}
        </Badge>
      </div>
      <ApiKeyBalanceDialog code={code} isOpen={showDialog} onClose={() => setShowDialog(false)} />
    </>
  );
};

export const ApiKeyBalanceDialog: React.FC<ApiKeyBalanceDialogProps> = ({
  code,
  isOpen,
  onClose
}) => {
  const [balance, setBalance] = useState<ApiKeyBalance | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen && code) {
      setLoading(true);
      setError(null);
      getApiKeyBalance(code)
        .then((data) => {
          if (data) {
            setBalance(data);
          } else {
            setError('无法获取余额信息');
          }
        })
        .catch(err => {
          console.error('Failed to fetch balance:', err);
          setError('获取余额信息时发生错误');
        })
        .finally(() => {
          setLoading(false);
        });
    }
  }, [isOpen, code]);

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="sm:max-w-md bg-white dark:bg-gray-800">
        <DialogHeader>
          <DialogTitle>API Key 余额详情</DialogTitle>
          <DialogDescription>当前月份的使用情况和剩余额度</DialogDescription>
        </DialogHeader>
        <div className="py-4">
          {loading ? (
            <div className="flex justify-center items-center p-8">
              <div className="animate-spin rounded-full h-8 w-8 border-4 border-blue-500 border-t-transparent"></div>
            </div>
          ) : error ? (
            <div className="flex items-center justify-center text-red-500 p-4"><AlertCircle className="h-5 w-5 mr-2" />{error}</div>
          ) : balance && (
            <div className="space-y-4 p-4 bg-gray-50 rounded-lg border border-gray-200">
              <div className="grid grid-cols-2 gap-4 text-sm">
                <div className="font-medium text-gray-500">月份</div>
                <div>{balance.month}</div>
                <div className="font-medium text-gray-500">总额度</div>
                <div>{new Intl.NumberFormat("zh-CN", { style: "currency", currency: "CNY" }).format(balance.quota)}</div>
                <div className="font-medium text-gray-500">已使用</div>
                <div>{new Intl.NumberFormat("zh-CN", { style: "currency", currency: "CNY" }).format(balance.cost)}</div>
                <div className="font-medium text-gray-500">剩余额度</div>
                <div className={`font-semibold ${balance.balance < balance.quota * 0.2 ? 'text-red-600' : 'text-green-600'}`}>
                  {new Intl.NumberFormat("zh-CN", { style: "currency", currency: "CNY" }).format(balance.balance)}
                </div>
              </div>
              <div className={`text-sm mt-4 p-2 rounded ${balance.balance < balance.quota * 0.2 ? 'bg-red-50 text-red-800' : 'bg-green-50 text-green-800'}`}>
                {balance.balance < balance.quota * 0.2 ? (
                  <p className="flex items-center"><AlertCircle className="h-4 w-4 mr-2" />余额不足，请考虑增加额度</p>
                ) : (
                  <p>余额充足</p>
                )}
              </div>
            </div>
          )}
        </div>
        <div className="flex justify-end">
          <Button onClick={onClose}>关闭</Button>
        </div>
      </DialogContent>
    </Dialog>
  );
};
