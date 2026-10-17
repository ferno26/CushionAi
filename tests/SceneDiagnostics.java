import java.io.File;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import com.cushionlab.autoguide.FrameAnalyzer;
import com.cushionlab.autoguide.Trajectory;
public final class SceneDiagnostics {
 public static void main(String[] a)throws Exception{
  BufferedImage im=ImageIO.read(new File(a[0]));int w=im.getWidth(),h=im.getHeight();FrameAnalyzer.Scene s=FrameAnalyzer.analyze(im.getRGB(0,0,w,h,null,0,w),w,h);
  System.out.printf("Table: %.1f %.1f %.1f %.1f radius %.1f Confidence %.3f\n",s.left,s.top,s.right,s.bottom,s.radius,s.confidence);
  for(Trajectory.Ball b:s.balls)System.out.printf("ball (%.1f,%.1f) cue=%b confidence=%.2f\n",b.x,b.y,b.cue,b.confidence);
  System.out.printf("aim %.3f,%.3f score %.3f\n",s.directionX,s.directionY,s.directionConfidence);
 }
}
