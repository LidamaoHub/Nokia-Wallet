package org.bouncycastle.util;
/** Minimal compatibility shim: only operations used by the pinned sources. */
public final class Arrays {
 public static void fill(int[] a,int v){for(int i=0;i<a.length;i++)a[i]=v;}
 public static void fill(byte[] a,byte v){for(int i=0;i<a.length;i++)a[i]=v;}
 public static int[] clone(int[] a){if(a==null)return null;int[] b=new int[a.length];System.arraycopy(a,0,b,0,a.length);return b;}
}
