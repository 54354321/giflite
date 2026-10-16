package com.izumikonata.giflite;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns the export, independently of Activity lifetime and configuration changes. */
public final class ExportService extends Service {
    public static final String CHANNEL="gif_export", CANCEL="com.izumikonata.giflite.CANCEL", OPEN="com.izumikonata.giflite.OPEN_EXPORT";
    private static final int NOTICE=41;
    private static final long MAX_RUNTIME=2*60*60*1000L;
    public static volatile boolean alive;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private PowerManager.WakeLock wake;
    private boolean started,finished;
    private long lastUpdate;
    private String job="";
    public static SharedPreferences state(Context c){return c.getSharedPreferences("export_state",MODE_PRIVATE);}
    public static void recoverInterrupted(Context c){
        if(!alive&&state(c).getBoolean("running",false))state(c).edit().putBoolean("running",false).putString("status","error").putString("message","上次转换被系统中断，请重新生成。").commit();
    }
    public void onCreate(){super.onCreate();alive=true;getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"GIF 转换进度",NotificationManager.IMPORTANCE_LOW));}
    public IBinder onBind(Intent i){return null;}
    public int onStartCommand(Intent i,int flags,int id){
        if(i==null){stopSelf();return START_NOT_STICKY;}
        if(CANCEL.equals(i.getAction())){cancelled.set(true);if(started&&!finished)publish("正在取消…",state(this).getInt("progress",0));else {finished=true;stopSelf();}return START_NOT_STICKY;}
        if(started)return START_NOT_STICKY;
        started=true;job=i.getStringExtra("job");if(job==null)job=Long.toString(System.currentTimeMillis());
        state(this).edit().clear().putString("job",job).putBoolean("running",true).putString("status","running").putString("message","准备转换，可切换到其他应用。").putInt("progress",0).commit();
        try{
            int type=Build.VERSION.SDK_INT>=35?ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING:ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            startForeground(NOTICE,notification("准备转换…",0,true),type);
            PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"GifLite:Export");wake.acquire(MAX_RUNTIME+10000);
            main.postDelayed(timeout,MAX_RUNTIME);
            worker.execute(()->encode(i));
        }catch(Exception e){finish("error","无法启动后台转换："+detail(e),null,0,0,0,0,0);}
        return START_NOT_STICKY;
    }
    private final Runnable timeout=()->{cancelled.set(true);finish("error","转换已达到后台运行时间上限，请缩短片段后重试。",null,0,0,0,0,0);};
    private Notification notification(String message,int percent,boolean active){
        Intent open=new Intent(this,MainActivity.class).setAction(OPEN).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(active?"轻转 GIF · 转换中":"轻转 GIF").setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message)).setContentIntent(content).setOnlyAlertOnce(true).setOngoing(active).setAutoCancel(!active).setCategory(active?Notification.CATEGORY_PROGRESS:Notification.CATEGORY_STATUS);
        if(active){b.setProgress(100,percent,false);Intent cancel=new Intent(this,ExportService.class).setAction(CANCEL);PendingIntent action=PendingIntent.getService(this,1,cancel,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);b.addAction(new Notification.Action.Builder(null,"取消",action).build());}
        if(Build.VERSION.SDK_INT>=31)b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return b.build();
    }
    private void publish(String message,int percent){
        if(finished)return;
        state(this).edit().putString("message",message).putInt("progress",percent).apply();
        try{getSystemService(NotificationManager.class).notify(NOTICE,notification(message,percent,true));}catch(SecurityException ignored){}
    }
    private void check()throws InterruptedException{if(cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedException();}
    private void update(String text,int p){long now=SystemClock.elapsedRealtime();if(now-lastUpdate>=500||p==100){lastUpdate=now;main.post(()->publish(text,p));}}
    private void encode(Intent request){
        File temp=new File(getCacheDir(),"encoding-"+job+".tmp");
        try(MediaMetadataRetriever r=new MediaMetadataRetriever()){
            Uri uri=request.getData();if(uri==null)throw new IOException("未选择视频");
            double a=request.getDoubleExtra("start",0),b=request.getDoubleExtra("end",0);int rate=request.getIntExtra("fps",15),colors=request.getIntExtra("colors",256),width=request.getIntExtra("width",0),height=request.getIntExtra("height",0),mode=request.getIntExtra("mode",0);long budget=request.getLongExtra("budget",0);boolean shrink=request.getBooleanExtra("shrink",true);
            ExportPolicy.validateDimensions(width,height);
            if(!Double.isFinite(a)||!Double.isFinite(b)||a<0||b<=a||rate<1||rate>30||budget<100000||budget>100000000||!(colors==64||colors==128||colors==256))throw new IOException("导出参数无效");
            int count=ExportPolicy.frameCount(b-a,rate);if(count>18000)throw new IOException("片段过长");
            r.setDataSource(this,uri);double sourceDuration=Double.parseDouble(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))/1000;
            if(b>sourceDuration+.01)throw new IOException("所选片段超过视频时长");
            int edge=Math.max(width,height),round=0;
            while(true){check();round++;int[] dims=ExportPolicy.dimensions(width,height,edge);int w=dims[0],h=dims[1],pass=round;
                try(GifEncoder encoder=new GifEncoder(new BufferedOutputStream(new FileOutputStream(temp)),w,h)){
                    int[] pixels=new int[w*h];Bitmap canvasBitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                    try{Canvas canvas=new Canvas(canvasBitmap);Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG|Paint.DITHER_FLAG);
                        for(int frame=0;frame<count;frame++){
                            check();long us=(long)((a+frame/(double)rate)*1000000);int decodeEdge=Math.min(4096,Math.max(w,h));
                            Bitmap decoded=r.getScaledFrameAtTime(us,MediaMetadataRetriever.OPTION_CLOSEST,decodeEdge,decodeEdge);
                            if(decoded==null)throw new IOException("无法读取第 "+(frame+1)+" 帧");
                            try{canvas.drawColor(Color.BLACK);float[] rect=ExportPolicy.destination(decoded.getWidth(),decoded.getHeight(),w,h,mode);canvas.drawBitmap(decoded,null,new RectF(rect[0],rect[1],rect[2],rect[3]),paint);canvasBitmap.getPixels(pixels,0,w,0,0,w,h);}finally{decoded.recycle();}
                            encoder.frame(w,h,pixels,colors,ExportPolicy.delay(frame,count,b-a,rate));
                            int done=frame+1;update("第 "+pass+" 轮 · "+w+" × "+h+" · "+done+" / "+count+" 帧",done*100/count);
                        }
                    }finally{canvasBitmap.recycle();}
                }
                check();long size=temp.length();
                if(size<=budget){File output=new File(getCacheDir(),"gif-"+System.currentTimeMillis()+".gif");if(!temp.renameTo(output))throw new IOException("无法保存结果");main.post(()->finish("done","GIF 已完成，点此预览或保存到相册。",output,w,h,rate,colors,b-a));return;}
                if(!shrink)throw new IOException("指定分辨率生成的 GIF 超过体积上限。请提高上限，或开启允许自动缩小。");
                if(edge<=64||round>=15)throw new IOException("无法在当前片段、帧率和质量下满足大小限制。请调整参数。");
                edge=ExportPolicy.nextEdge(edge,size,budget);
            }
        }catch(InterruptedException e){main.post(()->finish("cancelled","已取消转换。",null,0,0,0,0,0));}
        catch(Exception|OutOfMemoryError e){String msg=detail(e);main.post(()->finish("error","转换未完成："+msg,null,0,0,0,0,0));}
        finally{temp.delete();}
    }
    private static String detail(Throwable e){return e.getMessage()==null?"请缩小尺寸或更换视频重试":e.getMessage();}
    private void finish(String status,String message,File file,int w,int h,int fps,int colors,double duration){
        if(finished)return;finished=true;
        state(this).edit().putBoolean("running",false).putString("status",status).putString("message",message).putString("file",file==null?"":file.getName()).putInt("width",w).putInt("height",h).putInt("fps",fps).putInt("colors",colors).putLong("durationMs",Math.round(duration*1000)).putInt("progress","done".equals(status)?100:0).commit();
        main.removeCallbacks(timeout);releaseWake();stopForeground(STOP_FOREGROUND_REMOVE);
        try{getSystemService(NotificationManager.class).notify(NOTICE,notification(message,100,false));}catch(SecurityException ignored){}
        stopSelf();
    }
    private void releaseWake(){if(wake!=null&&wake.isHeld())wake.release();}
    @Override public void onTimeout(int startId,int fgsType){cancelled.set(true);finish("error","系统限制了本次后台运行，请重新转换。",null,0,0,0,0,0);}
    public void onDestroy(){alive=false;cancelled.set(true);worker.shutdownNow();main.removeCallbacksAndMessages(null);releaseWake();if(started&&!finished){state(this).edit().putBoolean("running",false).putString("status","error").putString("message","后台转换已中断，请重新生成。").commit();}finished=true;super.onDestroy();}
}
