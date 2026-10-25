package com.example.bytebridge;

import com.example.bridge_lib.ByteBridge;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

/** Reader mô phỏng phía Unity: đọc seqlock, kiểm CRC và pattern. */
public final class JavaBridgeReader {
    // thống kê (volatile để UI thread đọc được)
    public volatile long reads = 0;      // số lần đọc hợp lệ (seq mới)
    public volatile long retries = 0;    // số lần phải đọc lại (đang ghi / seq lệch)
    public volatile long errors = 0;     // số lần CRC hoặc pattern sai
    public volatile int lastSeq = 0;
    public volatile int lastLen = 0;
    public volatile int headerCrc = 0;
    public volatile int calcCrc = 0;
    public volatile boolean lastOk = true;

    private final AtomicInteger fenceVar = new AtomicInteger();
    private volatile boolean running = false;
    private Thread thread;

    private void fence() { fenceVar.incrementAndGet(); }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(new Runnable() {
            @Override public void run() { loop(); }
        }, "ByteBridge-Reader");
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try { thread.join(500); } catch (InterruptedException ignored) {}
            thread = null;
        }
    }

    public void resetCounters() {
        reads = 0; retries = 0; errors = 0;
    }

    private void loop() {
        ByteBuffer src = ByteBridge.getBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer payload = ByteBridge.getBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        byte[] local = new byte[ByteBridge.MAX_PAYLOAD];
        CRC32 crc = new CRC32();
        int lastProcessed = -1;

        while (running) {
            int s1 = src.getInt(4);
            if ((s1 & 1) != 0) { retries++; continue; }      // đang ghi
            if (s1 == lastProcessed || s1 == 0) {             // chưa có dữ liệu mới
                try { Thread.sleep(5); } catch (InterruptedException e) { return; }
                continue;
            }
            fence();
            int len = src.getInt(8);
            int crcHeader = src.getInt(12);
            if (len < 0 || len > ByteBridge.MAX_PAYLOAD) { retries++; continue; }

            payload.position(ByteBridge.HEADER_SIZE);
            payload.get(local, 0, len);
            fence();
            int s2 = src.getInt(4);
            if (s1 != s2) { retries++; continue; }            // bị ghi chồng lúc đọc

            // dữ liệu nhất quán theo seqlock -> kiểm chứng
            crc.reset();
            crc.update(local, 0, len);
            int crcCalc = (int) crc.getValue();

            boolean patternOk = true;
            for (int i = 1; i < len; i++) {
                if (local[i] != (byte) (local[0] + i)) { patternOk = false; break; }
            }
            boolean ok = (crcCalc == crcHeader) && patternOk;
            if (!ok) errors++;

            lastSeq = s1; lastLen = len;
            headerCrc = crcHeader; calcCrc = crcCalc; lastOk = ok;
            lastProcessed = s1;
            reads++;

            try { Thread.sleep(5); } catch (InterruptedException e) { return; }
        }
    }
}
