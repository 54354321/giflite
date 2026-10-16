import cn.giflite.ExportPolicy;

public final class GeometryTest {
    private static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static boolean near(float a,float b){return Math.abs(a-b)<.01f;}
    public static void main(String[] args){
        int[] a=ExportPolicy.fitRatio(ExportPolicy.ratio(0,1080,2400),1280);
        check(a[0]==576&&a[1]==1280,"portrait source retains aspect");
        a=ExportPolicy.fitRatio(ExportPolicy.ratio(3,1080,2400),1280);
        check(a[0]==1280&&a[1]==720,"16:9 preset");
        a=ExportPolicy.fitRatio(ExportPolicy.ratio(4,1920,1080),1280);
        check(a[0]==720&&a[1]==1280,"9:16 preset");
        a=ExportPolicy.fitRatio(ExportPolicy.ratio(1,1920,1080),1280);
        check(a[0]==1280&&a[1]==1280,"square preset");
        float[] crop=ExportPolicy.destination(1920,1080,600,600,0);
        check(crop[0]<0&&near(crop[1],0)&&near(crop[3],600),"crop trims horizontal edges");
        check(near(crop[0]+crop[2],600),"crop is centered");
        float[] fit=ExportPolicy.destination(1920,1080,600,600,1);
        check(near(fit[0],0)&&near(fit[2],600)&&near(fit[1],131.25f)&&near(fit[3],468.75f),"letterbox keeps entire frame");
        float[] stretch=ExportPolicy.destination(1920,1080,600,600,2);
        check(near(stretch[0],0)&&near(stretch[1],0)&&near(stretch[2],600)&&near(stretch[3],600),"stretch fills requested bounds");
        float[] normal=ExportPolicy.destination(1080,2400,540,1200,0);
        check(near(normal[0],0)&&near(normal[1],0)&&near(normal[2],540)&&near(normal[3],1200),"original ratio does not crop");
        ExportPolicy.validateDimensions(1080,2400);checks++;
        for(int[] bad:new int[][]{{0,100},{-1,100},{15,500},{4097,16},{4096,4096}}){boolean rejected=false;try{ExportPolicy.validateDimensions(bad[0],bad[1]);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"reject invalid / excessive dimensions");}
        for(int edge:new int[]{1280,1024,640,320,128,64}){int[] d=ExportPolicy.dimensions(800,1200,edge);check(Math.abs(d[0]/(double)d[1]-2.0/3)<.015,"retry preserves custom ratio");}
        System.out.println("Geometry tests passed: "+checks+" assertions");
    }
}
