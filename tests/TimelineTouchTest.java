import cn.giflite.TimelineTouchPolicy;
import cn.giflite.SelectionPolicy;

public class TimelineTouchTest {
    static int checks;
    static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
    public static void main(String[] args){
        check(TimelineTouchPolicy.ambiguous(150,150.2f,150,48,16));
        check(!TimelineTouchPolicy.ambiguous(20,200,110,48,16));
        check(!TimelineTouchPolicy.ambiguous(150,150.2f,110,48,16));
        check(TimelineTouchPolicy.firstFromDirection(-5));
        check(!TimelineTouchPolicy.firstFromDirection(5));
        double a=6.19,b=6.20;
        boolean first=TimelineTouchPolicy.firstFromDirection(-30);
        double left=SelectionPolicy.move(12.46,a,b,first,a-1);
        check(first&&left<a&&SelectionPolicy.valid(12.46,left,b));
        first=TimelineTouchPolicy.firstFromDirection(30);
        double right=SelectionPolicy.move(12.46,a,b,first,b+1);
        check(!first&&right>b&&SelectionPolicy.valid(12.46,a,right));
        check(SelectionPolicy.move(12.46,0,.01,true,-1)==0);
        check(SelectionPolicy.move(12.46,12.45,12.46,false,14)==12.46);
        System.out.println(checks+" close-handle interaction checks passed");
    }
}
