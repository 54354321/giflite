import com.izumikonata.giflite.UiPolicy;
public class UiPolicyTest {
    private static int count;
    private static void check(boolean ok,String why){count++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        check(!UiPolicy.restoreSession(false,false,false),"fresh launch must not restore old form/results");
        check(UiPolicy.restoreSession(true,false,false),"theme recreation keeps current form");
        check(UiPolicy.restoreSession(false,true,false),"live export can resume its UI");
        check(UiPolicy.restoreSession(false,false,true),"explicit notification opens result");
        check(!UiPolicy.changesRatio(1080,2400,576,1280),"same aspect hides framing options");
        check(!UiPolicy.changesRatio(1080,2400,576,1280),"rounding is not a ratio change");
        check(UiPolicy.changesRatio(1080,2400,720,720),"square shows framing options");
        check(UiPolicy.changesRatio(1080,2400,800,1200),"free aspect shows framing options");
        check(!UiPolicy.changesRatio(0,0,720,720),"empty screen hides framing options");
        check(!UiPolicy.tick(5,5,100),"unchanged value is silent");
        check(!UiPolicy.tick(5,6,30),"throttle continuous haptics");
        check(UiPolicy.tick(5,6,80),"value crossing produces a tick");
        System.out.println("UI policy tests passed: "+count);
    }
}
