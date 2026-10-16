package com.cushionlab.autoguide;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.media.projection.MediaProjectionConfig;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** Lightweight phone-only control panel; overlays never intercept touches. */
public final class MainActivity extends Activity {
    private static final int CAPTURE_REQUEST=701;
    private SharedPreferences prefs;
    private LinearLayout column;
    private TextView captureStatus;
    private final android.os.Handler statusHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable statusPoll=new Runnable(){
        @Override public void run(){
            if(captureStatus!=null)captureStatus.setText(prefs.getString("capture_status","Capture is stopped"));
            statusHandler.postDelayed(this,1000);
        }
    };
    @Override protected void onResume(){super.onResume();statusHandler.post(statusPoll);}
    @Override protected void onPause(){statusHandler.removeCallbacks(statusPoll);super.onPause();}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("auto_guide",MODE_PRIVATE);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(14,21,29));
        column=new LinearLayout(this);
        column.setPadding(dp(18),dp(26),dp(18),dp(20));
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(column);
        setContentView(scroll);
        label("CUSHION LAB · AUTO GUIDE 4.1",22,0xFF7AFFB4);
        label("8 Ball Pool — automatic screen capture + multi-cushion guideline",15,Color.WHITE);
        label("No root • No internet • A separate overlay, not a modified game",13,0xFFA6B6C7);
        label("1  Allow display over other apps.\n2  Start capture and approve Android's screen-sharing dialog.\n3  Open 8 Ball Pool in landscape.\n4  Use these settings to adjust the prediction.",15,0xFFE0E8EA);
        captureStatus=label(prefs.getString("capture_status","Capture is stopped"),14,0xFFFFD78E);
        label("A status strip should appear even if no table is found. If Android offers a choice, share the entire screen. If the strip is missing only over the game, the game or system may be hiding overlays.",13,0xFFCAD4DC);
        Button permission=button("1 · ALLOW OVERLAY PERMISSION");
        permission.setOnClickListener(v->{
            Intent i=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()));
            startActivity(i);
        });
        Button capture=button("2 · START SCREEN GUIDANCE");
        capture.setOnClickListener(v->{
            if(!Settings.canDrawOverlays(this)){
                label("Enable the overlay permission first, then return.",13,0xFFFFCC66);return;
            }
            MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            Intent request;
            if(Build.VERSION.SDK_INT>=34){
                request=manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
            }else request=manager.createScreenCaptureIntent();
            startActivityForResult(request,CAPTURE_REQUEST);
        });
        Button stop=button("STOP OVERLAY & CAPTURE");
        stop.setOnClickListener(v->{
            Intent i=new Intent(this,CaptureService.class).setAction(CaptureService.ACTION_STOP);
            startService(i);
        });
        label("MANUAL GUIDANCE",16,0xFF7AFFB4);
        Switch manualSwitch=new Switch(this);
        manualSwitch.setText("Use manually positioned table and cue ball");
        manualSwitch.setTextColor(Color.WHITE);
        manualSwitch.setChecked(prefs.getBoolean("manual_mode",false));
        manualSwitch.setOnCheckedChangeListener((v,checked)->prefs.edit().putBoolean("manual_mode",checked).apply());
        column.addView(manualSwitch);
        label("If automatic detection finds nothing, enable this to draw a guide. Set the table edges, cue-ball position and Angle yourself. This mode does not track the game or other balls. Values use the current screen orientation; calibrate for landscape.",13,0xFFCAD4DC);
        seek("manual_left",12,0,45,"% of screen",false);
        seek("manual_top",15,0,45,"% of screen",false);
        seek("manual_width",76,30,100,"% of screen",false);
        seek("manual_height",70,25,100,"% of screen",false);
        seek("cue_x",30,0,100,"% across table",false);
        seek("cue_y",50,0,100,"% down table",false);
        label("WINDOW OPACITY",16,0xFF7AFFB4);
        seek("opacity",45,10,70,"%",false);
        label("TABLE ALIGNMENT",16,0xFF7AFFB4);
        label("Automatic detection stays active; these fine-tuning controls move/scale the displayed guide only.",13,0xFFCAD4DC);
        seek("table_width",100,75,125,"%",false);
        seek("table_height",100,75,125,"%",false);
        seek("table_shift_x",100,0,200,"",false);
        seek("table_shift_y",100,0,200,"",false);
        label("MAXIMUM CUSHION BOUNCES",16,0xFF7AFFB4);
        seek("bounces",4,0,12,"",false);
        label("SHOT DIRECTION",16,0xFF7AFFB4);
        @SuppressWarnings("deprecation")
        Switch autoSwitch=new Switch(this);
        autoSwitch.setText("Automatically detect cue-stick direction");
        autoSwitch.setTextColor(Color.WHITE);
        autoSwitch.setChecked(prefs.getBoolean("auto_aim",true));
        autoSwitch.setOnCheckedChangeListener((buttonView,checked)->prefs.edit().putBoolean("auto_aim",checked).apply());
        column.addView(autoSwitch);
        label("Fallback/manual angle (degrees). Used if cue-stick detection is uncertain.",13,0xFFCAD4DC);
        seek("angle",0,0,359,"°",true);
        label("WHAT IT DOES",16,0xFF7AFFB4);
        label("It looks for the felt, circular balls and cue stick in captured frames. The yellow line predicts cushion reflections. Cyan indicates estimated object-ball motion; dotted lines show pocket directions when a target is found.",14,Color.WHITE);
        label("ACCURACY LIMITS",16,0xFFFFD78E);
        label("Automatic computer vision here is experimental. Colored table skins, menus, animations, other overlays, spin and ball collisions may cause misdetections. It cannot read hidden game physics. Don't trust it as a guaranteed shot predictor.",13,0xFFE8C5A0);
        label("Android 15 requires a fresh screen-capture approval each session. Screenshots stay in memory on your device; this app contains no network permission or analytics.",13,0xFFA6B6C7);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==CAPTURE_REQUEST && resultCode==RESULT_OK && data!=null){
            Intent svc=new Intent(this,CaptureService.class);
            svc.putExtra("captureData",data);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(svc);else startService(svc);
            // Leave the UI in foreground until capture service starts, then user opens the game.
            label("Capture approval received. Open 8 Ball Pool now.",14,0xFF7AFFB4);
        } else if(requestCode==CAPTURE_REQUEST){
            label("Screen capture denied. No overlay started.",14,0xFFFFCC66);
        }
    }
    private void seek(String key,int def,int min,int max,String unit,boolean manual){
        TextView value=label("",14,Color.WHITE);
        SeekBar bar=new SeekBar(this);
        bar.setMax(max-min);
        bar.setProgress(Math.min(max,Math.max(min,prefs.getInt(key,def)))-min);
        value.setText(pretty(key)+": "+(bar.getProgress()+min)+unit);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar seekBar,int progress,boolean fromUser){
                int n=progress+min;
                value.setText(pretty(key)+": "+n+unit);
                prefs.edit().putInt(key,n).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar){}
            @Override public void onStopTrackingTouch(SeekBar seekBar){}
        });
        column.addView(bar);
    }
    private String pretty(String key){
        if(key.equals("manual_left"))return "Table left";
        if(key.equals("manual_top"))return "Table top";
        if(key.equals("manual_width"))return "Manual table width";
        if(key.equals("manual_height"))return "Manual table height";
        if(key.equals("cue_x"))return "Cue-ball horizontal position";
        if(key.equals("cue_y"))return "Cue-ball vertical position";
        if(key.equals("opacity"))return "Overlay opacity";
        if(key.equals("bounces"))return "Rebounds";
        if(key.equals("table_width"))return "Table width";
        if(key.equals("table_height"))return "Table height";
        if(key.equals("table_shift_x"))return "Horizontal shift (100=center)";
        if(key.equals("table_shift_y"))return "Vertical shift (100=center)";
        return "Angle";
    }
    private TextView label(String text,int sp,int color){
        TextView t=new TextView(this);
        t.setText(text);t.setTextSize(sp);t.setTextColor(color);
        t.setPadding(0,dp(10),0,dp(8));
        column.addView(t);return t;
    }
    private Button button(String title){
        Button b=new Button(this);b.setText(title);
        column.addView(b,new LinearLayout.LayoutParams(-1,dp(54)));return b;
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
