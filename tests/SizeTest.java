import cn.giflite.*;
import java.io.*;

public class SizeTest {
    public static void main(String[] args)throws Exception {
        int edge=640,round=0;long budget=100000;
        File file=new File("work/size-limited.gif");
        while(true){
            round++;int[] d=ExportPolicy.dimensions(1080,2400,edge);
            try(GifEncoder e=new GifEncoder(new FileOutputStream(file),d[0],d[1])){
                for(int f=0;f<30;f++){
                    int[] p=new int[d[0]*d[1]];
                    for(int y=0;y<d[1];y++)for(int x=0;x<d[0];x++)p[y*d[0]+x]=((x*255/d[0])<<16)|(((y*255/d[1]+f*7)&255)<<8)|(f*8);
                    e.frame(d[0],d[1],p,256,ExportPolicy.delay(f,30,2,15));
                }
            }
            System.out.println("Round "+round+": "+d[0]+"x"+d[1]+", "+file.length()+" bytes");
            if(file.length()<=budget){if(round<2)throw new AssertionError("Expected retry");break;}
            if(edge<=64||round>=15)throw new AssertionError("Budget not reached");
            edge=ExportPolicy.nextEdge(edge,file.length(),budget);
        }
    }
}
