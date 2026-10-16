package com.cushionlab.autoguide;

import java.util.ArrayList;
import java.util.List;

/** Pixel-space cue-ball ray tracing against cushions and circular balls. All math is local. */
public final class Trajectory {
    public static final class Ball {
        public final float x, y;
        public final boolean cue;
        public final float confidence;
        public Ball(float x, float y, boolean cue, float confidence) {
            this.x=x; this.y=y; this.cue=cue; this.confidence=confidence;
        }
    }
    public static final class Path {
        public final List<float[]> points = new ArrayList<>();
        public final List<float[]> objectPath = new ArrayList<>();
        public int hitBall=-1;
        public int rebounds;
    }

    public static Path trace(float sx,float sy,float dx,float dy,
                             float left,float top,float right,float bottom,
                             float radius,List<Ball> balls,int maxRebounds) {
        return traceAdjusted(sx,sy,dx,dy,left,top,right,bottom,radius,balls,maxRebounds,0f);
    }

    /** Reflection preview with an optional per-cushion angle adjustment for visual calibration. */
    public static Path traceAdjusted(float sx,float sy,float dx,float dy,
                             float left,float top,float right,float bottom,
                             float radius,List<Ball> balls,int maxRebounds,float angleAdjustmentDegrees) {
        Path result=new Path();
        float mag=(float)Math.hypot(dx,dy);
        if (mag<1.0e-5f || right<=left || bottom<=top) return result;
        dx/=mag;dy/=mag;
        float x=Math.max(left,Math.min(right,sx));
        float y=Math.max(top,Math.min(bottom,sy));
        result.points.add(new float[]{x,y});
        // A detected native segment can already end on/outside a cushion. Begin with its
        // reflected direction instead of producing an empty path at t=0.
        if(maxRebounds>0){
            boolean reflected=false;
            if((x<=left+.05f&&dx<0)||(x>=right-.05f&&dx>0)){dx=-dx;reflected=true;}
            if((y<=top+.05f&&dy<0)||(y>=bottom-.05f&&dy>0)){dy=-dy;reflected=true;}
            if(reflected)result.rebounds=1;
        }
        for (int segment=0;segment<20;segment++) {
            float t=Float.POSITIVE_INFINITY;
            boolean flipX=false,flipY=false;
            if (dx>1e-5f) { float t1=(right-x)/dx; if(t1>=1e-4f && t1<t){ t=t1; flipX=true; flipY=false; } }
            if (dx<-1e-5f){ float t1=(left-x)/dx; if(t1>=1e-4f && t1<t){ t=t1; flipX=true; flipY=false; } }
            if (dy>1e-5f) { float t1=(bottom-y)/dy; if(t1>=1e-4f && t1<t){ t=t1; flipY=true; flipX=false; } }
            if (dy<-1e-5f){ float t1=(top-y)/dy; if(t1>=1e-4f && t1<t){ t=t1; flipY=true; flipX=false; } }
            if (!Float.isFinite(t)) break;
            int which=-1;
            float hit=t;
            for(int i=0;i<balls.size();i++) {
                Ball b=balls.get(i);
                if(b.cue)continue;
                float vx=x-b.x,vy=y-b.y;
                float proj=vx*dx+vy*dy;
                float c=vx*vx+vy*vy-4f*radius*radius;
                float discr=proj*proj-c;
                if(discr<0)continue;
                float t1=-proj-(float)Math.sqrt(discr);
                if(t1>=1.0e-3f && t1<hit) {hit=t1;which=i;}
            }
            float endX=Math.max(left,Math.min(right,x+hit*dx));
            float endY=Math.max(top,Math.min(bottom,y+hit*dy));
            result.points.add(new float[]{endX,endY});
            if(which>=0) {
                result.hitBall=which;
                Ball b=balls.get(which);
                float ox=b.x-endX,oy=b.y-endY;
                float m=(float)Math.hypot(ox,oy);
                if(m>1e-5f){
                    ox/=m;oy/=m;
                    result.objectPath.add(new float[]{b.x,b.y});
                    float[] ep=firstRail(b.x,b.y,ox,oy,left,top,right,bottom);
                    result.objectPath.add(ep);
                }
                break;
            }
            if(result.rebounds>=maxRebounds) break;
            x=endX;y=endY;
            if(flipX)dx=-dx;
            if(flipY)dy=-dy;
            // Stop infinitesimal corner loops and reflect on both walls if hit at a corner.
            if (Math.abs(x-left)<.01f || Math.abs(x-right)<.01f) {
                if(!flipX && Math.abs(dx)>1e-5f)dx=-dx;
            }
            if (Math.abs(y-top)<.01f || Math.abs(y-bottom)<.01f) {
                if(!flipY && Math.abs(dy)>1e-5f)dy=-dy;
            }
            if(Math.abs(angleAdjustmentDegrees)>.01f){
                double turn=Math.toRadians(Math.max(-25f,Math.min(25f,angleAdjustmentDegrees)));
                float nextX=(float)(dx*Math.cos(turn)-dy*Math.sin(turn));
                float nextY=(float)(dx*Math.sin(turn)+dy*Math.cos(turn));
                dx=nextX;dy=nextY;
            }
            result.rebounds++;
        }
        return result;
    }
    private static float[] firstRail(float x,float y,float dx,float dy,float l,float t,float r,float b){
        float min=Float.POSITIVE_INFINITY;
        if(dx>1e-5f)min=Math.min(min,(r-x)/dx);
        if(dx<-1e-5f)min=Math.min(min,(l-x)/dx);
        if(dy>1e-5f)min=Math.min(min,(b-y)/dy);
        if(dy<-1e-5f)min=Math.min(min,(t-y)/dy);
        if(!Float.isFinite(min))return new float[]{x,y};
        return new float[]{x+dx*Math.max(0,min),y+dy*Math.max(0,min)};
    }
    private Trajectory() {}
}
