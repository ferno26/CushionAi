import java.io.File;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import com.cushionlab.autoguide.FrameAnalyzer;
import com.cushionlab.autoguide.Trajectory;

/** Exact gameplay screenshot regression: disambiguate cue, stripe and fake guide markers. */
public final class DetectorFalsePositiveTest {
 public static void main(String[] args) throws Exception {
  BufferedImage im=ImageIO.read(new File(args[0]));
  FrameAnalyzer.Scene s=FrameAnalyzer.analyze(im.getRGB(0,0,im.getWidth(),im.getHeight(),null,0,im.getWidth()), im.getWidth(),im.getHeight());
  if(s.cue==null || Math.hypot(s.cue.x-468,s.cue.y-604)>14)throw new AssertionError("Cue identity incorrect");
  assertBall(s,623,560,true,"striped object ball");
  assertBall(s,735,425,false,"aim endpoint icon");
  assertBall(s,527,569,false,"cue stick/guideline");
  assertBall(s,775,445,true,"black ball");
  System.out.println("PASS: cue vs stripe, three object/false-marker controls");
 }
 static void assertBall(FrameAnalyzer.Scene s,float x,float y,boolean expected,String label){
  boolean found=false;
  for(Trajectory.Ball b:s.balls)if(Math.hypot(b.x-x,b.y-y)<12)found=true;
  if(found!=expected)throw new AssertionError(label+" found="+found+" expected="+expected);
 }
}
