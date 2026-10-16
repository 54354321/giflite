import com.izumikonata.giflite.ColorPolicy;

public final class ColorPolicyTest {
    private static int checks;
    private static void check(boolean condition){checks++;if(!condition)throw new AssertionError("Check "+checks);}
    public static void main(String[] args){
        int[] light={0xfffafafa,0xffffffff,0xff171717,0xff444444,0xff385eab,0xffffffff,0xffe2e9ff};
        int[] dark={0xff131313,0xff292929,0xfffafafa,0xffdddddd,0xffadc6ff,0xff123066,0xff253450};
        int[] fallback={0xfff5f6f2,0xffffffff,0xff1c2b25,0xff637067,0xff16755d,0xffffffff,0xffe9f4ed};
        check(ColorPolicy.usable(light));check(ColorPolicy.usable(dark));
        check(ColorPolicy.select(true,light,fallback)==light);
        check(ColorPolicy.select(true,dark,fallback)==dark);
        check(ColorPolicy.select(false,light,fallback)==fallback);
        check(ColorPolicy.select(true,null,fallback)==fallback);
        check(ColorPolicy.select(true,new int[2],fallback)==fallback);
        int[] transparent=light.clone();transparent[4]=0x00385eab;
        check(ColorPolicy.select(true,transparent,fallback)==fallback);
        int[] unreadable=light.clone();unreadable[2]=unreadable[0];
        check(ColorPolicy.select(true,unreadable,fallback)==fallback);
        check(ColorPolicy.contrast(0xff000000,0xffffffff)==21);
        System.out.println(checks+" color policy checks passed");
    }
}
