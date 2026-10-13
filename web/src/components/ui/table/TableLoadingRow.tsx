import { Loader } from "lucide-react";
import { TableCell, TableRow } from "@/components/common/table";

interface TableLoadingRowProps {
    colSpan?: number;
    message?: string;
}

export function TableLoadingRow({ colSpan = 7, message = "加载中..." }: TableLoadingRowProps) {
    return (
        <TableRow>
            <TableCell colSpan={colSpan} className="text-center py-8">
                <div className="flex items-center justify-center text-muted-foreground">
                    <Loader className="h-5 w-5 animate-spin mr-2" />
                    <span>{message}</span>
                </div>
            </TableCell>
        </TableRow>
    );
}
