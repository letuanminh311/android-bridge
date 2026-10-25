package com.example.bridge_lib;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

/**
 * Layout (little-endian):
 *   0 : int magic 0xCAFE0001
 *   4 : int sequence (lẻ = đang ghi, chẵn = xong)
 *   8 : int độ dài payload
 *   12: int CRC32 payload
 *   16: payload (tối đa 1 MB)
 */
public final class ByteBridge {
    public static final String TAG = "ByteBridge";
    public static final int MAGIC = 0xCAFE0001;
    public static final int HEADER_SIZE = 16;
    public static final int MAX_PAYLOAD = 1024 * 1024;

    private static final int OFF_MAGIC = 0;
    private static final int OFF_SEQ = 4;
    private static final int OFF_LEN = 8;
    private static final int OFF_CRC = 12;

    private static ByteBuffer buffer;           // static: giữ vùng nhớ sống suốt process
    private static int seq = 0;
    private static final CRC32 crc = new CRC32();
    private static volatile UpdateListener listener;

    private ByteBridge() {}

    public static synchronized void init() {
        if (buffer != null) return;
        buffer = ByteBuffer.allocateDirect(HEADER_SIZE + MAX_PAYLOAD)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(OFF_MAGIC, MAGIC);
        buffer.putInt(OFF_SEQ, 0);
        buffer.putInt(OFF_LEN, 0);
        buffer.putInt(OFF_CRC, 0);
        seq = 0;
        fence();
        Log.d(TAG, "init capacity=" + buffer.capacity());
    }

    /** C# lấy ByteBuffer này rồi dùng AndroidJNI.GetDirectBufferAddress. */
    public static synchronized ByteBuffer getBuffer() {
        init();
        return buffer;
    }

    public static void setListener(UpdateListener l) {
        listener = l;
        Log.d(TAG, "setListener " + (l != null));
    }

    /** Ghi data rồi báo cho listener (ngoài khối synchronized). */
    public static void publish(byte[] data) {
        publish(data, data.length);
    }

    public static void publish(byte[] data, int length) {
        doPublish(data, length);
        UpdateListener l = listener;
        if (l != null) {
            try {
                l.notifyUpdateShareMemory();
            } catch (Throwable t) {
                Log.e(TAG, "listener lỗi", t);
            }
        }
    }

    private static synchronized void doPublish(byte[] data, int length) {
        if (length < 0 || length > data.length || length > MAX_PAYLOAD) {
            throw new IllegalArgumentException("length không hợp lệ: " + length);
        }
        init();

        crc.reset();
        crc.update(data, 0, length);
        int crcValue = (int) crc.getValue();

        seq++;                                   // lẻ: đang ghi
        buffer.putInt(OFF_SEQ, seq);
        fence();

        buffer.putInt(OFF_LEN, length);
        buffer.putInt(OFF_CRC, crcValue);
        ByteBuffer dst = buffer.duplicate();
        dst.position(HEADER_SIZE);
        dst.put(data, 0, length);

        fence();
        seq++;                                   // chẵn: xong
        buffer.putInt(OFF_SEQ, seq);
    }

    // ---------- 3 getter đối chiếu (có COPY / reflection) ----------

    /** Địa chỉ native của vùng nhớ. Trả 0 nếu máy chặn reflection. */
    public static synchronized long getAddressShareMemory() {
        init();
        try {
            Field f = java.nio.Buffer.class.getDeclaredField("address");
            f.setAccessible(true);
            return f.getLong(buffer);
        } catch (Throwable t) {
            Log.w(TAG, "không lấy được address bằng reflection: " + t);
            return 0L;
        }
    }

    /** Copy payload hiện tại ra byte[] (chỉ để đối chiếu và đo baseline). */
    public static synchronized byte[] getDataShareMemory() {
        init();
        int len = buffer.getInt(OFF_LEN);
        byte[] out = new byte[len];
        ByteBuffer src = buffer.duplicate();
        src.position(HEADER_SIZE);
        src.get(out, 0, len);
        return out;
    }

    public static synchronized int getLenghtShareMemory() {
        init();
        return buffer.getInt(OFF_LEN);
    }

    public static synchronized int getSequence() { return seq; }

    public static String ping() { return "hello from ByteBridge"; }

    // ---------- memory fence ----------
    private static final Object UNSAFE;
    private static final Method FULL_FENCE;
    private static final AtomicInteger FALLBACK = new AtomicInteger();

    static {
        Object u = null;
        Method m = null;
        try {
            Class<?> c = Class.forName("sun.misc.Unsafe");
            Field f = c.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            u = f.get(null);
            m = c.getMethod("fullFence");
        } catch (Throwable t) {
            u = null;
            m = null;
        }
        UNSAFE = u;
        FULL_FENCE = m;
        Log.d(TAG, "fence mode = " + (m != null ? "Unsafe.fullFence" : "AtomicInteger fallback"));
    }

    private static void fence() {
        if (FULL_FENCE != null) {
            try { FULL_FENCE.invoke(UNSAFE); return; } catch (Throwable ignored) {}
        }
        FALLBACK.incrementAndGet();
    }
}