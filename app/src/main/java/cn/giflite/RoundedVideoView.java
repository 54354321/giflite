package cn.giflite;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;

/** Texture-backed preview: corner clipping applies to the video itself, not blank margins. */
public final class RoundedVideoView extends TextureView implements TextureView.SurfaceTextureListener {
    private MediaPlayer player;
    private Surface surface;
    private Uri uri;
    private boolean prepared,wantsPlay,seeking,foreground=true;
    private Runnable playbackStarted;
    private java.util.function.Consumer<String> previewState;
    public void setPreviewStateListener(java.util.function.Consumer<String> listener){previewState=listener;}
    private String lastState="";
    private void state(String message){if(message.equals(lastState))return;lastState=message;if(previewState!=null)previewState.accept(message);}
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable timeout=()->{if(player!=null)reportError(player,MediaPlayer.MEDIA_ERROR_TIMED_OUT,0);};
    private int issuedSeek;
    private int videoWidth,videoHeight,pendingSeek;
    private MediaPlayer.OnPreparedListener preparedListener;
    private MediaPlayer.OnCompletionListener completionListener;
    private MediaPlayer.OnErrorListener errorListener;
    public RoundedVideoView(Context c){
        super(c);setOpaque(false);setSurfaceTextureListener(this);setClipToOutline(true);
        final float radius=8*c.getResources().getDisplayMetrics().density;
        setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View v,Outline o){o.setRoundRect(0,0,v.getWidth(),v.getHeight(),radius);}});
    }
    public void setOnPlaybackStarted(Runnable listener){playbackStarted=listener;}
    public void setOnPreparedListener(MediaPlayer.OnPreparedListener listener){preparedListener=listener;}
    public void setOnCompletionListener(MediaPlayer.OnCompletionListener listener){completionListener=listener;}
    public void setOnErrorListener(MediaPlayer.OnErrorListener listener){errorListener=listener;}
    public void setVideoURI(Uri value){releasePlayer();uri=value;pendingSeek=1;wantsPlay=false;videoWidth=0;videoHeight=0;requestLayout();if(isAvailable())open();}
    private void open(){
        if(!foreground||uri==null||!isAvailable()||player!=null)return;
        try{
            state("正在载入预览…");MediaPlayer p=new MediaPlayer();player=p;surface=new Surface(getSurfaceTexture());p.setSurface(surface);p.setDataSource(getContext(),uri);
            p.setOnVideoSizeChangedListener((mp,w,h)->{if(mp!=player)return;videoWidth=w;videoHeight=h;requestLayout();invalidateOutline();});
            p.setOnPreparedListener(mp->{if(mp!=player)return;handler.removeCallbacks(timeout);prepared=true;videoWidth=mp.getVideoWidth();videoHeight=mp.getVideoHeight();requestLayout();seekTo(pendingSeek);if(preparedListener!=null)preparedListener.onPrepared(mp);});
            p.setOnCompletionListener(mp->{if(mp!=player)return;wantsPlay=false;if(completionListener!=null)completionListener.onCompletion(mp);});
            p.setOnErrorListener((mp,what,extra)->{if(mp!=player)return true;reportError(mp,what,extra);return true;});
            p.setOnSeekCompleteListener(mp->{if(mp!=player)return;handler.removeCallbacks(timeout);seeking=false;state("");if(pendingSeek!=issuedSeek){issueSeek();}else if(wantsPlay){startPrepared();}});
            p.prepareAsync();handler.postDelayed(timeout,8000);
        }catch(Exception e){reportError(player,MediaPlayer.MEDIA_ERROR_UNKNOWN,0);}
    }
    private void reportError(MediaPlayer failed,int what,int extra){wantsPlay=false;releasePlayer();state("预览暂时失败\n点击播放重试");if(errorListener!=null)errorListener.onError(failed,what,extra);}
    public void playFrom(int position){pause();wantsPlay=true;seekTo(position);}
    private void startPrepared(){if(!prepared||player==null||!foreground)return;try{player.start();if(playbackStarted!=null)playbackStarted.run();}catch(IllegalStateException e){reportError(player,MediaPlayer.MEDIA_ERROR_UNKNOWN,0);}}
    public void pause(){wantsPlay=false;if(prepared&&player!=null)try{if(player.isPlaying())player.pause();}catch(IllegalStateException e){reportError(player,MediaPlayer.MEDIA_ERROR_UNKNOWN,0);}}
    public void suspendPreview(){pause();pendingSeek=getCurrentPosition();foreground=false;releasePlayer();}
    public void resumePreview(){foreground=true;open();}
    public int getCurrentPosition(){if(seeking)return pendingSeek;if(prepared&&player!=null)try{return player.getCurrentPosition();}catch(IllegalStateException ignored){}return pendingSeek;}
    public void seekTo(int position){pendingSeek=Math.max(0,position);if(player==null)open();else if(prepared&&!seeking)issueSeek();}
    private void issueSeek(){if(player==null||!prepared)return;try{issuedSeek=pendingSeek;seeking=true;player.seekTo(issuedSeek,MediaPlayer.SEEK_CLOSEST);handler.removeCallbacks(timeout);handler.postDelayed(timeout,3000);}catch(IllegalStateException e){seeking=false;reportError(player,MediaPlayer.MEDIA_ERROR_UNKNOWN,0);}}
    public void stopPlayback(){wantsPlay=false;uri=null;pendingSeek=0;releasePlayer();}
    private void releasePlayer(){handler.removeCallbacks(timeout);prepared=false;seeking=false;if(player!=null){player.setOnSeekCompleteListener(null);player.setOnPreparedListener(null);player.setOnErrorListener(null);player.setOnCompletionListener(null);player.setOnVideoSizeChangedListener(null);player.release();player=null;}if(surface!=null){surface.release();surface=null;}}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int w=View.getDefaultSize(0,widthSpec),h=View.getDefaultSize(0,heightSpec);
        if(videoWidth>0&&videoHeight>0){double scale=Math.min(w/(double)videoWidth,h/(double)videoHeight);w=Math.max(1,(int)Math.round(videoWidth*scale));h=Math.max(1,(int)Math.round(videoHeight*scale));}
        setMeasuredDimension(w,h);
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);invalidateOutline();}
    public void onSurfaceTextureAvailable(SurfaceTexture texture,int w,int h){open();}
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int w,int h){}
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture){pendingSeek=getCurrentPosition();wantsPlay=false;releasePlayer();return true;}
    public void onSurfaceTextureUpdated(SurfaceTexture texture){if(prepared)state("");}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();open();}
    @Override protected void onDetachedFromWindow(){pendingSeek=getCurrentPosition();wantsPlay=false;releasePlayer();super.onDetachedFromWindow();}
}
