'use client';
import * as React from 'react';
import * as Primitive from '@radix-ui/react-dialog';
import {X} from 'lucide-react';
import {cn} from '@/lib/utils';
export const Dialog=Primitive.Root,DialogTitle=Primitive.Title,DialogDescription=Primitive.Description;
export function DialogContent({className,children,...props}:React.ComponentProps<typeof Primitive.Content>){return <Primitive.Portal><Primitive.Overlay className="fixed inset-0 z-50 bg-black/40"/><Primitive.Content className={cn('fixed left-1/2 top-1/2 z-50 max-h-[90dvh] w-[calc(100%-2rem)] max-w-lg -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-xl border bg-background p-6 shadow-lg outline-none',className)} {...props}>{children}<Primitive.Close aria-label="关闭" className="absolute right-4 top-4 rounded-sm p-1 text-muted-foreground hover:text-foreground focus-visible:outline-2"><X size={18}/></Primitive.Close></Primitive.Content></Primitive.Portal>;}
