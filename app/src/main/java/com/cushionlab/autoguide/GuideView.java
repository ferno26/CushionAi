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
    GuideView(Context ctx,SharedPreferences prefs){super(ctx);settings=prefs;setBackgroundColor(0x00000000);}
    void submit(FrameAnalyzer.Scene result,int w,int h){scene=result;frameW=w;frameH=h;postInvalidate();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        FrameAnalyzer.Scene s=scene;
        if(s==null||s.confidence<.30f||frameW<1||frameH<1)return;
        c.save(); c.scale(getWidth()/(float)frameW,getHeight()/(float)frameH);
        // Keep the full-screen capture coordinate space and apply only guide calibration.
        float zoomX=Math.max(.75f,Math.min(1.25f,settings.getInt("table_width",100)/100f));
        float zoomY=Math.max(.75f,Math.min(1.25f,settings.getInt("table_height",100)/100f));
        float offsetX=(settings.getInt("table_shift_x",100)-100)*frameW/1000f;
        float offsetY=(settings.getInt("table_shift_y",100)-100)*frameH/1000f;
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
            boolean auto=settings.getBoolean("auto_aim",true);
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
    private void drawPath(Canvas c,List<float[]> pts){
        if(pts.size()<2)return;
        path.reset();path.moveTo(pts.get(0)[0],pts.get(0)[1]);
        for(int i=1;i<pts.size();i++)path.lineTo(pts.get(i)[0],pts.get(i)[1]);
        c.drawPath(path,p);
    }
}
