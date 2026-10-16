package com.cushionlab.autoguide;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Non-touchable overlay that draws only evidence-backed guide extensions. */
final class GuideView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();private final SharedPreferences settings;
    private volatile FrameAnalyzer.Scene scene;private volatile int frameW,frameH;private long lastFrameAt,lastStatusAt;private int frames;
    private final Runnable heartbeat=new Runnable(){@Override public void run(){invalidate();postDelayed(this,1000);}};
    GuideView(Context context,SharedPreferences prefs){super(context);settings=prefs;setBackgroundColor(0x00000000);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();post(heartbeat);}
    @Override protected void onDetachedFromWindow(){removeCallbacks(heartbeat);super.onDetachedFromWindow();}
    void submit(FrameAnalyzer.Scene result,int w,int h){scene=result;frameW=w;frameH=h;lastFrameAt=android.os.SystemClock.elapsedRealtime();frames++;postInvalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);FrameAnalyzer.Scene s=scene;boolean fresh=lastFrameAt>0&&android.os.SystemClock.elapsedRealtime()-lastFrameAt<5000;
        String state=frames==0?"Waiting for screen frames":!fresh?"Capture paused":s==null||s.confidence<.30f?"Table not detected":s.rays.isEmpty()?"White guide not detected":"Extending "+s.rays.size()+" guide line(s)";
        String error=settings.getString("capture_error","");if(!error.isEmpty())state=error;publishStatus(state);
        if(!fresh||s==null||s.confidence<.30f||s.rays.isEmpty()||frameW<1||frameH<1)return;
        canvas.save();canvas.scale(getWidth()/(float)frameW,getHeight()/(float)frameH);
        int bounces=Math.max(0,Math.min(12,settings.getInt("bounces",3)));List<Trajectory.Ball> noBalls=new ArrayList<>();
        for(FrameAnalyzer.GuideRay ray:s.rays){
            Trajectory.Path extension=Trajectory.trace(ray.x,ray.y,ray.dx,ray.dy,s.left+s.radius,s.top+s.radius,s.right-s.radius,s.bottom-s.radius,s.radius,noBalls,bounces);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setColor(0xD9000000);paint.setStrokeWidth(Math.max(4f,s.radius*.34f));drawPath(canvas,extension.points);
            paint.setColor(0xFFE2F8FF);paint.setStrokeWidth(Math.max(2.1f,s.radius*.17f));drawPath(canvas,extension.points);
        }
        canvas.restore();
    }
    private void publishStatus(String text){long now=android.os.SystemClock.elapsedRealtime();if(now-lastStatusAt>=1000){lastStatusAt=now;settings.edit().putString("capture_status",text).apply();}}
    private void drawPath(Canvas c,List<float[]> points){if(points.size()<2)return;path.reset();path.moveTo(points.get(0)[0],points.get(0)[1]);for(int i=1;i<points.size();i++)path.lineTo(points.get(i)[0],points.get(i)[1]);c.drawPath(path,paint);}
}
