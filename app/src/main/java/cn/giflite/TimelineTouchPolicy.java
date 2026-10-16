package cn.giflite;

public final class TimelineTouchPolicy {
    private TimelineTouchPolicy(){}
    public static boolean ambiguous(float startX,float endX,float touchX,float closeDistance,float centerRadius){
        return endX-startX<closeDistance&&Math.abs(touchX-(startX+endX)/2)<=centerRadius;
    }
    public static boolean firstFromDirection(float dx){return dx<0;}
}
