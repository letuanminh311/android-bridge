package com.example.bytebridge;


import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.bridge_lib.ByteBridge;
import com.example.bridge_lib.DemoWriter;

public class MainActivity extends Activity {
    private final JavaBridgeReader reader = new JavaBridgeReader();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView status;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            status.setText(buildStatus());
            ui.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        root.addView(makeButton("Start NORMAL (mỗi 5s, 64KB)", new View.OnClickListener() {
            @Override public void onClick(View v) { startMode(DemoWriter.MODE_NORMAL); }
        }));
        root.addView(makeButton("Start NORMAL (mỗi 5s, 64KB)", new View.OnClickListener() {
            @Override public void onClick(View v) { startMode(DemoWriter.MODE_HEAVY); }
        }));
        root.addView(makeButton("Start STRESS (liên tục, 1MB)", new View.OnClickListener() {
            @Override public void onClick(View v) { startMode(DemoWriter.MODE_STRESS); }
        }));
        root.addView(makeButton("Stop", new View.OnClickListener() {
            @Override public void onClick(View v) {
                DemoWriter.stop();
                reader.stop();
            }
        }));
        root.addView(makeButton("Reset bộ đếm", new View.OnClickListener() {
            @Override public void onClick(View v) { reader.resetCounters(); }
        }));

        status = new TextView(this);
        status.setTypeface(Typeface.MONOSPACE);
        status.setTextSize(13);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(status);
        root.addView(scroll);

        setContentView(root);
        ByteBridge.init();
        status.setText(buildStatus());
    }

    private Button makeButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private void startMode(int mode) {
        reader.stop();
        reader.resetCounters();
        DemoWriter.start(mode);
        reader.start();
    }

    private String buildStatus() {
        return "[Java ghi]\n"
                + "  running   = " + DemoWriter.isRunning() + "\n"
                + "  seq       = " + ByteBridge.getSequence() + "\n"
                + "  writes    = " + DemoWriter.getWriteCount() + "\n\n"
                + "[Reader đọc]\n"
                + "  seq       = " + reader.lastSeq + "\n"
                + "  len       = " + reader.lastLen + "\n"
                + "  crc header= " + String.format("%08X", reader.headerCrc) + "\n"
                + "  crc tính  = " + String.format("%08X", reader.calcCrc) + "\n"
                + "  kết quả   = " + (reader.lastOk ? "KHỚP" : "SAI") + "\n\n"
                + "Số lần đọc : " + reader.reads + "\n"
                + "Đọc lại    : " + reader.retries + "\n"
                + "LỖI        : " + reader.errors + "\n";
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(refresh);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(refresh);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DemoWriter.stop();
        reader.stop();
    }
}
