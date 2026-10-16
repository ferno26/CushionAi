import com.cushionlab.autoguide.FrameAnalyzer;
import com.cushionlab.autoguide.Trajectory;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class CoreSmokeTest {
 static void check(boolean pass,String what){if(!pass)throw new AssertionError(what);}
 static void disk(int[] p,int w,int h,int cx,int cy,int r,int color){
  for(int y=Math.max(0,cy-r);y<Math.min(h,cy+r+1);y++)for(int x=Math.max(0,cx-r);x<Math.min(w,cx+r+1);x++)if(Math.hypot(x-cx,y-cy)<=r)p[y*w+x]=color;
 }
 static void line(int[] p,int w,int h,int x1,int y1,int x2,int y2,int color){
  int n=(int)Math.ceil(Math.hypot(x2-x1,y2-y1));
  for(int i=0;i<=n;i++){float t=i/(float)Math.max(1,n);int x=Math.round(x1+(x2-x1)*t),y=Math.round(y1+(y2-y1)*t);disk(p,w,h,x,y,2,color);}
 }
 static FrameAnalyzer.Scene analyzeFile(String name)throws Exception{
  BufferedImage im=ImageIO.read(new File(name));int w=im.getWidth(),h=im.getHeight();int[] rgb=new int[w*h];im.getRGB(0,0,w,h,rgb,0,w);return FrameAnalyzer.analyze(rgb,w,h);
 }
 public static void main(String[]args)throws Exception{
  for(int a=0;a<360;a+=7){
   float dx=(float)Math.cos(Math.toRadians(a)),dy=(float)Math.sin(Math.toRadians(a));
   Trajectory.Path r=Trajectory.trace(100,80,dx,dy,5,5,195,155,6,new ArrayList<>(),7);
   check(r.points.size()>=2,"no ray for angle "+a);check(r.rebounds==7,"bounce count "+a);
   for(float[] q:r.points){check(q[0]>=4.98f&&q[0]<=195.02f,"wall x "+a);check(q[1]>=4.98f&&q[1]<=155.02f,"wall y "+a);}
  }
  List<Trajectory.Ball> balls=new ArrayList<>();balls.add(new Trajectory.Ball(100,80,true,1));balls.add(new Trajectory.Ball(150,80,false,1));
  Trajectory.Path contact=Trajectory.trace(100,80,1,0,5,5,195,155,5,balls,3);check(contact.hitBall==1,"object collision not detected");
  int w=420,h=240;int[] pixels=new int[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++)pixels[y*w+x]=(x>=40&&x<380&&y>=30&&y<210)?0xff187ea4:0xff141a23;
  disk(pixels,w,h,115,116,8,0xfff7f7f4);line(pixels,w,h,115,116,200,100,0xffffffff);
  line(pixels,w,h,200,100,274,64,0xffffffff);line(pixels,w,h,218,130,258,182,0xffffffff);
  FrameAnalyzer.Scene synthetic=FrameAnalyzer.analyze(pixels,w,h);
  check(synthetic.confidence>.5f,"synthetic table not found");check(synthetic.left<90&&synthetic.right>330,"synthetic table incorrect");
  check(synthetic.rays.size()==2,"expected two synthetic guide branches, got "+synthetic.rays.size());
  if(args.length>=2){
   FrameAnalyzer.Scene first=analyzeFile(args[0]),second=analyzeFile(args[1]);
   System.out.println("fixtures: first="+first.rays.size()+" second="+second.rays.size()+" bounds2="+second.left+","+second.top+"-"+second.right+","+second.bottom);
   check(first.confidence>.3f&&second.confidence>.3f,"screenshot table detection failed");
   check(first.right<1536*.86f&&second.right<1536*.86f,"side tray was mistaken for table");
   check(!first.rays.isEmpty(),"screenshot 1 guide not detected");check(second.rays.size()>=2,"screenshot 2 two branches not detected");
  }
  System.out.println("PASS: reflections, collision, synthetic two-branch guide, screenshot regression");
 }
}
