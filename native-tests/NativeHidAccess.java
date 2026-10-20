package com.wellmosphere.freedsp.cable;
/** Host JNI boundary checks; never accesses USB hardware. */
public class NativeHidAccess {
    private native String[] call(int fd, int iface, int op);
    public static void main(String[] args) {
        System.load(args[0]);
        NativeHidAccess n = new NativeHidAccess();
        int count = 0;
        for (int op=0; op<5; op++) {
            String[] r=n.call(1000000,3,op);
            if (!r[0].equals("-1") || !r[2].equals("EBADF") || !r[4].isEmpty()) throw new AssertionError(java.util.Arrays.toString(r));
            count++;
        }
        for (int iface: new int[]{0,1,2,4,-1}) for (int op: new int[]{1,3,4}) {
            if (!n.call(1000000,iface,op)[2].equals("EINVAL")) throw new AssertionError("Interface guard failed");
            count++;
        }
        if (!n.call(1000000,3,9)[2].equals("EINVAL")) throw new AssertionError("Operation guard failed");
        if (!n.call(-1,3,0)[2].equals("EINVAL")) throw new AssertionError("FD guard failed");
        System.out.println((count+2)+" host JNI checks passed; no hardware accessed.");
    }
}
