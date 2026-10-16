package com.cushionlab.autoguide;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Heuristic table/ball/cue-stick detector for captured screenshots; no SDK or network dependencies.
 * This is NOT guaranteed to handle every table skin, obstructed cue, or camera/animation frame.
 */
public final class FrameAnalyzer {
    public static final class Scene {
        public float left,top,right,bottom;
        public float radius;
        public float confidence;
        public float directionX=1,directionY=0,directionConfidence;
        public final List<Trajectory.Ball> balls=new ArrayList<>();
        public Trajectory.Ball cue;
    }
    static float brightness(int color){
        return (0.2126f*((color>>16)&255)+0.7152f*((color>>8)&255)+0.0722f*(color&255))/255f;
    }
    static float saturation(int p){
        int r=(p>>16)&255,g=(p>>8)&255,b=p&255;
        int mx=Math.max(r,Math.max(g,b)),mn=Math.min(r,Math.min(g,b));
        return mx==0?0:(mx-mn)/(float)mx;
    }
    static float hue(int p) {
        float r=((p>>16)&255)/255f,g=((p>>8)&255)/255f,b=(p&255)/255f;
        float hi=Math.max(r,Math.max(g,b)),lo=Math.min(r,Math.min(g,b)),delta=hi-lo;
        if(delta<0.0001f)return 0;
        float h=hi==r?((g-b)/delta)%6:(hi==g?(b-r)/delta+2:(r-g)/delta+4);
        return (h*60+360)%360;
    }
    private static float distance(float a,float b){float d=Math.abs(a-b);return Math.min(d,360-d);}
    static boolean felt(int pixel,float feltHue){
        return saturation(pixel)>.23f && brightness(pixel)>.275f &&
                brightness(pixel)<.96f && distance(hue(pixel),feltHue)<25f;
    }
    private static int pix(int[] p,int w,int h,int x,int y){
        x=Math.min(w-1,Math.max(0,x));y=Math.min(h-1,Math.max(0,y));return p[y*w+x];
    }
    private static boolean neutralWhite(int px){
        int r=(px>>16)&255,g=(px>>8)&255,b=px&255;
        return r>175&&g>175&&b>175 && Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<67;
    }
    public static Scene analyze(int[] rgb,int width,int height) {
        Scene s=new Scene();
        if(rgb==null||width<200||height<120||rgb.length<width*height)return s;
        int[] bins=new int[36];
        // The broad center excludes most game UI. Dominant saturated hue is usually the felt.
        for(int y=height/4;y<height*3/4;y+=5)for(int x=width/5;x<width*4/5;x+=5){
            int p=rgb[y*width+x];
            if(saturation(p)>.28f&&brightness(p)>.15f&&brightness(p)<.93f)bins[(int)(hue(p)/10)%36]++;
        }
        int topBin=0;
        for(int i=1;i<bins.length;i++)if(bins[i]>bins[topBin])topBin=i;
        float feltHue=topBin*10+5;
        int step=Math.max(3,width/190);
        int rowMin=height,rowMax=0,lowX=width,highX=0,qualified=0;
        for(int y=height/8;y<height*9/10;y+=step){
            int hit=0,first=width,last=-1;
            for(int x=width/15;x<width*14/15;x+=step){
                if(felt(rgb[y*width+x],feltHue)){hit++;first=Math.min(first,x);last=Math.max(last,x);}
            }
            if(hit < (width/step)*.27f)continue;
            rowMin=Math.min(rowMin,y);rowMax=Math.max(rowMax,y);
            lowX=Math.min(lowX,first);highX=Math.max(highX,last);qualified++;
        }
        // Reject menus/loading screens instead of giving misleading predictions.
        if(qualified<height/(step*8) || highX-lowX<width*.33f || rowMax-rowMin<height*.28f) return s;
        s.left=lowX;s.right=highX;s.top=rowMin;s.bottom=rowMax;
        s.confidence=Math.min(1f,qualified*step/(height*.42f));
        float h=s.bottom-s.top;
        float r=Math.max(5f,Math.min(h*.031f,(s.right-s.left)*.021f));
        s.radius=r;
        // Score circular objects using center-to-ring contrast with felt.
        List<Candidate> candidates=new ArrayList<>();
        int stride=Math.max(3,Math.round(r*.26f));
        for(int y=(int)(s.top+1.6f*r);y<(int)(s.bottom-1.6f*r);y+=stride){
            for(int x=(int)(s.left+1.6f*r);x<(int)(s.right-1.6f*r);x+=stride){
                int inner=0,white=0,outer=0;
                for(int k=0;k<12;k++){
                    double a=k*Math.PI/6d;
                    int ip=pix(rgb,width,height,Math.round(x+(float)Math.cos(a)*r*.48f),Math.round(y+(float)Math.sin(a)*r*.48f));
                    int op=pix(rgb,width,height,Math.round(x+(float)Math.cos(a)*r*1.35f),Math.round(y+(float)Math.sin(a)*r*1.35f));
                    if(!felt(ip,feltHue))inner++;
                    if(neutralWhite(ip))white++;
                    if(felt(op,feltHue))outer++;
                }
                if(inner<7||outer<7)continue;
                float score=(inner/12f)*.45f+(outer/12f)*.55f;
                if(!felt(rgb[y*width+x],feltHue))score+=.06f;
                if(score<.76f)continue;
                candidates.add(new Candidate(x,y,score,white/12f));
            }
        }
        candidates.sort(Comparator.comparingDouble((Candidate c)->c.score).reversed());
        List<Candidate> chosen=new ArrayList<>();
        for(Candidate c:candidates){
            boolean near=false;
            for(Candidate o:chosen)if(Math.hypot(o.x-c.x,o.y-c.y)<r*1.25f){near=true;break;}
            if(!near)chosen.add(c);
            if(chosen.size()>=18)break;
        }
        Candidate bestCue=null;
        float bestWhite=.55f;
        for(Candidate c:chosen){
            float white=whiteDisk(rgb,width,height,c.x,c.y,r);
            if(white>bestWhite){bestWhite=white;bestCue=c;}
        }
        for(Candidate c:chosen){
            boolean cue=c==bestCue;
            Trajectory.Ball ball=new Trajectory.Ball(c.x,c.y,cue,c.score);
            s.balls.add(ball);
            if(cue)s.cue=ball;
        }
        if(s.cue!=null)detectStick(rgb,width,height,feltHue,s);
        return s;
    }
    private static float whiteDisk(int[] pixels,int w,int h,int x,int y,float r){
        int white=0,all=0;
        for(float fraction:new float[]{.25f,.5f,.72f}){
            for(int k=0;k<16;k++){
                double a=k*Math.PI/8;
                int px=pix(pixels,w,h,Math.round(x+(float)Math.cos(a)*r*fraction),
                        Math.round(y+(float)Math.sin(a)*r*fraction));
                if(neutralWhite(px))white++;
                all++;
            }
        }
        return white/(float)all;
    }
    private static void detectStick(int[] px,int w,int h,float feltHue,Scene scene){
        // Cue stick extends BEHIND the cue ball. Predicted shot goes in opposite direction.
        float r=scene.radius;
        float best=-1;
        float bestRad=0;
        for(int deg=0;deg<360;deg+=5){
            double a=Math.toRadians(deg);
            float vx=(float)Math.cos(a),vy=(float)Math.sin(a);
            int continuous=0,count=0;
            for(int z=0;z<14;z++){
                float step=r*(2.8f+z*.65f);
                int x=Math.round(scene.cue.x+step*vx),y=Math.round(scene.cue.y+step*vy);
                if(x<scene.left+r||x>scene.right-r||y<scene.top+r||y>scene.bottom-r)break;
                int color=pix(px,w,h,x,y);
                if(!felt(color,feltHue)&&brightness(color)>.20f)continuous++;
                count++;
            }
            if(count<9)continue;
            float conf=continuous/(float)count;
            if(conf>best){best=conf;bestRad=(float)a;}
        }
        if(best>.72f){
            scene.directionX=-(float)Math.cos(bestRad);
            scene.directionY=-(float)Math.sin(bestRad);
            scene.directionConfidence=Math.max(0,Math.min(1,(best-.72f)/.28f));
        }
    }
    private static class Candidate {
        final int x,y; final float score,white;
        Candidate(int x,int y,float score,float white){this.x=x;this.y=y;this.score=score;this.white=white;}
    }
    private FrameAnalyzer(){}
}
