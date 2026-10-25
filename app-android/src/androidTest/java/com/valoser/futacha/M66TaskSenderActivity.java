package com.valoser.futacha;

import android.app.Activity;
import android.content.ClipData;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.ResultReceiver;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Real foreign-task sender in the test APK's own process; only framework/Java dependencies. */
public class M66TaskSenderActivity extends Activity {
    private ResultReceiver observer;
    private String token;
    private boolean registered;
    private final BroadcastReceiver finishReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (token != null && token.equals(intent.getStringExtra("fixtureToken"))) finishFixture();
        }
    };
    private void acknowledge(int code) {
        if (observer == null) return;
        Bundle data = new Bundle();
        data.putInt("task", getTaskId());
        data.putBoolean("root", isTaskRoot());
        observer.send(code, data);
    }
    private void finishFixture() {
        // A result picker belongs above Main; removing that entire task would destroy the subject.
        if (isTaskRoot()) finishAndRemoveTask(); else finish();
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        observer = getIntent().getParcelableExtra("fixtureObserver");
        token = getIntent().getStringExtra("fixtureToken");
        IntentFilter filter = new IntentFilter(getPackageName() + ".audit.M66_FINISH");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(finishReceiver, filter, Context.RECEIVER_EXPORTED);
        else registerReceiver(finishReceiver, filter);
        registered = true;
        acknowledge(1);
        if (getIntent().getBooleanExtra("finishFixture", false)) { finishFixture(); return; }
        renderContent();
        Log.i("AuditM66", "sender created task=" + getTaskId() + " uid=" + android.os.Process.myUid());
    }
    private void renderContent() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        TextView info = new TextView(this);
        info.setText("M66 foreign sender task=" + getTaskId());
        layout.addView(info);
        Button send = new Button(this);
        boolean picker = getIntent().getBooleanExtra("pickerFixture", false);
        send.setText(picker ? "M66 return picker" : "M66 send once");
        send.setOnClickListener(view -> {
            if (picker) {
                Uri uri = Uri.parse(getIntent().getStringExtra("attachmentUri"));
                Intent result = new Intent().setData(uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                result.setClipData(ClipData.newRawUri("audit-m66-picker", uri));
                setResult(RESULT_OK, result);
                finish();
            } else send();
        });
        layout.addView(send);
        setContentView(layout);
    }
    @Override public void onResume() {
        super.onResume();
        acknowledge(2);
    }
    @Override public void onDestroy() {
        if (registered) unregisterReceiver(finishReceiver);
        super.onDestroy();
        acknowledge(3);
    }
    private void send() {
        Intent config = getIntent();
        Intent outgoing;
        if (config.getBooleanExtra("viewFixture", false)) {
            outgoing = new Intent(Intent.ACTION_VIEW, Uri.parse("futacha://ai?action=open_board_list"));
        } else {
            Uri uri = Uri.parse(config.getStringExtra("attachmentUri"));
            outgoing = new Intent(Intent.ACTION_SEND).setType("image/jpeg")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            outgoing.setClipData(ClipData.newRawUri("audit-m66", uri));
        }
        // Resolved like a real share sheet or browser: by the app's intent filters (ExternalIntentActivity, M79).
        outgoing.setPackage(config.getStringExtra("targetPackage"));
        outgoing.putExtra("auditM66SenderTask", getTaskId());
        Log.i("AuditM66", "sender dispatch task=" + getTaskId() + " flags=" + outgoing.getFlags() + " action=" + outgoing.getAction());
        // Deliberately no NEW_TASK/CLEAR_TOP: this is an ordinary external Activity share/open.
        startActivity(outgoing);
    }
    @Override public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Log.i("AuditM66", "sender newIntent task=" + getTaskId() + " finish=" + intent.getBooleanExtra("finishFixture", false));
        if (intent.getBooleanExtra("finishFixture", false)) finishFixture();
        else renderContent();
    }
}
