import cn.giflite.SelectionPolicy;
import java.util.Random;

public final class SelectionPolicyTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("Check "+checks);}
    static void near(double a,double b){check(Math.abs(a-b)<.0000001);}
    public static void main(String[] args){
        check(SelectionPolicy.valid(12.46,2.13,9.72));near(9.72-2.13,7.59);
        check(!SelectionPolicy.valid(12.46,9.72,2.13));check(!SelectionPolicy.valid(12.46,2,2));
        check(!SelectionPolicy.valid(12.46,-1,2));check(!SelectionPolicy.valid(12.46,0,12.47));
        check(!SelectionPolicy.valid(12.46,Double.NaN,3));check(!SelectionPolicy.valid(12.46,0,Double.POSITIVE_INFINITY));
        near(SelectionPolicy.move(12.46,2.13,9.72,true,-2),0);
        near(SelectionPolicy.move(12.46,2.13,9.72,false,20),12.46);
        near(SelectionPolicy.move(12.46,2.13,9.72,true,11),9.71);
        near(SelectionPolicy.move(12.46,2.13,9.72,false,0),2.14);
        near(SelectionPolicy.move(12.456,0,5,false,12.456),12.456);
        near(SelectionPolicy.move(.005,0,.005,true,.004),0);
        near(SelectionPolicy.move(.005,0,.005,false,0),.005);
        check(SelectionPolicy.feedback(false,true,1,0,20)==2);
        check(SelectionPolicy.feedback(true,true,0,0,500)==0);
        check(SelectionPolicy.feedback(false,false,1,2,99)==0);
        check(SelectionPolicy.feedback(false,false,1,2,100)==1);
        check(SelectionPolicy.feedback(false,false,1,1,1000)==0);
        // Repeated touch/keyboard moves cannot reverse or leave the video interval.
        Random r=new Random(54);double a=0,b=12.456;
        for(int i=0;i<1000;i++){boolean first=r.nextBoolean();double next=SelectionPolicy.move(12.456,a,b,first,r.nextDouble()*18-3);if(first)a=next;else b=next;check(SelectionPolicy.valid(12.456,a,b));}
        System.out.println(checks+" selection checks passed");
    }
}
