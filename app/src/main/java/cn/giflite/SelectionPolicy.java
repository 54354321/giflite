package cn.giflite;

/** One interval, in seconds, shared by touch, keyboard and precise inputs. */
public final class SelectionPolicy {
    private SelectionPolicy(){}
    public static boolean valid(double duration,double start,double end){return Double.isFinite(duration)&&Double.isFinite(start)&&Double.isFinite(end)&&duration>0&&start>=0&&end>start&&end<=duration+.000001;}
    public static double move(double duration,double start,double end,boolean first,double value){
        if(!Double.isFinite(value))return first?start:end;
        double gap=Math.min(.01,duration);
        return first?Math.max(0,Math.min(end-gap,value)):Math.min(duration,Math.max(start+gap,value));
    }
    public static int tick(double seconds){return (int)Math.floor(seconds*2+.000001);}
    public static boolean boundary(double duration,double start,double end,boolean first){return first?start<=.000001:end>=duration-.000001;}
    public static int feedback(boolean wasBoundary,boolean boundary,int previousTick,int tick,long elapsed){
        if(boundary&&!wasBoundary)return 2;
        return previousTick!=tick&&elapsed>=100?1:0;
    }
}
