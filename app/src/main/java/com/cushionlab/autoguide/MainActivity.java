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
        label("CUSHION LAB · AUTO GUIDE 4.3 TEST",22,0xFF7AFFB4);
        label("Extends the game's visible white guide lines with cushion rebounds",15,Color.WHITE);
        label("No root • No internet • A separate overlay, not a modified game",13,0xFFA6B6C7);
        label("1  Allow display over other apps.\n2  Start capture and share the entire screen.\n3  Open the game in landscape and aim normally.\n4  The pale-white extensions appear from the game's guide lines.",15,0xFFE0E8EA);
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
            prefs.edit().putBoolean("manual_mode",false).apply();
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
        label("WINDOW OPACITY",16,0xFF7AFFB4);
        seek("opacity",45,10,70,"%",false);
        label("MAXIMUM CUSHION BOUNCES",16,0xFF7AFFB4);
        seek("bounces",3,0,12,"",false);
        label("VISUAL GUIDE TUNING",16,0xFF7AFFB4);
        seek("guide_width",17,10,40,"%",false);
        seek("rail_inset",100,50,160,"%",false);
        signedSeek("cushion_bias",0,-20,20,"° per bounce");
        signedSeek("spin_estimate",0,-10,10," visual estimate");
        Switch debugSwitch=new Switch(this);
        debugSwitch.setText("Show detector debug overlay");debugSwitch.setTextColor(Color.WHITE);
        debugSwitch.setChecked(prefs.getBoolean("debug_overlay",true));
        debugSwitch.setOnCheckedChangeListener((v,checked)->prefs.edit().putBoolean("debug_overlay",checked).apply());
        column.addView(debugSwitch);
        label("WHAT IT DOES",16,0xFF7AFFB4);
        label("It reads up to two white guide branches and extends them. Cushion bias changes the reflected angle; Spin estimate adds a visual correction after each cushion. Debug mode shows the detected table and ray starts.",14,Color.WHITE);
        label("ACCURACY LIMITS",16,0xFFFFD78E);
        label("The extensions are geometric estimates. Spin, cushion response, moving animation frames and partially hidden guide lines can still shift the real path. When no strong white guide is visible, the app intentionally draws nothing.",13,0xFFE8C5A0);
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
    private void signedSeek(String key,int def,int min,int max,String unit){seek(key,def,min,max,unit,false);}
    private String pretty(String key){
        if(key.equals("manual_left"))return "Table left";
        if(key.equals("manual_top"))return "Table top";
        if(key.equals("manual_width"))return "Manual table width";
        if(key.equals("manual_height"))return "Manual table height";
        if(key.equals("cue_x"))return "Cue-ball horizontal position";
        if(key.equals("cue_y"))return "Cue-ball vertical position";
        if(key.equals("opacity"))return "Overlay opacity";
        if(key.equals("bounces"))return "Rebounds";
        if(key.equals("guide_width"))return "Guide thickness";
        if(key.equals("rail_inset"))return "Cushion contact inset";
        if(key.equals("cushion_bias"))return "Cushion angle bias";
        if(key.equals("spin_estimate"))return "Spin compensation";
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
