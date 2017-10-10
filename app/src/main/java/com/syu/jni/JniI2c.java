package com.syu.jni;

@SuppressWarnings("JniMissingFunction")
public class JniI2c {
    static {
        System.loadLibrary("jni_i2c");
    }

    public static native int open(String path);
    public static native int close(int filedes);
    public static native int readRk(int filedes, int slaveaddress, int registeraddress);
    public static native int write(int filedes, int slaveaddress, int registeraddress, int value);
    public static native int writeRk(int filedes, int slaveaddress, int registeraddress, int value);
}
