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
        Path result=new Path();
        float mag=(float)Math.hypot(dx,dy);
        if (mag<1.0e-5f || right<=left || bottom<=top) return result;
        dx/=mag;dy/=mag;
        float x=Math.max(left,Math.min(right,sx));
        float y=Math.max(top,Math.min(bottom,sy));
        result.points.add(new float[]{x,y});
        for (int bounce=0;bounce<=maxRebounds && bounce<20;bounce++) {
            float t=Float.POSITIVE_INFINITY;
            boolean flipX=false,flipY=false;
            if (dx>1e-5f) { float t1=(right-x)/dx; if(t1>=1e-4f && t1<t){ t=t1; flipX=true; flipY=false; } }
            if (dx<-1e-5f){ float t1=(left-x)/dx; if(t1>=1e-4f && t1<t){ t=t1; flipX=true; flipY=false; } }
            if (dy>1e-5f) { float t1=(bottom-y)/dy; if(t1>=1e-4f && t1<t){ t=t1; flipY=true; flipX=false; } }
            if (dy<-1e-5f){ float t1=(top-y)/dy; if(t1>=1e-4f && t1<t){ t=t1; flipY=true; flipX=false; } }
            if (!Float.isFinite(t) || t<1e-4f) break;
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
            if(bounce==maxRebounds) break;
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
