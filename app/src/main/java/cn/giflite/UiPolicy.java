package cn.giflite;

/** Pure policies shared by the UI and regression tests. */
public final class UiPolicy {
    private UiPolicy(){}
    public static boolean restoreSession(boolean sameProcess,boolean running,boolean notification){return sameProcess||running||notification;}
    public static boolean changesRatio(int sw,int sh,int dw,int dh){
        if(sw<=0||sh<=0||dw<=0||dh<=0)return false;
        // Ignore the unavoidable <= one-pixel rounding when keeping the source aspect.
        return Math.abs(dw*sh/(double)sw-dh)>1.0&&Math.abs(dh*sw/(double)sh-dw)>1.0;
    }
    public static boolean tick(int previous,int current,long elapsed){return previous!=current&&elapsed>=65;}
}
