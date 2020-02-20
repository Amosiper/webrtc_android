package com.dds.java.voip;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.dds.skywebrtc.CallSession;
import com.dds.skywebrtc.EnumType;
import com.dds.skywebrtc.SkyEngineKit;
import com.dds.skywebrtc.except.NotInitializedException;
import com.dds.skywebrtc.permission.Permissions;
import com.dds.webrtc.R;

import java.util.UUID;

public class CallSingleActivity extends AppCompatActivity implements CallSession.CallSessionCallback {

    public static final String EXTRA_TARGET = "targetId";
    public static final String EXTRA_MO = "isOutGoing";
    public static final String EXTRA_AUDIO_ONLY = "audioOnly";
    public static final String EXTRA_FROM_FLOATING_VIEW = "fromFloatingView";

    private Handler handler = new Handler();
    private boolean isOutgoing;
    private String targetId;
    private boolean isAudioOnly;
    private boolean isFromFloatingView;

    private SkyEngineKit gEngineKit;

    private CallSession.CallSessionCallback currentFragment;


    public static void openActivity(Context context, String targetId, boolean isOutgoing,
                                    boolean isAudioOnly) {
        Intent voip = new Intent(context, CallSingleActivity.class);
        voip.putExtra(CallSingleActivity.EXTRA_MO, isOutgoing);
        voip.putExtra(CallSingleActivity.EXTRA_TARGET, targetId);
        voip.putExtra(CallSingleActivity.EXTRA_AUDIO_ONLY, isAudioOnly);
        voip.putExtra(CallSingleActivity.EXTRA_FROM_FLOATING_VIEW, false);
        if (context instanceof Activity) {
            context.startActivity(voip);
        } else {
            voip.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(voip);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //全屏+锁屏+常亮显示
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(getSystemUiVisibility());
        setContentView(R.layout.activity_single_call);

        try {
            gEngineKit = SkyEngineKit.Instance();
        } catch (NotInitializedException e) {
            finish();
        }
        final Intent intent = getIntent();
        targetId = intent.getStringExtra(EXTRA_TARGET);
        isFromFloatingView = intent.getBooleanExtra(EXTRA_FROM_FLOATING_VIEW, false);
        isOutgoing = intent.getBooleanExtra(EXTRA_MO, false);
        isAudioOnly = intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false);
        if (isFromFloatingView) {
            Intent serviceIntent = new Intent(this, FloatingVoipService.class);
            stopService(serviceIntent);
            init(targetId, false, isAudioOnly);
        } else {
            // 权限检测
            String[] per;
            if (isAudioOnly) {
                per = new String[]{Manifest.permission.RECORD_AUDIO};
            } else {
                per = new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA};
            }
            Permissions.request(this, per, integer -> {
                if (integer == 0) {
                    // 权限同意
                    init(targetId, isOutgoing, isAudioOnly);
                } else {
                    // 权限拒绝
                    CallSingleActivity.this.finish();
                }
            });
        }


    }

    private void init(String targetId, boolean outgoing, boolean audioOnly) {
        Fragment fragment;
        if (audioOnly) {
            fragment = new FragmentAudio();
        } else {
            fragment = new FragmentVideo();
        }

        currentFragment = (CallSession.CallSessionCallback) fragment;
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .add(android.R.id.content, fragment)
                .commit();
        if (outgoing) {
            // 创建会话
            String room = UUID.randomUUID().toString() + System.currentTimeMillis();
            boolean b = gEngineKit.startOutCall(getApplicationContext(), room, targetId, audioOnly);
            if (!b) {
                finish();
                return;
            }
            CallSession session = gEngineKit.getCurrentSession();
            if (session == null) {
                finish();
            } else {
                session.setSessionCallback(this);
            }
        } else {
            CallSession session = gEngineKit.getCurrentSession();
            if (session == null) {
                finish();
            } else {
                session.setSessionCallback(this);
            }
        }

    }

    public SkyEngineKit getEngineKit() {
        return gEngineKit;
    }

    public boolean isOutgoing() {
        return isOutgoing;
    }

    // 显示小窗
    public void showFloatingView() {
        if (!checkOverlayPermission()) {
            return;
        }
        Intent intent = new Intent(this, FloatingVoipService.class);
        intent.putExtra(EXTRA_TARGET, targetId);
        intent.putExtra(EXTRA_AUDIO_ONLY, isAudioOnly);
        intent.putExtra(EXTRA_MO, isOutgoing);
        startService(intent);
        finish();
    }

    // ======================================界面回调================================
    @Override
    public void didCallEndWithReason(EnumType.CallEndReason var1) {
        finish();
    }

    @Override
    public void didChangeState(EnumType.CallState callState) {
        handler.post(() -> currentFragment.didChangeState(callState));
    }

    @Override
    public void didChangeMode(boolean var1) {
        handler.post(() -> currentFragment.didChangeMode(var1));
    }

    @Override
    public void didCreateLocalVideoTrack() {
        handler.post(() -> currentFragment.didCreateLocalVideoTrack());
    }

    @Override
    public void didReceiveRemoteVideoTrack() {
        handler.post(() -> currentFragment.didReceiveRemoteVideoTrack());
    }

    @Override
    public void didError(String var1) {
        finish();
    }


    // ========================================================================================

    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            SettingsCompat.setDrawOverlays(this, true);
            if (!SettingsCompat.canDrawOverlays(this)) {
                Toast.makeText(this, "需要悬浮窗权限", Toast.LENGTH_LONG).show();
                SettingsCompat.manageDrawOverlays(this);
                return false;
            }
        }
        return true;
    }


    @TargetApi(19)
    private static int getSystemUiVisibility() {
        int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        return flags;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}