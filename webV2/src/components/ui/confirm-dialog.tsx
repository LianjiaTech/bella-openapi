import React from 'react';
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
} from "@/components/ui/alert-dialog";

interface ConfirmDialogProps {
    isOpen: boolean;
    onClose: () => void;
    onConfirm: () => void;
    title: string;
    description: string;
    confirmText?: string;
    cancelText?: string;
}

export function ConfirmDialog({
                                  isOpen,
                                  onClose,
                                  onConfirm,
                                  title,
                                  description,
                                  confirmText = "确认",
                                  cancelText = "取消"
                              }: ConfirmDialogProps) {
    return (
        <AlertDialog open={isOpen} onOpenChange={onClose}>
            <AlertDialogContent className="bg-white rounded-lg shadow-lg border border-gray-200">
                <AlertDialogHeader>
                    <AlertDialogTitle className="text-lg font-semibold text-gray-900">{title}</AlertDialogTitle>
                    <AlertDialogDescription className="text-sm text-gray-600">{description}</AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter className="space-x-2">
                    <AlertDialogCancel
                        onClick={onClose}
                        className="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-gray-500 rounded-md"
                    >
                        {cancelText}
                    </AlertDialogCancel>
                    <AlertDialogAction
                        onClick={onConfirm}
                        className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 rounded-md"
                    >
                        {confirmText}
                    </AlertDialogAction>
                </AlertDialogFooter>
            </AlertDialogContent>
        </AlertDialog>
    );
}
