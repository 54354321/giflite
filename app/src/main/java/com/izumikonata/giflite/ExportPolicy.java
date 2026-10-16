package com.izumikonata.giflite;

public final class ExportPolicy {
    private ExportPolicy(){}
    public static double ratio(int choice,int sourceWidth,int sourceHeight){
        switch(choice){case 1:return 1;case 2:return 4.0/3;case 3:return 16.0/9;case 4:return 9.0/16;default:return sourceWidth/(double)sourceHeight;}
    }
    public static int[] fitRatio(double ratio,int edge){
        if(!Double.isFinite(ratio)||ratio<=0||edge<1)throw new IllegalArgumentException("无效比例");
        return ratio>=1?new int[]{edge,Math.max(1,(int)Math.round(edge/ratio))}:new int[]{Math.max(1,(int)Math.round(edge*ratio)),edge};
    }
    public static void validateDimensions(int w,int h){
        if(w<16||h<16||w>4096||h>4096||(long)w*h>4194304)throw new IllegalArgumentException("宽高须为 16–4096 像素，且总像素不超过 419 万。");
    }
    /** Destination rectangle: 0=center crop, 1=letterbox, 2=stretch. */
    public static float[] destination(int sw,int sh,int dw,int dh,int mode){
        if(mode==2)return new float[]{0,0,dw,dh};
        double scale=mode==1?Math.min(dw/(double)sw,dh/(double)sh):Math.max(dw/(double)sw,dh/(double)sh);
        float w=(float)(sw*scale),h=(float)(sh*scale);
        return new float[]{(dw-w)/2,(dh-h)/2,(dw+w)/2,(dh+h)/2};
    }
    public static int[] dimensions(int width,int height,int longEdge){
        double scale=Math.min(1.0,longEdge/(double)Math.max(width,height));
        return new int[]{Math.max(1,(int)Math.round(width*scale)),Math.max(1,(int)Math.round(height*scale))};
    }
    public static int nextEdge(int edge,long actual,long limit){
        double factor=Math.min(.85,Math.sqrt(limit/(double)actual)*.92);
        return Math.max(64,Math.min(edge-1,(int)Math.floor(edge*factor)));
    }
    public static int frameCount(double duration,int fps){return Math.max(1,(int)Math.ceil(duration*fps));}
    public static int delay(int frame,int count,double duration,int fps){
        double a=frame/(double)fps,b=Math.min(duration,(frame+1)/(double)fps);
        return Math.max(1,(int)Math.round(b*100)-(int)Math.round(a*100));
    }
}
