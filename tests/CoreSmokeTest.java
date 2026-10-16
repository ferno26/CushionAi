import com.cushionlab.autoguide.FrameAnalyzer;
import com.cushionlab.autoguide.Trajectory;
import java.util.ArrayList;
import java.util.List;

public class CoreSmokeTest {
 static void check(boolean pass,String what){if(!pass)throw new AssertionError(what);}
 public static void main(String[]args){
  for(int a=0;a<360;a+=7){
   float dx=(float)Math.cos(Math.toRadians(a)),dy=(float)Math.sin(Math.toRadians(a));
   Trajectory.Path r=Trajectory.trace(100,80,dx,dy,5,5,195,155,6, new ArrayList<>(),7);
   check(r.points.size()>=2,"no ray for angle "+a);
   check(r.rebounds==7,"bounce count "+a);
   for(float[] p:r.points){check(p[0]>=4.98f&&p[0]<=195.02f,"wall x "+a);check(p[1]>=4.98f&&p[1]<=155.02f,"wall y "+a);}
  }
  List<Trajectory.Ball> balls=new ArrayList<>();
  balls.add(new Trajectory.Ball(100,80,true,1));balls.add(new Trajectory.Ball(150,80,false,1));
  Trajectory.Path contact=Trajectory.trace(100,80,1,0,5,5,195,155,5,balls,3);
  check(contact.hitBall==1,"object collision not detected");
  check(!contact.objectPath.isEmpty(),"object trajectory missing");
  int w=420,h=240;int[] pixels=new int[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++)pixels[y*w+x]=(x>=40&&x<380&&y>=30&&y<210)?0xff187ea4:0xff141a23;
  // Synthetic white cue ball + red object; image detection may be approximate.
  for(int y=30;y<210;y++)for(int x=40;x<380;x++){
   if(Math.hypot(x-115,y-116)<6)pixels[y*w+x]=0xfff7f7f4;
   if(Math.hypot(x-225,y-115)<6)pixels[y*w+x]=0xffd0152a;
  }
  FrameAnalyzer.Scene scene=FrameAnalyzer.analyze(pixels,w,h);
  check(scene.confidence>.5f,"table not found");
  check(scene.left<90&&scene.right>330,"table incorrect");
  System.out.println("PASS: trajectory 52 angles, 7 bounces, ball collision, synthetic table");
 }
}
