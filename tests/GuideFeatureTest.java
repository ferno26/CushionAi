import com.cushionlab.autoguide.AimGuides;
import com.cushionlab.autoguide.Trajectory;
import java.util.ArrayList;
import java.util.List;
public class GuideFeatureTest {
 static void ok(boolean x,String msg){if(!x)throw new AssertionError(msg);}
 public static void main(String[]args){
    for(int a=0;a<360;a+=3){
      float dx=(float)Math.cos(Math.toRadians(a)),dy=(float)Math.sin(Math.toRadians(a));
      AimGuides.Result rays=AimGuides.calculate(95,75,dx,dy,0,0,200,150,6,
          new ArrayList<>(),5,true,.65f);
      ok(rays.center!=null && rays.edges.size()==2,"three lines missing "+a);
      for(Trajectory.Path path:rays.edges){
        ok(path.points.size()==7,"ray does not have six segments "+a);
        for(float[] p:path.points)ok(p[0]>=5.9&&p[0]<=194.1&&p[1]>=5.9&&p[1]<=144.1,
                                      "edge ray escaped rail bounds");
      }
    }
    List<Trajectory.Ball> objects=new ArrayList<>();
    objects.add(new Trajectory.Ball(100,90,false,1));
    objects.add(new Trajectory.Ball(120,90,false,1));
    ok(!AimGuides.clearPocket(100,90,150,90,objects,0,6),"should block pocket");
    ok(AimGuides.clearPocket(100,90,100,130,objects,0,6),"should leave pocket open");
    ok(!AimGuides.clearPocket(100,90,100,90,objects,0,6),"zero-length pocket");
    System.out.println("PASS: 120 headings x 3 lines, all cushions, clear/blocked pocket checks");
 }
}
