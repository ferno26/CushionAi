import java.io.File;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import com.cushionlab.autoguide.FrameAnalyzer;

/** Host-side regression against the user's actual 1536x709 gameplay screenshot. */
public final class RealScreenshotTest {
  public static void main(String[] args) throws Exception {
    BufferedImage im=ImageIO.read(new File(args[0]));
    int w=im.getWidth(),h=im.getHeight();
    FrameAnalyzer.Scene s=FrameAnalyzer.analyze(im.getRGB(0,0,w,h,null,0,w),w,h);
    require(s.cue!=null,"cue ball undetected");
    require(Math.hypot(s.cue.x-468,s.cue.y-604)<14,"cue detected in wrong position");
    require(Math.abs(s.left-290)<25 && Math.abs(s.right-1248)<25,"table boundaries incorrect");
    require(s.directionConfidence>.3,"cue direction low confidence");
    double dx=746-468,dy=422-604,len=Math.hypot(dx,dy);
    require(s.directionX*dx/len+s.directionY*dy/len>.975,"aim heading incorrect");
    System.out.printf("PASS: cue(%.0f,%.0f), bounds(%.0f,%.0f,%.0f,%.0f), heading=(%.3f,%.3f), confidence=%.3f%n",s.cue.x,s.cue.y,s.left,s.top,s.right,s.bottom,s.directionX,s.directionY,s.directionConfidence);
  }
  static void require(boolean check,String message){if(!check)throw new AssertionError(message);}
}
