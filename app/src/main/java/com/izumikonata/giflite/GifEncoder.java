package com.izumikonata.giflite;

import java.io.*;
import java.util.*;

/** Streaming GIF89a encoder. Each frame has its own adaptive palette. */
public final class GifEncoder implements Closeable {
    private final OutputStream out;
    private boolean closed;
    public GifEncoder(OutputStream out, int width, int height) throws IOException {
        this.out=out;
        out.write("GIF89a".getBytes("US-ASCII")); word(width); word(height);
        out.write(0x70); out.write(0); out.write(0);
        out.write(new byte[]{0x21,(byte)0xff,11}); out.write("NETSCAPE2.0".getBytes("US-ASCII"));
        out.write(new byte[]{3,1,0,0,0});
    }
    private void word(int n) throws IOException { out.write(n & 255); out.write((n>>>8)&255); }
    public void frame(int width,int height,int[] pixels,int colors,int delay) throws IOException {
        Palette p=new Palette(pixels,colors);
        out.write(new byte[]{0x21,(byte)0xf9,4,4}); word(Math.max(1,delay)); out.write(0);out.write(0);
        out.write(0x2c); word(0);word(0);word(width);word(height);
        int bits=colors==256?8:colors==128?7:6;
        out.write(0x80 | (bits-1));out.write(p.table);
        out.write(bits);
        encode(p.indices,bits);
    }
    private void encode(byte[] pixels,int bits) throws IOException {
        int clear=1<<bits, end=clear+1, next=end+1, size=bits+1;
        HashMap<Integer,Integer> dictionary=new HashMap<>(4096);
        Blocks b=new Blocks(out);b.code(clear,size);
        int prefix=pixels[0]&255;
        for(int i=1;i<pixels.length;i++) {
            int value=pixels[i]&255, key=(prefix<<8)|value;
            Integer found=dictionary.get(key);
            if(found!=null){prefix=found;continue;}
            b.code(prefix,size);
            // The decoder creates entries one emitted code behind the encoder.
            if(next==(1<<size) && size<12) size++;
            if(next<4096) dictionary.put(key,next++);
            else {b.code(clear,size);dictionary.clear();next=end+1;size=bits+1;}
            prefix=value;
        }
        b.code(prefix,size);
        if(next==(1<<size)&&size<12)size++;
        b.code(end,size);b.finish();
    }
    public void close() throws IOException {if(!closed){closed=true;out.write(0x3b);out.close();}}
    private static final class Blocks {
        final OutputStream out;final byte[] block=new byte[255];int count,buffer,used;
        Blocks(OutputStream o){out=o;}
        void code(int code,int size)throws IOException {buffer|=code<<used;used+=size;while(used>=8){put(buffer&255);buffer>>>=8;used-=8;}}
        void put(int v)throws IOException{block[count++]=(byte)v;if(count==255)flush();}
        void flush()throws IOException{if(count>0){out.write(count);out.write(block,0,count);count=0;}}
        void finish()throws IOException{if(used>0)put(buffer&255);flush();out.write(0);}
    }
    private static final class Palette {
        final byte[] table,indices;
        Palette(int[] pixels,int colors) {
            int[] hist=new int[32768];for(int p:pixels)hist[key(p)]++;
            ArrayList<Integer> used=new ArrayList<>();for(int i=0;i<hist.length;i++)if(hist[i]>0)used.add(i);
            ArrayList<Box> boxes=new ArrayList<>();boxes.add(new Box(used,hist));
            while(boxes.size()<colors){Box best=null;for(Box b:boxes)if(b.values.size()>1&&(best==null||b.score()>best.score()))best=b;
                if(best==null)break;boxes.remove(best);final int channel=best.axis;
                best.values.sort(Comparator.comparingInt(v->component(v,channel)));
                long sum=0;int cut=0;for(;cut<best.values.size()-1;cut++){sum+=hist[best.values.get(cut)];if(sum*2>=best.weight){cut++;break;}}
                cut=Math.max(1,Math.min(cut,best.values.size()-1));
                boxes.add(new Box(new ArrayList<>(best.values.subList(0,cut)),hist));boxes.add(new Box(new ArrayList<>(best.values.subList(cut,best.values.size())),hist));
            }
            table=new byte[colors*3];int[] rgb=new int[boxes.size()];
            for(int i=0;i<boxes.size();i++){Box b=boxes.get(i);long r=0,g=0,bl=0;for(int v:b.values){r+=(long)component(v,0)*hist[v];g+=(long)component(v,1)*hist[v];bl+=(long)component(v,2)*hist[v];}
                int rr=(int)Math.round(r*255.0/b.weight/31),gg=(int)Math.round(g*255.0/b.weight/31),bb=(int)Math.round(bl*255.0/b.weight/31);
                table[i*3]=(byte)rr;table[i*3+1]=(byte)gg;table[i*3+2]=(byte)bb;rgb[i]=(rr<<16)|(gg<<8)|bb;}
            int[] lookup=new int[32768];Arrays.fill(lookup,-1);indices=new byte[pixels.length];
            for(int i=0;i<pixels.length;i++){int k=key(pixels[i]);if(lookup[k]<0){int r=component(k,0)*255/31,g=component(k,1)*255/31,b=component(k,2)*255/31,best=0;long distance=Long.MAX_VALUE;
                    for(int j=0;j<rgb.length;j++){int dr=r-((rgb[j]>>16)&255),dg=g-((rgb[j]>>8)&255),db=b-(rgb[j]&255);long d=2L*dr*dr+3L*dg*dg+db*db;if(d<distance){distance=d;best=j;}}
                    lookup[k]=best;}
                indices[i]=(byte)lookup[k];}
        }
        static int key(int p){return ((p>>19)&31)<<10|((p>>11)&31)<<5|((p>>3)&31);}
        static int component(int p,int axis){return (p>>(10-axis*5))&31;}
    }
    private static final class Box {
        final ArrayList<Integer> values;long weight;int axis,range;
        Box(ArrayList<Integer> v,int[] hist){values=v;int[] lo={31,31,31},hi={0,0,0};for(int k:v){weight+=hist[k];for(int a=0;a<3;a++){int c=Palette.component(k,a);lo[a]=Math.min(lo[a],c);hi[a]=Math.max(hi[a],c);}}
            for(int a=0;a<3;a++)if(hi[a]-lo[a]>range){range=hi[a]-lo[a];axis=a;}}
        long score(){return (long)range*weight;}
    }
}
