import com.izumikonata.giflite.*;
import java.io.*;
import java.util.*;

public class EncoderTest {
    public static void main(String[] args)throws Exception {
        int w=193,h=127;Random random=new Random(712);
        for(int colors:new int[]{64,128,256}){
            try(GifEncoder enc=new GifEncoder(new FileOutputStream("work/test-"+colors+".gif"),w,h)){
                for(int frame=0;frame<6;frame++){
                    int[] p=new int[w*h];
                    for(int y=0;y<h;y++)for(int x=0;x<w;x++)p[y*w+x]=frame==0?0xffffff:frame==1?0:frame==2?((x*255/w)<<16)|((y*255/h)<<8):random.nextInt(0xffffff);
                    enc.frame(w,h,p,colors,ExportPolicy.delay(frame,6,.4,15));
                }
            }
        }
        int[] d=ExportPolicy.dimensions(1080,2400,1280);
        if(d[0]!=576||d[1]!=1280)throw new AssertionError("portrait ratio");
        d=ExportPolicy.dimensions(320,180,1280);if(d[0]!=320||d[1]!=180)throw new AssertionError("upscale");
        if(ExportPolicy.nextEdge(1280,10000000,5000000)>=1280)throw new AssertionError("shrink");
        int total=0;for(int i=0;i<150;i++)total+=ExportPolicy.delay(i,150,10,15);
        if(total!=1000)throw new AssertionError("timing drift");
        System.out.println("Export policy assertions passed; GIF fixtures generated.");
    }
}
