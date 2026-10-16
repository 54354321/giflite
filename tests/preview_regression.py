"""Compile the actual preview class against a strict fake Android media state machine.
Not a device/decoder/rendering test. Usage: python preview_regression.py JDK OUTDIR
"""
import subprocess, sys
from pathlib import Path
jdk, out = Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve()
out.mkdir(parents=True, exist_ok=True)
stubs = {
"android/content/Context.java": "package android.content; public class Context {public android.content.res.Resources getResources(){return new android.content.res.Resources();}}",
"android/content/res/Resources.java": "package android.content.res; public class Resources {public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();}}",
"android/util/DisplayMetrics.java": "package android.util; public class DisplayMetrics {public float density=1;}",
"android/graphics/Outline.java": "package android.graphics; public class Outline {public void setRoundRect(int a,int b,int c,int d,float r){}}",
"android/graphics/SurfaceTexture.java": "package android.graphics; public class SurfaceTexture {}",
"android/net/Uri.java": "package android.net; public class Uri {}",
"android/os/Looper.java": "package android.os; public class Looper {public static Looper getMainLooper(){return new Looper();}}",
"android/os/Handler.java": "package android.os; public class Handler {public static Runnable pending;public Handler(Looper l){}public void removeCallbacks(Runnable r){if(pending==r)pending=null;}public void postDelayed(Runnable r,long delay){pending=r;}}",
"android/view/ViewOutlineProvider.java": "package android.view; public abstract class ViewOutlineProvider {public abstract void getOutline(View v,android.graphics.Outline o);}",
"android/view/View.java": """package android.view; public class View {private android.content.Context c;public View(android.content.Context c){this.c=c;}public android.content.Context getContext(){return c;}public int getWidth(){return 100;}public int getHeight(){return 200;}public static int getDefaultSize(int a,int b){return b;}public void requestLayout(){}public void invalidateOutline(){}public void setClipToOutline(boolean b){}public void setOutlineProvider(ViewOutlineProvider p){}protected void onMeasure(int a,int b){}protected void setMeasuredDimension(int a,int b){}protected void onSizeChanged(int a,int b,int c,int d){}protected void onAttachedToWindow(){}protected void onDetachedFromWindow(){}}""",
"android/view/Surface.java": "package android.view; public class Surface {public Surface(android.graphics.SurfaceTexture t){}public void release(){}}",
"android/view/TextureView.java": """package android.view; public class TextureView extends View {public boolean available=true;public TextureView(android.content.Context c){super(c);}public void setOpaque(boolean b){}public boolean isAvailable(){return available;}public android.graphics.SurfaceTexture getSurfaceTexture(){return new android.graphics.SurfaceTexture();}public void setSurfaceTextureListener(SurfaceTextureListener l){}public interface SurfaceTextureListener {void onSurfaceTextureAvailable(android.graphics.SurfaceTexture t,int w,int h);void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture t,int w,int h);boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture t);void onSurfaceTextureUpdated(android.graphics.SurfaceTexture t);}}""",
"android/media/MediaPlayer.java": """package android.media;
public class MediaPlayer {
 public static final int MEDIA_ERROR_UNKNOWN=1,MEDIA_ERROR_TIMED_OUT=-110,SEEK_CLOSEST=3;
 public static MediaPlayer latest;public int state=0,position,seekTarget,seekCount,pauseCount,startCount;public boolean released;
 public interface OnPreparedListener{void onPrepared(MediaPlayer p);}public interface OnCompletionListener{void onCompletion(MediaPlayer p);}public interface OnErrorListener{boolean onError(MediaPlayer p,int w,int e);}public interface OnVideoSizeChangedListener{void onVideoSizeChanged(MediaPlayer p,int w,int h);}public interface OnSeekCompleteListener{void onSeekComplete(MediaPlayer p);}
 private OnPreparedListener prepared;private OnCompletionListener complete;private OnErrorListener error;private OnSeekCompleteListener seek;
 public MediaPlayer(){latest=this;}public void setSurface(android.view.Surface s){}public void setDataSource(android.content.Context c,android.net.Uri u){state=1;}public void prepareAsync(){state=2;}
 public void ready(){state=3;prepared.onPrepared(this);}public void finishSeek(){position=seekTarget;seek.onSeekComplete(this);}
 public void start(){if(state<3||released)throw new AssertionError("start invalid");state=4;startCount++;}
 public void pause(){if(state!=4&&state!=5&&state!=6)throw new AssertionError("pause invalid state "+state);state=5;pauseCount++;}
 public boolean isPlaying(){return state==4;}public int getCurrentPosition(){return position;}public int getVideoWidth(){return 1080;}public int getVideoHeight(){return 2400;}
 public void seekTo(long n,int mode){if(state<3||released)throw new AssertionError("seek invalid");seekTarget=(int)n;seekCount++;}public void release(){released=true;state=-1;}
 public void setOnPreparedListener(OnPreparedListener l){prepared=l;}public void setOnCompletionListener(OnCompletionListener l){complete=l;}public void setOnErrorListener(OnErrorListener l){error=l;}public void setOnVideoSizeChangedListener(OnVideoSizeChangedListener l){}public void setOnSeekCompleteListener(OnSeekCompleteListener l){seek=l;}
}
""",
"com/izumikonata/giflite/PreviewRegression.java": """package com.izumikonata.giflite;
import android.media.MediaPlayer;import android.content.Context;import android.net.Uri;import android.os.Handler;
public class PreviewRegression {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 public static void main(String[] args){
  RoundedVideoView v=new RoundedVideoView(new Context());int[] starts={0},errors={0};v.setOnPlaybackStarted(()->starts[0]++);v.setOnErrorListener((p,w,e)->{errors[0]++;return true;});
  v.setVideoURI(new Uri());MediaPlayer p=MediaPlayer.latest;p.ready();p.finishSeek();
  v.pause();check(p.pauseCount==0); // Prepared is NOT a legal pause state.
  v.seekTo(2130);p.finishSeek();check(p.position==2130);
  v.playFrom(1000);check(p.startCount==0);p.finishSeek();check(p.isPlaying()&&starts[0]==1);
  p.position=1500;v.pause();check(p.pauseCount==1);v.pause();check(p.pauseCount==1);
  v.seekTo(2000);v.seekTo(4000);v.seekTo(6000);int issued=p.seekCount;p.finishSeek();check(p.seekCount==issued+1);p.finishSeek();check(p.position==6000);
  v.suspendPreview();check(p.released);v.resumePreview();MediaPlayer resumed=MediaPlayer.latest;check(resumed!=p);resumed.ready();resumed.finishSeek();check(resumed.position==6000&&!resumed.isPlaying());
  resumed.position=6500;v.onSurfaceTextureDestroyed(v.getSurfaceTexture());check(resumed.released);v.onSurfaceTextureAvailable(v.getSurfaceTexture(),100,200);MediaPlayer restored=MediaPlayer.latest;restored.ready();restored.finishSeek();check(restored.position==6500);
  v.onDetachedFromWindow();check(restored.released);v.onAttachedToWindow();MediaPlayer attached=MediaPlayer.latest;check(attached!=restored);attached.ready();attached.finishSeek();
  v.playFrom(2000);Runnable timeout=Handler.pending;timeout.run();check(errors[0]==1&&attached.released);
  v.playFrom(1000);MediaPlayer retry=MediaPlayer.latest;check(retry!=attached);retry.ready();retry.finishSeek();check(retry.isPlaying());
  v.suspendPreview();MediaPlayer old=MediaPlayer.latest;v.setVideoURI(new Uri());check(MediaPlayer.latest==old);v.resumePreview();check(MediaPlayer.latest!=old);
  v.stopPlayback();check(Handler.pending==null);System.out.println(checks+" preview state regression checks passed");
 }
}
"""
}
for name, content in stubs.items():
    f=out/name; f.parent.mkdir(parents=True, exist_ok=True); f.write_text(content, encoding="utf-8")
source=Path(__file__).resolve().parents[1]/"app/src/main/java/com/izumikonata/giflite/RoundedVideoView.java"
subprocess.run([str(jdk/"bin/javac.exe"),"-encoding","UTF-8","-d",str(out),*map(str,(out/n for n in stubs)),str(source)],check=True)
subprocess.run([str(jdk/"bin/java.exe"),"-cp",str(out),"com.izumikonata.giflite.PreviewRegression"],check=True)
