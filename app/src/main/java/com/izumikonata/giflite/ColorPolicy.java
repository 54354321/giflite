package com.izumikonata.giflite;

public final class ColorPolicy {
    private ColorPolicy(){}
    private static double linear(int c){double v=c/255.0;return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);}
    private static double luminance(int c){return .2126*linear((c>>16)&255)+.7152*linear((c>>8)&255)+.0722*linear(c&255);}
    public static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    /** Order: page, surface, ink, muted, accent, on-accent, soft. */
    public static boolean usable(int[] colors){
        if(colors==null||colors.length!=7)return false;
        for(int c:colors)if((c>>>24)!=255)return false;
        return contrast(colors[2],colors[0])>=4.5&&contrast(colors[2],colors[1])>=4.5
            &&contrast(colors[3],colors[0])>=4.5&&contrast(colors[3],colors[1])>=4.5
            &&contrast(colors[5],colors[4])>=4.5&&contrast(colors[4],colors[6])>=4.5;
    }
    public static int[] select(boolean enabled,int[] system,int[] fallback){return enabled&&usable(system)?system:fallback;}
}
