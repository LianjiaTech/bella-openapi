'use client'
import * as React from 'react'
import {
    ColumnDef,
    flexRender,
    getCoreRowModel,
    useReactTable,
    RowSelectionState,
} from '@tanstack/react-table'
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '@/components/ui/table'
export const HoverContext = React.createContext<string | null>(null);
interface DataTableProps<TData, TValue> {
    columns: ColumnDef<TData, TValue>[]
    data: TData[]
}
export function DataTable<TData, TValue>({
                                             columns,
                                             data,
                                         }: DataTableProps<TData, TValue>) {
    const [rowSelection, setRowSelection] = React.useState<RowSelectionState>({})
    const [hoveredRowId, setHoveredRowId] = React.useState<string | null>(null);
    const table = useReactTable({
        data,
        columns,
        getCoreRowModel: getCoreRowModel(),
        onRowSelectionChange: setRowSelection,
        state: {
            rowSelection,
        },
    })
    return (
        <HoverContext.Provider value={hoveredRowId}>
            <div className="bg-white text-black text-base">
                <Table>
                    <TableHeader>
                        <TableRow className="h-12 border-b border-gray-200">
                            {table.getHeaderGroups()[0].headers.map((header) => (
                                <TableHead key={header.id} className="text-center font-semibold text-gray-700">
                                    {header.isPlaceholder
                                        ? null
                                        : flexRender(
                                            header.column.columnDef.header,
                                            header.getContext()
                                        )}
                                </TableHead>
                            ))}
                        </TableRow>
                    </TableHeader>
                    <TableBody>
                        {table.getRowModel().rows?.length ? (
                            table.getRowModel().rows.map((row, index) => (
                                <TableRow
                                    key={row.id}
                                    data-state={row.getIsSelected() ? "selected" : undefined}
                                    className={`h-14 transition-colors ${
                                        row.getIsSelected()
                                            ? 'bg-gray-50'
                                            : 'bg-white'
                                    } hover:bg-gray-100 ${
                                        index === table.getRowModel().rows.length - 1
                                            ? 'border-b border-gray-200'
                                            : 'border-b border-gray-100'
                                    }`}
                                    onMouseEnter={() => setHoveredRowId(row.id)}
                                    onMouseLeave={() => setHoveredRowId(null)}
                                >
                                    {row.getVisibleCells().map((cell) => (
                                        <TableCell key={cell.id} className="text-center py-4">
                                            {flexRender(cell.column.columnDef.cell, cell.getContext())}
                                        </TableCell>
                                    ))}
                                </TableRow>
                            ))
                        ) : (
                            <TableRow>
                                <TableCell colSpan={columns.length} className="h-24 text-center">
                                    No results.
                                </TableCell>
                            </TableRow>
                        )}
                    </TableBody>
                </Table>
            </div>
        </HoverContext.Provider>
    )
}
