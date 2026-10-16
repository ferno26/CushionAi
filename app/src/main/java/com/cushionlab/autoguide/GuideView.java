package com.cushionlab.autoguide;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.List;

/** Transparent, permanently non-touchable screen-wide prediction overlay. */
final class GuideView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final SharedPreferences settings;
    private volatile FrameAnalyzer.Scene scene;
    private volatile int frameW,frameH;
    private long lastFrameAt, lastStatusAt;
    private int frames;
    private final Runnable heartbeat=new Runnable(){
        @Override public void run(){invalidate();postDelayed(this,1000);}
    };
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();post(heartbeat);}
    @Override protected void onDetachedFromWindow(){removeCallbacks(heartbeat);super.onDetachedFromWindow();}
    GuideView(Context ctx,SharedPreferences prefs){super(ctx);settings=prefs;setBackgroundColor(0x00000000);}
    void submit(FrameAnalyzer.Scene result,int w,int h){scene=result;frameW=w;frameH=h;lastFrameAt=android.os.SystemClock.elapsedRealtime();frames++;postInvalidate();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        FrameAnalyzer.Scene s=scene;
        boolean manual=settings.getBoolean("manual_mode",false);
        boolean fresh=lastFrameAt>0 && android.os.SystemClock.elapsedRealtime()-lastFrameAt<5000;
        String state=frames==0?"Waiting for screen frames":!fresh?"Capture paused - restart screen sharing":
                s==null||s.confidence<.30f?"Frames OK - table not detected":
                s.cue==null?"Table found - cue ball not detected":"Tracking table and cue ball";
        String error=settings.getString("capture_error","");
        if(!error.isEmpty())state=error;
        drawStatus(c,(manual?"MANUAL GUIDE | ":"AUTO | ")+state+" | frames "+frames);
        // Manual mode is an explicit calibration tool, never inferred game geometry.
        int renderW=frameW,renderH=frameH;
        if(manual){
            renderW=Math.max(1,getWidth());renderH=Math.max(1,getHeight());
            s=manualScene(renderW,renderH);
        }else if(!fresh||s==null||s.confidence<.30f||frameW<1||frameH<1)return;
        c.save(); c.scale(getWidth()/(float)renderW,getHeight()/(float)renderH);
        // Keep the full-screen capture coordinate space and apply only guide calibration.
        float zoomX=Math.max(.75f,Math.min(1.25f,settings.getInt("table_width",100)/100f));
        float zoomY=Math.max(.75f,Math.min(1.25f,settings.getInt("table_height",100)/100f));
        float offsetX=(settings.getInt("table_shift_x",100)-100)*renderW/1000f;
        float offsetY=(settings.getInt("table_shift_y",100)-100)*renderH/1000f;
        c.translate(offsetX,offsetY);
        c.scale(zoomX,zoomY,(s.left+s.right)*.5f,(s.top+s.bottom)*.5f);
        float r=s.radius;
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1.4f,r*.14f));
        p.setPathEffect(null);p.setColor(0xFF57FF88);
        c.drawRect(s.left,s.top,s.right,s.bottom,p);
        // Six pocket markers are derived from the current table boundary.
        float[] hx={s.left,(s.left+s.right)*.5f,s.right,s.left,(s.left+s.right)*.5f,s.right};
        float[] hy={s.top,s.top,s.top,s.bottom,s.bottom,s.bottom};
        p.setStyle(Paint.Style.FILL);p.setColor(0xFF081B13);
        for(int i=0;i<6;i++)c.drawCircle(hx[i],hy[i],r*.57f,p);
        p.setStyle(Paint.Style.STROKE);p.setColor(0xFF57FF88);
        for(int i=0;i<6;i++)c.drawCircle(hx[i],hy[i],r*.57f,p);
        for(Trajectory.Ball b:s.balls){
            p.setPathEffect(null);p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.3f,r*.12f));
            p.setColor(b.cue?0xFFFFFFFF:0xFF6BE7FF);
            c.drawCircle(b.x,b.y,r,p);
        }
        if(s.cue!=null){
            float dx,dy;
            boolean auto=!manual&&settings.getBoolean("auto_aim",true);
            if(auto&&s.directionConfidence>.32f){dx=s.directionX;dy=s.directionY;}
            else {
                float angle=settings.getInt("angle",0);
                dx=(float)Math.cos(Math.toRadians(angle));
                dy=(float)Math.sin(Math.toRadians(angle));
            }
            int bounce=Math.max(0,Math.min(12,settings.getInt("bounces",4)));
            List<Trajectory.Ball> detected=s.balls;
            Trajectory.Path shot=Trajectory.trace(s.cue.x,s.cue.y,dx,dy,
                    s.left+r,s.top+r,s.right-r,s.bottom-r,r,detected,bounce);
            p.setColor(0xFFFFED48);p.setPathEffect(null);p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2f,r*.22f));
            drawPath(c,shot.points);
            if(shot.hitBall>=0){
                Trajectory.Ball target=s.balls.get(shot.hitBall);
                p.setColor(0xCCB5ECFF);p.setStrokeWidth(Math.max(1.2f,r*.10f));
                p.setPathEffect(new DashPathEffect(new float[]{r*.43f,r*.35f},0));
                for(int i=0;i<6;i++)c.drawLine(target.x,target.y,hx[i],hy[i],p);
                p.setColor(0xFF00EDFF);p.setStrokeWidth(Math.max(2f,r*.2f));
                p.setPathEffect(null);drawPath(c,shot.objectPath);
            }
            if(auto&&s.directionConfidence<=.32f){
                p.setStyle(Paint.Style.FILL);p.setColor(0xFFFAEFA7);p.setTextSize(Math.max(11f,r*.82f));
                c.drawText("ANGLE FALLBACK",s.left+r,s.top+r*1.6f,p);
            }
        }
        c.restore();
    }
    private FrameAnalyzer.Scene manualScene(int w,int h){
        FrameAnalyzer.Scene s=new FrameAnalyzer.Scene();
        s.left=w*settings.getInt("manual_left",12)/100f;
        s.top=h*settings.getInt("manual_top",15)/100f;
        s.right=Math.min(w-1,s.left+w*settings.getInt("manual_width",76)/100f);
        s.bottom=Math.min(h-1,s.top+h*settings.getInt("manual_height",70)/100f);
        s.radius=Math.max(2,Math.min(s.right-s.left,s.bottom-s.top)*.031f);
        float x=s.left+s.radius+(s.right-s.left-2*s.radius)*settings.getInt("cue_x",30)/100f;
        float y=s.top+s.radius+(s.bottom-s.top-2*s.radius)*settings.getInt("cue_y",50)/100f;
        s.cue=new Trajectory.Ball(x,y,true,1);s.balls.add(s.cue);s.confidence=1;
        return s;
    }
    private void drawStatus(Canvas c,String text){
        float d=getResources().getDisplayMetrics().density;
        p.setStyle(Paint.Style.FILL);p.setPathEffect(null);p.setTextSize(12*d);
        float x=12*d,y=36*d;
        p.setColor(0xEE101820);
        c.drawRect(x-5*d,y-17*d,Math.min(getWidth()-8*d,x+p.measureText(text)+5*d),y+7*d,p);
        p.setColor(0xFFFFFFFF);c.drawText(text,x,y,p);
        long now=android.os.SystemClock.elapsedRealtime();
        if(now-lastStatusAt>=1000){
            lastStatusAt=now;
            settings.edit().putString("capture_status",text).apply();
        }
    }
    private void drawPath(Canvas c,List<float[]> pts){
        if(pts.size()<2)return;
        path.reset();path.moveTo(pts.get(0)[0],pts.get(0)[1]);
        for(int i=1;i<pts.size();i++)path.lineTo(pts.get(i)[0],pts.get(i)[1]);
        c.drawPath(path,p);
    }
}
