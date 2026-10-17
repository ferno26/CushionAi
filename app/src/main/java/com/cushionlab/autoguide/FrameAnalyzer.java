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
        List<Integer> leftEdges=new ArrayList<>(),rightEdges=new ArrayList<>(),goodRows=new ArrayList<>();
        int allowedGap=Math.max(10,Math.round(Math.min(width,height)*.029f));
        for(int y=height/9;y<height*9/10;y+=step){
            // Find a coherent felt run. A single same-colour UI pixel no longer
            // expands the table bounds beyond the physical cushions.
            int bestStart=-1,bestEnd=-1,start=-1,last=-1,gap=0;
            for(int x=width/16;x<width*15/16;x+=step){
                if(felt(rgb[y*width+x],feltHue)){
                    if(start<0)start=x;
                    last=x;gap=0;
                }else if(start>=0){
                    gap+=step;
                    if(gap>allowedGap){
                        if(last-start>bestEnd-bestStart){bestStart=start;bestEnd=last;}
                        start=-1;last=-1;gap=0;
                    }
                }
            }
            if(start>=0&&last-start>bestEnd-bestStart){bestStart=start;bestEnd=last;}
            if(bestStart>=0&&bestEnd-bestStart>width*.40f){
                leftEdges.add(bestStart);rightEdges.add(bestEnd);goodRows.add(y);
            }
        }
        if(goodRows.size()<Math.max(10,height/(step*12)))return s;
        java.util.Collections.sort(leftEdges);java.util.Collections.sort(rightEdges);
        s.left=leftEdges.get(leftEdges.size()/2);
        s.right=rightEdges.get(rightEdges.size()/2);
        s.top=goodRows.get(0);
        s.bottom=goodRows.get(goodRows.size()-1);
        if(s.right-s.left<width*.35f||s.bottom-s.top<height*.28f)return s;
        // The coherent felt run includes the blue cushion lining; approximate
        // the actual reflection rails by offsetting that lining inward.
        float margin=Math.max(6f,Math.min(16f,(s.bottom-s.top)*.033f));
        s.left+=margin;
        s.right-=margin;
        s.bottom+=margin;
        int qualified=goodRows.size();
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
                // A cue shaft or painted targeting line produces a narrow dark stripe,
                // while a real ball occupies a roughly circular disk. Check multiple
                // radial rings to reject these line-shaped false positives.
                float disk=circleCoverage(rgb,width,height,x,y,r,feltHue);
                if(disk<.72f && !(disk>.51f && whiteDiskArea(rgb,width,height,x,y,r)>.28f))continue;
                score=Math.min(1f,score*.7f+disk*.3f);
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
        float bestWhite=.42f;
        for(Candidate c:chosen){
            // A striped object ball may have a brilliant white central patch;
            // sample the whole ball disk instead of a few thin rings.
            float white=whiteDiskArea(rgb,width,height,c.x,c.y,r);

            if(white>bestWhite){bestWhite=white;bestCue=c;}
        }
        for(Candidate c:chosen){
            // A cue marker or illuminated cue shaft can look like a second ball.
            // Discard impossible overlap with a confidently located cue ball.
            if(bestCue!=null&&c!=bestCue&&Math.hypot(c.x-bestCue.x,c.y-bestCue.y)<r*2.25f)continue;
            boolean cue=c==bestCue;
            Trajectory.Ball ball=new Trajectory.Ball(c.x,c.y,cue,c.score);
            s.balls.add(ball);
            if(cue)s.cue=ball;
        }
        if(s.cue!=null)detectStick(rgb,width,height,feltHue,s);
        return s;
    }
    private static float circleCoverage(int[] pixels,int w,int h,int x,int y,float r,float feltHue){
        int inside=0,total=0;
        for(float fraction:new float[]{.51f,.78f}){
            for(int k=0;k<24;k++){
                double a=k*Math.PI/12d;
                int ix=Math.round(x+(float)Math.cos(a)*r*fraction);
                int iy=Math.round(y+(float)Math.sin(a)*r*fraction);
                if(!felt(pix(pixels,w,h,ix,iy),feltHue))inside++;
                total++;
            }
        }
        return inside/(float)total;
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
    private static float whiteDiskArea(int[] pixels,int w,int h,int x,int y,float r){
        int all=0,white=0;
        int rad=Math.max(5,Math.round(r*.86f));
        for(int dy=-rad;dy<=rad;dy+=2)for(int dx=-rad;dx<=rad;dx+=2){
            if(dx*dx+dy*dy>rad*rad)continue;
            if(neutralWhite(pix(pixels,w,h,x+dx,y+dy)))white++;
            all++;
        }
        return all==0?0:white/(float)all;
    }
    private static void detectStick(int[] px,int w,int h,float feltHue,Scene scene){
        // Detect the GAME'S existing bright cue aiming guideline. It provides
        // a more reliable forward angle than guessing from coloured cue sticks.
        float r=scene.radius;int cx=Math.round(scene.cue.x),cy=Math.round(scene.cue.y);
        float best=-1,runner=-1;
        for(int deg=0;deg<360;deg+=2){
            double a=Math.toRadians(deg);float dx=(float)Math.cos(a),dy=(float)Math.sin(a);
            float score=0;int total=0,bright=0;
            for(float distance=r*1.9f;distance<r*17f;distance+=3f){
                int x=Math.round(cx+distance*dx),y=Math.round(cy+distance*dy);
                if(x<scene.left+r||x>scene.right-r||y<scene.top+r||y>scene.bottom-r)break;
                total++;
                // A thin game line is only 2-3 px wide, allowing a 1px sampling tolerance.
                boolean lit=false;
                for(int off=-1;off<=1;off++){
                    int sx=Math.round(x-off*dy),sy=Math.round(y+off*dx);
                    if(neutralWhite(pix(px,w,h,sx,sy))){lit=true;break;}
                }
                if(lit){bright++;score+=1.0f;}
            }
            if(total<8)continue;
            float ratio=score/total;
            // Reject isolated bright pixels that are not a sustained line.
            if(bright<7||ratio<.36f)continue;
            if(ratio>best){best=ratio;runner=(float)a;}
        }
        if(best>.36f){
            scene.directionX=(float)Math.cos(runner);
            scene.directionY=(float)Math.sin(runner);
            scene.directionConfidence=Math.min(1f,(best-.36f)/.52f);
        }
    }
    private static class Candidate {
        final int x,y; final float score,white;
        Candidate(int x,int y,float score,float white){this.x=x;this.y=y;this.score=score;this.white=white;}
    }
    private FrameAnalyzer(){}
}
