package com.cushionlab.autoguide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Finds the table and the game's own bright aiming-guide segments. Pure Java for host tests. */
public final class FrameAnalyzer {
    public static final class GuideRay {
        public final float x,y,dx,dy,confidence;
        GuideRay(float x,float y,float dx,float dy,float confidence){this.x=x;this.y=y;this.dx=dx;this.dy=dy;this.confidence=confidence;}
    }
    public static final class Scene {
        public float left,top,right,bottom,radius,confidence;
        public final List<GuideRay> rays=new ArrayList<>();
    }
    private static final class Point { final float x,y; Point(float x,float y){this.x=x;this.y=y;} }
    private static final class Line {
        float x1,y1,x2,y2,length,score,angle;
        float distance(float x,float y){float dx=x2-x1,dy=y2-y1,m=Math.max(1f,length);return Math.abs((x-x1)*dy-(y-y1)*dx)/m;}
    }
    static float brightness(int color){return (0.2126f*((color>>16)&255)+0.7152f*((color>>8)&255)+0.0722f*(color&255))/255f;}
    static float saturation(int p){int r=(p>>16)&255,g=(p>>8)&255,b=p&255;int mx=Math.max(r,Math.max(g,b)),mn=Math.min(r,Math.min(g,b));return mx==0?0:(mx-mn)/(float)mx;}
    static float hue(int p){
        float r=((p>>16)&255)/255f,g=((p>>8)&255)/255f,b=(p&255)/255f;
        float hi=Math.max(r,Math.max(g,b)),lo=Math.min(r,Math.min(g,b)),d=hi-lo;
        if(d<.0001f)return 0;
        float h=hi==r?((g-b)/d)%6:(hi==g?(b-r)/d+2:(r-g)/d+4);
        return (h*60+360)%360;
    }
    private static float hueDistance(float a,float b){float d=Math.abs(a-b);return Math.min(d,360-d);}
    static boolean felt(int p,float hue){float b=brightness(p);return saturation(p)>.24f&&b>.25f&&b<.94f&&hueDistance(hue(p),hue)<23f;}
    private static boolean guideWhite(int p){
        int r=(p>>16)&255,g=(p>>8)&255,b=p&255,hi=Math.max(r,Math.max(g,b)),lo=Math.min(r,Math.min(g,b));
        return lo>=186&&hi-lo<=38&&brightness(p)>.76f;
    }
    public static Scene analyze(int[] pixels,int width,int height){
        Scene scene=new Scene();
        if(pixels==null||width<200||height<120||pixels.length<width*height)return scene;
        float feltHue=dominantFeltHue(pixels,width,height);
        findTable(pixels,width,height,feltHue,scene);
        if(scene.confidence<.30f)return scene;
        List<Point> whites=whitePoints(pixels,width,height,scene);
        List<Line> lines=houghLines(whites,width,height,scene.radius);
        chooseRays(pixels,width,height,scene,lines);
        return scene;
    }
    private static float dominantFeltHue(int[] p,int w,int h){
        int[] bins=new int[36];
        for(int y=h/4;y<h*4/5;y+=5)for(int x=w/7;x<w*6/7;x+=5){int c=p[y*w+x];if(saturation(c)>.28f&&brightness(c)>.16f&&brightness(c)<.92f)bins[(int)(hue(c)/10)%36]++;}
        int best=0;for(int i=1;i<36;i++)if(bins[i]>bins[best])best=i;return best*10+5;
    }
    private static void findTable(int[] p,int w,int h,float feltHue,Scene s){
        List<Integer> lefts=new ArrayList<>(),rights=new ArrayList<>(),ys=new ArrayList<>();
        int step=Math.max(2,w/420),allowedGap=Math.max(2,w/300);
        for(int y=h/8;y<h*9/10;y+=step){
            int bestA=-1,bestB=-1,start=-1,last=-1,miss=0;
            for(int x=w/20;x<w*19/20;x+=step){
                if(felt(p[y*w+x],feltHue)){if(start<0)start=x;last=x;miss=0;}
                else if(start>=0){miss+=step;if(miss>allowedGap){if(last-start>bestB-bestA){bestA=start;bestB=last;}start=-1;last=-1;miss=0;}}
            }
            if(start>=0&&last-start>bestB-bestA){bestA=start;bestB=last;}
            if(bestA>=0&&bestB-bestA>w*.42f){lefts.add(bestA);rights.add(bestB);ys.add(y);}
        }
        if(ys.size()<Math.max(12,h/(step*12)))return;
        Collections.sort(lefts);Collections.sort(rights);
        s.left=lefts.get(lefts.size()/2);s.right=rights.get(rights.size()/2);s.top=ys.get(0);s.bottom=ys.get(ys.size()-1);
        if(s.right-s.left<w*.38f||s.bottom-s.top<h*.30f)return;
        s.radius=Math.max(5f,Math.min((s.bottom-s.top)*.033f,(s.right-s.left)*.022f));
        s.confidence=Math.min(1f,ys.size()*step/(h*.42f));
    }
    private static List<Point> whitePoints(int[] p,int w,int h,Scene s){
        List<Point> out=new ArrayList<>();int inset=Math.max(2,Math.round(s.radius*.4f)),step=(s.right-s.left>900)?2:1;
        for(int y=(int)s.top+inset;y<(int)s.bottom-inset;y+=step)for(int x=(int)s.left+inset;x<(int)s.right-inset;x+=step)if(guideWhite(p[y*w+x]))out.add(new Point(x,y));
        return out;
    }
    private static List<Line> houghLines(List<Point> pts,int w,int h,float radius){
        List<Line> found=new ArrayList<>();if(pts.size()<20)return found;
        final int angles=90,binSize=3,diag=(int)Math.ceil(Math.hypot(w,h)),bins=diag*2/binSize+3;
        int[][] votes=new int[angles][bins];float[] cs=new float[angles],sn=new float[angles];
        for(int a=0;a<angles;a++){double rad=Math.toRadians(a*2);cs[a]=(float)Math.cos(rad);sn[a]=(float)Math.sin(rad);}
        int stride=Math.max(1,pts.size()/9000);
        for(int n=0;n<pts.size();n+=stride){Point q=pts.get(n);for(int a=0;a<angles;a++){int b=Math.round((-q.x*sn[a]+q.y*cs[a]+diag)/binSize);if(b>=0&&b<bins)votes[a][b]++;}}
        int minVotes=Math.max(7,Math.round(radius*.65f/stride));List<int[]> peaks=new ArrayList<>();
        for(int a=0;a<angles;a++)for(int b=1;b<bins-1;b++)if(votes[a][b]>=minVotes&&votes[a][b]>=votes[a][b-1]&&votes[a][b]>=votes[a][b+1])peaks.add(new int[]{a,b,votes[a][b]});
        peaks.sort((u,v)->Integer.compare(v[2],u[2]));int limit=Math.min(70,peaks.size());
        for(int k=0;k<limit;k++){
            int a=peaks.get(k)[0],b=peaks.get(k)[1];float rho=b*binSize-diag;List<Float> ts=new ArrayList<>();
            for(Point q:pts)if(Math.abs(-q.x*sn[a]+q.y*cs[a]-rho)<=2.5f)ts.add(q.x*cs[a]+q.y*sn[a]);
            if(ts.size()<minVotes)continue;Collections.sort(ts);
            float bestStart=0,bestEnd=0,start=ts.get(0),last=start;int count=1,bestCount=0;
            for(int i=1;i<=ts.size();i++){
                if(i<ts.size()&&ts.get(i)-last<=Math.max(7f,radius*.62f)){last=ts.get(i);count++;continue;}
                if(last-start>bestEnd-bestStart){bestStart=start;bestEnd=last;bestCount=count;}
                if(i<ts.size()){start=last=ts.get(i);count=1;}
            }
            float len=bestEnd-bestStart;if(len<Math.max(18f,radius*1.55f)||bestCount<Math.max(6,len*.10f))continue;
            Line line=new Line();line.angle=a*2;line.x1=cs[a]*bestStart-sn[a]*rho;line.y1=sn[a]*bestStart+cs[a]*rho;line.x2=cs[a]*bestEnd-sn[a]*rho;line.y2=sn[a]*bestEnd+cs[a]*rho;line.length=len;line.score=len+bestCount*1.5f;
            boolean duplicate=false;for(Line old:found){float da=Math.abs(old.angle-line.angle);da=Math.min(da,180-da);float mx=(line.x1+line.x2)*.5f,my=(line.y1+line.y2)*.5f;if(da<7&&old.distance(mx,my)<radius*.55f){duplicate=true;break;}}
            if(!duplicate)found.add(line);if(found.size()>=12)break;
        }
        found.sort(Comparator.comparingDouble((Line l)->l.score).reversed());return found;
    }
    private static void chooseRays(int[] px,int w,int h,Scene s,List<Line> lines){
        if(lines.isEmpty())return;Line incoming=null;boolean cueAtFirst=true;float best=-1;
        for(Line l:lines){float f1=whiteFill(px,w,h,l.x1,l.y1,s.radius*.78f),f2=whiteFill(px,w,h,l.x2,l.y2,s.radius*.78f),filled=Math.max(f1,f2),asym=Math.abs(f1-f2),score=l.length*(.65f+filled+asym*.4f);if(filled>.18f&&score>best){best=score;incoming=l;cueAtFirst=f1>=f2;}}
        if(incoming==null)return;
        float gx=cueAtFirst?incoming.x2:incoming.x1,gy=cueAtFirst?incoming.y2:incoming.y1;
        float inDx=cueAtFirst?(incoming.x2-incoming.x1):(incoming.x1-incoming.x2),inDy=cueAtFirst?(incoming.y2-incoming.y1):(incoming.y1-incoming.y2),inMag=(float)Math.hypot(inDx,inDy);
        List<GuideRay> candidates=new ArrayList<>();
        for(Line l:lines){
            if(l==incoming)continue;float d1=(float)Math.hypot(l.x1-gx,l.y1-gy),d2=(float)Math.hypot(l.x2-gx,l.y2-gy),near=Math.min(d1,d2);if(near>s.radius*6.2f)continue;
            float sx,sy,dx,dy;if(d1<=d2){sx=l.x2;sy=l.y2;dx=l.x2-l.x1;dy=l.y2-l.y1;}else{sx=l.x1;sy=l.y1;dx=l.x1-l.x2;dy=l.y1-l.y2;}
            float mag=(float)Math.hypot(dx,dy);if(mag<1)continue;dx/=mag;dy/=mag;float dot=Math.abs((dx*inDx+dy*inDy)/Math.max(1f,inMag));if(dot>.985f)continue;
            candidates.add(new GuideRay(sx,sy,dx,dy,Math.min(1f,.45f+l.length/(s.right-s.left))));
        }
        candidates.sort((a,b)->Float.compare(b.confidence,a.confidence));
        for(GuideRay r:candidates){boolean parallel=false;for(GuideRay old:s.rays)if(Math.abs(old.dx*r.dx+old.dy*r.dy)>.975f){parallel=true;break;}if(!parallel)s.rays.add(r);if(s.rays.size()==2)break;}
        if(s.rays.isEmpty()&&inMag>1)s.rays.add(new GuideRay(gx,gy,inDx/inMag,inDy/inMag,.55f));
    }
    private static float whiteFill(int[] p,int w,int h,float cx,float cy,float radius){
        int yes=0,all=0,step=Math.max(1,Math.round(radius/4));
        for(int y=Math.round(cy-radius);y<=cy+radius;y+=step)for(int x=Math.round(cx-radius);x<=cx+radius;x+=step){if(x<0||x>=w||y<0||y>=h||Math.hypot(x-cx,y-cy)>radius)continue;all++;if(guideWhite(p[y*w+x]))yes++;}
        return all==0?0:yes/(float)all;
    }
    private FrameAnalyzer(){}
}
