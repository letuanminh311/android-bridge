package com.example.bridge_lib;

import android.util.Log;

import java.util.Arrays;
import java.util.Random;

/** Giả lập nguồn data: bắn mảng byte random vào ByteBridge. */
public final class DemoWriter {
    public static final int MODE_NORMAL = 0; // mỗi 5 s, 64 KB
    public static final int MODE_HEAVY = 1;  // mỗi 5 s, 1 MB
    public static final int MODE_STRESS = 2; // liên tục, 1 MB

    private static Thread thread;
    private static volatile boolean running = false;
    private static volatile long writeCount = 0;
    private static volatile byte[] lastSent = new byte[0];

    private DemoWriter() {}

    /** 64 byte đầu của mảng vừa gửi (để hiển thị đối chiếu). */
    public static byte[] getLastSent() { return lastSent; }

    public static synchronized void start(final int mode) {
        stop();
        ByteBridge.init();

        final int size = (mode == MODE_NORMAL) ? 64 * 1024 : ByteBridge.MAX_PAYLOAD;
        final long sleepMs = (mode == MODE_STRESS) ? 0 : 5000;

        running = true;
        writeCount = 0;
        thread = new Thread(new Runnable() {
            @Override public void run() {
                byte[] data = new byte[size];
                Random rnd = new Random();
                int counter = 0;
                long lastLog = 0;
                while (running) {
                    rnd.nextBytes(data);                       // dữ liệu ngẫu nhiên
                    lastSent = Arrays.copyOf(data, Math.min(size, 64));
                    ByteBridge.publish(data);                  // ghi xong tự báo listener
                    counter++;
                    writeCount++;

                    long now = System.currentTimeMillis();
                    if (sleepMs > 0 || now - lastLog >= 1000) {
                        lastLog = now;
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < 16 && i < size; i++) {
                            sb.append(String.format("%02X ", data[i]));
                        }
                        Log.d(ByteBridge.TAG, "GỬI #" + counter + " seq=" + ByteBridge.getSequence()
                                + " len=" + size + " 16B đầu: " + sb);
                    }
                    if (sleepMs > 0) {
                        try { Thread.sleep(sleepMs); }
                        catch (InterruptedException e) { return; }
                    }
                }
            }
        }, "ByteBridge-DemoWriter");
        thread.start();
        Log.d(ByteBridge.TAG, "DemoWriter start mode=" + mode + " size=" + size);
    }

    public static synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try { thread.join(500); } catch (InterruptedException ignored) {}
            thread = null;
            Log.d(ByteBridge.TAG, "DemoWriter stop");
        }
    }

    public static boolean isRunning() { return running; }
    public static long getWriteCount() { return writeCount; }
}