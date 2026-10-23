'use client';
import * as TabsPrimitive from '@radix-ui/react-tabs';
import type {ComponentProps} from 'react';
import {cn} from '@/lib/utils';
export const Tabs=TabsPrimitive.Root;
export function TabsList({className,...props}:ComponentProps<typeof TabsPrimitive.List>){return <TabsPrimitive.List className={cn('inline-flex h-9 items-center gap-1 rounded-lg bg-muted p-1 text-muted-foreground',className)} {...props}/>;}
export function TabsTrigger({className,...props}:ComponentProps<typeof TabsPrimitive.Trigger>){return <TabsPrimitive.Trigger className={cn('inline-flex h-7 items-center justify-center rounded-md px-3 text-sm font-medium outline-none focus-visible:ring-2 focus-visible:ring-ring data-[state=active]:bg-background data-[state=active]:text-foreground data-[state=active]:shadow-sm',className)} {...props}/>;}
export function TabsContent({className,...props}:ComponentProps<typeof TabsPrimitive.Content>){return <TabsPrimitive.Content className={cn('mt-5 outline-none focus-visible:ring-2 focus-visible:ring-ring',className)} {...props}/>;}
