package com.cushionlab.autoguide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure Java 3-line aim fan and bank prediction. Does not depend on Android. */
public final class AimGuides {
    public static final class Result {
        public Trajectory.Path center;
        public final List<Trajectory.Path> edges = new ArrayList<>();
    }
    public static Result calculate(float cueX,float cueY,float directionX,float directionY,
                                   float left,float top,float right,float bottom,float radius,
                                   List<Trajectory.Ball> balls,int bounces,boolean threeLines,float widthFraction){
        Result out=new Result();
        float magnitude=(float)Math.hypot(directionX,directionY);
        if(magnitude<1e-5f || radius<=0f) return out;
        float dx=directionX/magnitude,dy=directionY/magnitude;
        float l=left+radius,t=top+radius,r=right-radius,b=bottom-radius;
        out.center=Trajectory.trace(cueX,cueY,dx,dy,l,t,r,b,radius,balls,bounces);
        if(threeLines){
            // The outer paths represent a visual clearance corridor, NOT an exact spin calculation.
            float offset=radius*Math.min(1f,Math.max(0f,widthFraction));
            for(int sign:new int[]{-1,1}){
                float x=Math.max(l,Math.min(r,cueX+sign*(-dy)*offset));
                float y=Math.max(t,Math.min(b,cueY+sign*dx*offset));
                out.edges.add(Trajectory.trace(x,y,dx,dy,l,t,r,b,radius,
                                             Collections.<Trajectory.Ball>emptyList(),bounces));
            }
        }
        return out;
    }
    /** Simple line-of-centers clearance; does not account for spin, speed, or cushion rebounds. */
    public static boolean clearPocket(float ballX,float ballY,float holeX,float holeY,
                                      List<Trajectory.Ball> balls,int targetIndex,float radius){
        float vx=holeX-ballX,vy=holeY-ballY,denom=vx*vx+vy*vy;
        if(denom<1e-4f)return false;
        for(int i=0;i<balls.size();i++){
            if(i==targetIndex)continue;
            Trajectory.Ball other=balls.get(i);
            float fraction=((other.x-ballX)*vx+(other.y-ballY)*vy)/denom;
            if(fraction<=0f||fraction>=1f)continue;
            float dx=other.x-(ballX+vx*fraction);
            float dy=other.y-(ballY+vy*fraction);
            if(dx*dx+dy*dy < (2*radius)*(2*radius))return false;
        }
        return true;
    }
    private AimGuides(){}
}
