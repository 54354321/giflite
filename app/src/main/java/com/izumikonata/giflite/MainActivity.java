package com.izumikonata.giflite;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.Editable;
import android.Manifest;
import android.content.pm.PackageManager;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final String PROCESS_TOKEN=UUID.randomUUID().toString();
    private int ink,muted,green,page,surface,onAccent,soft,paletteSignature;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout root,settings,resultCard;
    private ScrollView scroll;
    private Dialog appearanceDialog;
    private LinearLayout appearanceContent;
    private TextView status,sourceInfo,resultInfo;
    private EditText start,end,limit,targetWidth,targetHeight;
    private Spinner fps,quality,ratio,framing;
    private CheckBox customSize,allowShrink;
    private LinearLayout sizeFields,framingFields;
    private TextView sizeInfo;
    private int sourceWidth=720,sourceHeight=1280;
    private boolean adjustingSize,loading,saving;
    private Intent pendingExport;
    private String pendingJob="",seenJob="";
    private long pendingSince;
    private final Runnable poll=new Runnable(){public void run(){syncExport();handler.postDelayed(this,500);}};
    private RangeTimeline timeline;
    private TextView selectedDuration;
    private boolean adjustingTime;
    private double previewEnd;
    private Button pick,convert,cancel,save,share,preview;
    private ProgressBar progress;
    private RoundedVideoView video;
    private FrameLayout videoFrame;
    private ImageView gif;
    private Uri source;
    private double duration;
    private File result;
    private boolean busy,previewing;
    private final Runnable stopPreview=new Runnable(){public void run(){if(previewing){if(video.getCurrentPosition()>=Math.round(previewEnd*1000)){pauseSelection();video.seekTo((int)Math.round(previewEnd*1000));}else handler.postDelayed(this,20);}}};

    public void onCreate(Bundle state){
        super.onCreate(state);AppPalette palette=AppPalette.read(this);paletteSignature=palette.signature;
        if(palette.dynamic)getTheme().applyStyle(R.style.DynamicControls,true);
        ink=palette.ink;muted=palette.muted;green=palette.accent;page=palette.page;surface=palette.surface;onAccent=palette.onAccent;soft=palette.soft;
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(page);
        ExportService.recoverInterrupted(this);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(page);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(22),dp(16),dp(22),dp(24));scroll.addView(root);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});setContentView(scroll);
        LinearLayout titleRow=row();titleRow.setGravity(Gravity.CENTER_VERTICAL);add(root,titleRow,4);
        TextView title=text("轻转 GIF",40,ink);title.setTypeface(Typeface.create("sans-serif-medium",0));titleRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button settingsEntry=button("设置",false);settingsEntry.setTextSize(14);settingsEntry.setPadding(dp(12),0,dp(12),0);settingsEntry.setMinWidth(0);settingsEntry.setMinimumWidth(0);titleRow.addView(settingsEntry,new LinearLayout.LayoutParams(dp(64),dp(48)));settingsEntry.setOnClickListener(v->showAppearance());

        LinearLayout input=card();add(root,input,24);heading(input,"01  选择视频");
        sourceInfo=text("未选择视频",14,muted);add(input,sourceInfo,8);
        pick=button("选择视频",true);add(input,pick,14);pick.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("video/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,10);});
        settings=card();add(root,settings,16);heading(settings,"02  选择片段");
        videoFrame=new FrameLayout(this);LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,dp(230));vp.topMargin=dp(16);settings.addView(videoFrame,vp);videoFrame.setVisibility(View.GONE);
        video=new RoundedVideoView(this);videoFrame.addView(video,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));video.setVisibility(View.GONE);
        TextView previewMessage=text("正在载入预览…",14,muted);previewMessage.setGravity(Gravity.CENTER);previewMessage.setBackground(background(soft,8));videoFrame.addView(previewMessage,new FrameLayout.LayoutParams(-1,-1));
        video.setPreviewStateListener(message->{previewMessage.setText(message);previewMessage.setVisibility(message.isEmpty()?View.GONE:View.VISIBLE);});
        video.setOnErrorListener((mp,what,extra)->{pauseSelection();status.setText("预览暂时失败，请再次点击播放重试，或重新选择视频。");return true;});
        video.setOnPlaybackStarted(()->{if(previewing)preview.setText("暂停预览");});
        video.setOnCompletionListener(mp->{previewing=false;preview.setText("播放所选片段");});

        timeline=new RangeTimeline(this);add(settings,timeline,12);timeline.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(112)));
        selectedDuration=text("",13,muted);add(settings,selectedDuration,4);selectedDuration.setVisibility(View.GONE);
        LinearLayout times=row();add(settings,times,10);start=field("0.00");end=field("5.00");pair(times,labeled("开始时间 / 秒",start),labeled("结束时间 / 秒",end));
        timeline.setListener((a,b,first)->{adjustingTime=true;start.setText(time(a));end.setText(time(b));adjustingTime=false;start.setError(null);end.setError(null);selectedDuration.setVisibility(View.GONE);pauseSelection();video.seekTo((int)Math.round((first?a:b)*1000));});
        start.addTextChangedListener(timeWatcher(true));end.addTextChangedListener(timeWatcher(false));
        preview=button("播放所选片段",false);add(settings,preview,8);preview.setOnClickListener(v->playSelection());
        headingGap(settings,"03  导出设置");
        limit=field("5");add(settings,labeled("文件大小上限 / MB（可自定义）",limit),10);
        fps=new AnimatedSpinner(this);fps.setAdapter(adapter(fps,new String[]{"10 帧 / 秒","15 帧 / 秒","20 帧 / 秒","25 帧 / 秒","30 帧 / 秒"}));fps.setSelection(1);
        quality=new AnimatedSpinner(this);quality.setAdapter(adapter(quality,new String[]{"轻巧 · 64 色","均衡 · 128 色","清晰 · 256 色"}));quality.setSelection(2);
        android.content.SharedPreferences prefs=getPreferences(MODE_PRIVATE);
        limit.setText(prefs.getString("limit","5"));fps.setSelection(Math.max(0,Math.min(4,prefs.getInt("fps",1))));quality.setSelection(Math.max(0,Math.min(2,prefs.getInt("quality",2))));
        add(settings,labeled("帧率",fps),10);add(settings,labeled("颜色质量",quality),10);
        headingGap(settings,"04  尺寸与比例");
        customSize=ExpressiveControls.checkBox(this);customSize.setText("自定义分辨率");customSize.setTextColor(ink);add(settings,customSize,8);
        ratio=new AnimatedSpinner(this);ratio.setAdapter(adapter(ratio,new String[]{"保持原比例（默认）","1 : 1 · 方形","4 : 3 · 横向","16 : 9 · 横屏","9 : 16 · 竖屏","自由宽高"}));add(settings,labeled("画面比例",ratio),8);
        sizeFields=row();targetWidth=field("720");targetHeight=field("1280");targetWidth.setInputType(InputType.TYPE_CLASS_NUMBER);targetHeight.setInputType(InputType.TYPE_CLASS_NUMBER);pair(sizeFields,labeled("宽 / 像素",targetWidth),labeled("高 / 像素",targetHeight));add(settings,sizeFields,8);
        sizeInfo=text("",13,muted);add(settings,sizeInfo,8);
        framing=new AnimatedSpinner(this);framing.setAdapter(adapter(framing,new String[]{"居中裁剪 · 填满画面","完整保留 · 黑色留边","拉伸填满 · 可能变形"}));framingFields=labeled("画面如何适应新比例",framing);add(settings,framingFields,8);framingFields.setVisibility(View.GONE);
        allowShrink=ExpressiveControls.checkBox(this);allowShrink.setText("超出体积上限时，允许等比缩小");allowShrink.setTextColor(ink);allowShrink.setChecked(true);add(settings,allowShrink,8);
        customSize.setOnCheckedChangeListener((v,checked)->{if(!adjustingSize&&!checked&&ratio.getSelectedItemPosition()==5)ratio.setSelection(0);refreshSize();});
        allowShrink.setOnCheckedChangeListener((v,checked)->refreshSize());
        ratio.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> a){}public void onItemSelected(AdapterView<?> a,View v,int pos,long id){if(pos==5)customSize.setChecked(true);if(!adjustingSize)linkSize(true);refreshSize();}});
        targetWidth.addTextChangedListener(sizeWatcher(true));targetHeight.addTextChangedListener(sizeWatcher(false));
        refreshSize();
        convert=button("生成 GIF",true);add(root,convert,20);LinearLayout.LayoutParams mainButton=(LinearLayout.LayoutParams)convert.getLayoutParams();mainButton.setMarginStart(0);mainButton.setMarginEnd(0);convert.setLayoutParams(mainButton);convert.setOnClickListener(v->convert());
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);add(root,progress,10);progress.setVisibility(View.GONE);
        status=text("先选一段视频，即可开始。",13,muted);add(root,status,8);
        cancel=button("取消转换",false);add(root,cancel,8);cancel.setVisibility(View.GONE);cancel.setOnClickListener(v->{startService(new Intent(this,ExportService.class).setAction(ExportService.CANCEL));cancel.setEnabled(false);status.setText("正在取消…");});

        resultCard=card();add(root,resultCard,18);heading(resultCard,"生成完成");resultInfo=text("",14,muted);add(resultCard,resultInfo,8);
        gif=new ImageView(this);gif.setAdjustViewBounds(true);gif.setMaxHeight(dp(360));gif.setContentDescription("生成的 GIF 预览");add(resultCard,gif,12);
        save=button("保存到相册",true);share=button("分享 GIF",false);add(resultCard,save,12);add(resultCard,share,6);save.setOnClickListener(v->saveResult());share.setOnClickListener(v->shareResult());resultCard.setVisibility(View.GONE);
        enabled(settings,false);settings.setVisibility(View.GONE);convert.setVisibility(View.GONE);convert.setEnabled(false);
        boolean sameProcess=state!=null&&PROCESS_TOKEN.equals(state.getString("processToken"));
        boolean fromNotification=ExportService.OPEN.equals(getIntent().getAction());
        boolean running=ExportService.alive&&ExportService.state(this).getBoolean("running",false);
        if(UiPolicy.restoreSession(sameProcess,running,fromNotification))restoreForm();
        else seenJob=ExportService.state(this).getString("job","");
        if(sameProcess&&!state.getBoolean("displayResult",false))seenJob=state.getString("seenJob",seenJob);
        if(sameProcess){pendingExport=state.getParcelable("pendingExport");pendingJob=state.getString("pendingJob","");pendingSince=state.getLong("pendingSince",0);}
        syncExport();
    }
    private void showAppearance(){
        if(appearanceDialog!=null&&appearanceDialog.isShowing())return;
        appearanceContent=card();LinearLayout panelHeader=row();panelHeader.setGravity(Gravity.CENTER_VERTICAL);appearanceContent.addView(panelHeader);
        TextView panelTitle=text("设置",20,ink);panelTitle.setTypeface(null,1);panelHeader.addView(panelTitle,new LinearLayout.LayoutParams(0,-2,1));
        Button close=button("×",false);close.setTextSize(24);close.setPadding(0,0,0,0);close.setContentDescription("关闭设置");panelHeader.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));
        Switch toggle=ExpressiveControls.toggle(this);toggle.setText("跟随系统配色");toggle.setTextColor(ink);toggle.setChecked(AppPalette.enabled(this));add(appearanceContent,toggle,16);
        Button notifications=button("通知设置",false);add(appearanceContent,notifications,20);notifications.setOnClickListener(v->startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName())));
        appearanceDialog=new Dialog(this);appearanceDialog.setContentView(appearanceContent);appearanceDialog.setCanceledOnTouchOutside(true);
        Window w=appearanceDialog.getWindow();w.setBackgroundDrawable(background(surface,20));w.setGravity(Gravity.CENTER);w.setLayout(getResources().getDisplayMetrics().widthPixels-dp(48),WindowManager.LayoutParams.WRAP_CONTENT);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams attributes=w.getAttributes();attributes.dimAmount=.32f;w.setAttributes(attributes);w.setWindowAnimations(R.style.SettingsAnimation);
        close.setOnClickListener(v->appearanceDialog.dismiss());
        toggle.setOnCheckedChangeListener((v,checked)->{getSharedPreferences("appearance",MODE_PRIVATE).edit().putBoolean("dynamicColor",checked).apply();handler.postDelayed(()->{if(!isDestroyed()){applyPalette();}},240);});
        appearanceDialog.show();
    }
    private int mapped(int color,int[] before,int[] after){for(int i=0;i<before.length;i++)if(color==before[i])return after[i];return color;}
    private void tintBackground(Drawable d,int[] before,int[] after){
        if(d instanceof GradientDrawable){android.content.res.ColorStateList c=((GradientDrawable)d).getColor();if(c!=null)((GradientDrawable)d).setColor(mapped(c.getDefaultColor(),before,after));}
        if(d instanceof StateListDrawable){StateListDrawable states=(StateListDrawable)d;for(int i=0;i<states.getStateCount();i++)tintBackground(states.getStateDrawable(i),before,after);}
        if(d instanceof LayerDrawable){LayerDrawable layers=(LayerDrawable)d;for(int i=0;i<layers.getNumberOfLayers();i++)tintBackground(layers.getDrawable(i),before,after);}
        if(d instanceof RippleDrawable)((RippleDrawable)d).setColor(android.content.res.ColorStateList.valueOf((green&0xffffff)|0x30000000));
    }
    private void recolor(View v,int[] before,int[] after){
        tintBackground(v.getBackground(),before,after);
        if(v instanceof TextView){TextView t=(TextView)v;t.setTextColor(mapped(t.getCurrentTextColor(),before,after));}
        if(v instanceof Button&&v.getTag() instanceof Boolean){boolean primary=(Boolean)v.getTag();((Button)v).setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{muted,primary?onAccent:green}));}
        if(v instanceof CompoundButton)ExpressiveControls.recolor((CompoundButton)v);
        if(v instanceof Spinner){v.setBackground(new SelectBackground(soft,ink,green,getResources().getDisplayMetrics().density));SpinnerAdapter a=((Spinner)v).getAdapter();if(a instanceof BaseAdapter)((BaseAdapter)a).notifyDataSetChanged();}
        if(v instanceof RangeTimeline)((RangeTimeline)v).refreshPalette();
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)recolor(g.getChildAt(i),before,after);}
    }
    private void applyPalette(){
        AppPalette p=AppPalette.read(this);if(p.signature==paletteSignature)return;
        int[] before={green,onAccent,ink,muted,soft,surface,page};
        ink=p.ink;muted=p.muted;green=p.accent;page=p.page;surface=p.surface;onAccent=p.onAccent;soft=p.soft;paletteSignature=p.signature;
        int[] after={green,onAccent,ink,muted,soft,surface,page};
        recolor(root,before,after);scroll.setBackgroundColor(page);getWindow().setNavigationBarColor(page);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(green));
        if(appearanceDialog!=null&&appearanceDialog.isShowing()){recolor(appearanceContent,before,after);appearanceDialog.getWindow().setBackgroundDrawable(background(surface,20));}
        // A subtle fade on the settings surface; video and scroll position are untouched.
        if(android.animation.ValueAnimator.areAnimatorsEnabled()){root.setAlpha(.9f);root.animate().alpha(1).setDuration(220).start();}
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);return t;}
    private GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(20),dp(22),dp(20),dp(22));l.setBackground(background(surface,20));return l;}
    private void heading(LinearLayout l,String s){TextView t=text(s,18,ink);t.setTypeface(null,1);l.addView(t);}
    private void headingGap(LinearLayout l,String s){TextView t=text(s,18,ink);t.setTypeface(null,1);add(l,t,24);}
    private void add(LinearLayout parent,View v,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);if(parent==root&&v instanceof Button){p.setMarginStart(dp(20));p.setMarginEnd(dp(20));}parent.addView(v,p);}
    private Button button(String label,boolean primary){
        Button b=new Button(this);b.setTag(Boolean.valueOf(primary));b.setText(label);b.setTextSize(16);b.setTypeface(Typeface.create("sans-serif-medium",0));b.setAllCaps(false);b.setPadding(dp(24),dp(12),dp(24),dp(12));
        b.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{muted,primary?onAccent:green}));
        StateListDrawable fill=new StateListDrawable();fill.addState(new int[]{-android.R.attr.state_enabled},background(soft,primary?18:14));fill.addState(new int[]{},background(primary?green:soft,primary?18:14));
        b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf((green&0x00ffffff)|0x30000000),fill,null));b.setMinHeight(dp(56));b.setMinimumHeight(dp(56));b.setStateListAnimator(null);b.setElevation(0);b.setTranslationZ(0);b.setOutlineProvider(null);b.setBackgroundTintList(null);return b;
    }
    private EditText field(String value){EditText e=new EditText(this);e.setText(value);e.setSingleLine(true);e.setTextColor(ink);e.setTextSize(18);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);e.setPadding(dp(16),dp(10),dp(16),dp(10));e.setBackground(background(soft,10));return e;}
    private void pair(LinearLayout row,View first,View second){
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1);left.setMarginEnd(dp(6));
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,-2,1);right.setMarginStart(dp(6));row.addView(first,left);row.addView(second,right);
    }
    private LinearLayout labeled(String label,View child){
        if(child instanceof Spinner){((Spinner)child).setPopupBackgroundDrawable(background(surface,12));child.setBackground(new SelectBackground(soft,ink,green,getResources().getDisplayMetrics().density));child.setBackgroundTintList(null);child.setStateListAnimator(null);child.setElevation(0);}
        LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.addView(text(label,13,muted));LinearLayout.LayoutParams inputParams=new LinearLayout.LayoutParams(-1,dp(56));inputParams.topMargin=dp(6);l.addView(child,inputParams);child.setContentDescription(label);return l;}
    private ArrayAdapter<String> adapter(Spinner owner,String[] values){
        ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,values){
            @Override public View getView(int position,View reuse,ViewGroup parent){TextView v=(TextView)super.getView(position,reuse,parent);v.setTextColor(ink);v.setTextSize(15);v.setPadding(dp(16),dp(12),0,dp(12));v.setBackgroundColor(Color.TRANSPARENT);v.setGravity(Gravity.CENTER_VERTICAL);v.setSingleLine(true);v.setEllipsize(android.text.TextUtils.TruncateAt.END);return v;}
            @Override public View getDropDownView(int position,View reuse,ViewGroup parent){CheckedTextView v=reuse instanceof CheckedTextView?(CheckedTextView)reuse:new CheckedTextView(MainActivity.this);boolean selected=position==owner.getSelectedItemPosition();v.setText(values[position]);v.setTextColor(selected?green:ink);v.setTextSize(16);v.setGravity(Gravity.CENTER_VERTICAL);v.setMinHeight(dp(56));v.setPadding(dp(16),dp(12),dp(16),dp(12));v.setCheckMarkDrawable(ExpressiveControls.selectedMark(MainActivity.this,selected));v.setChecked(selected);v.setBackground(background(selected?soft:surface,10));return v;}
        };a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);return a;
    }
    private void enabled(View v,boolean on){v.setEnabled(on);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)enabled(g.getChildAt(i),on);}}
    private void setBusy(boolean on){busy=on;settings.setVisibility(source!=null?View.VISIBLE:View.GONE);convert.setVisibility(source!=null?View.VISIBLE:View.GONE);pick.setEnabled(!on);enabled(settings,!on&&source!=null);convert.setEnabled(!on&&source!=null);progress.setVisibility(on?View.VISIBLE:View.GONE);cancel.setVisibility(on?View.VISIBLE:View.GONE);cancel.setEnabled(on);refreshSize();}
    private String time(double seconds){return String.format(Locale.US,Math.abs(seconds*100-Math.round(seconds*100))>.0001?"%.3f":"%.2f",seconds);}
    private void pauseSelection(){previewing=false;handler.removeCallbacks(stopPreview);video.pause();if(preview!=null)preview.setText("播放所选片段");}
    private void updateTimes(boolean seek,boolean first){
        if(adjustingTime||source==null)return;
        try{double a=Double.parseDouble(start.getText().toString()),b=Double.parseDouble(end.getText().toString());
            if(!SelectionPolicy.valid(duration,a,b))throw new IllegalArgumentException();
            start.setError(null);end.setError(null);timeline.setRange(duration,a,b);selectedDuration.setVisibility(View.GONE);
            if(seek){pauseSelection();video.seekTo((int)Math.round((first?a:b)*1000));}
        }catch(Exception invalid){if(seek)pauseSelection();selectedDuration.setVisibility(View.VISIBLE);selectedDuration.setText("请输入 0–"+time(duration)+" 秒内的有效片段，结束须晚于开始。");}
    }
    private TextWatcher timeWatcher(boolean first){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){updateTimes(true,first);}public void afterTextChanged(Editable e){}};}
    protected void onActivityResult(int req,int code,Intent data){super.onActivityResult(req,code,data);if(req==10&&code==RESULT_OK&&data!=null&&data.getData()!=null){Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}loadSource(u);}}
    private void loadSource(Uri uri){
        previewing=false;video.stopPlayback();loading=true;setBusy(true);status.setText("正在读取视频…");cancel.setVisibility(View.GONE);
        worker.execute(()->{try(MediaMetadataRetriever r=new MediaMetadataRetriever()){r.setDataSource(this,uri);String d=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);if(d==null)throw new IOException("未找到视频时长");double length=Double.parseDouble(d)/1000;Bitmap first=r.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST,480,480);if(first==null||length<=0)throw new IOException("无法读取视频画面");first.recycle();String w=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),h=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);String rotation=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);boolean rotated="90".equals(rotation)||"270".equals(rotation);int displayWidth=Integer.parseInt(rotated?h:w),displayHeight=Integer.parseInt(rotated?w:h);if(displayWidth<=0||displayHeight<=0)throw new IOException("视频尺寸无效");
            runOnUiThread(()->{if(isDestroyed())return;source=uri;duration=length;sourceWidth=displayWidth;sourceHeight=displayHeight;loading=false;resetSize();adjustingTime=true;start.setText("0.00");end.setText(time(Math.min(5,length)));adjustingTime=false;updateTimes(false,true);pick.setText("更换视频");sourceInfo.setText(String.format(Locale.CHINA,"视频时长 %.2f 秒 · %d × %d",length,sourceWidth,sourceHeight));videoFrame.setVisibility(View.VISIBLE);video.setVisibility(View.VISIBLE);video.setVideoURI(uri);resultCard.setVisibility(View.GONE);seenJob=ExportService.state(this).getString("job","");setBusy(false);status.setText("选择片段和大小上限，开始转换。");saveForm();});
        }catch(Exception e){runOnUiThread(()->{if(isDestroyed())return;loading=false;setBusy(false);status.setText("读取失败："+message(e));});}});
    }
    private String message(Exception e){return e.getMessage()==null?"请尝试其他视频":e.getMessage();}
    private double[] selection(){double a=Double.parseDouble(start.getText().toString()),b=Double.parseDouble(end.getText().toString());if(!SelectionPolicy.valid(duration,a,b))throw new IllegalArgumentException("请填写有效片段：结束时间须大于开始时间，且不超过视频时长。");return new double[]{a,Math.min(b,duration)};}
    private void playSelection(){if(previewing){pauseSelection();return;}try{double[] s=selection();handler.removeCallbacks(stopPreview);previewEnd=s[1];previewing=true;preview.setText("正在准备预览…");video.playFrom((int)Math.round(s[0]*1000));handler.post(stopPreview);}catch(Exception e){status.setText("请检查开始和结束时间。");}}
    private void convert(){
        try{
            if(ExportService.state(this).getBoolean("running",false))return;
            double[] clip=selection();double mb=Double.parseDouble(limit.getText().toString());
            if(!Double.isFinite(mb)||mb<.1||mb>100)throw new IllegalArgumentException("文件上限请填写 0.1 到 100 MB。");
            int rate=new int[]{10,15,20,25,30}[fps.getSelectedItemPosition()],colors=new int[]{64,128,256}[quality.getSelectedItemPosition()];
            if(ExportPolicy.frameCount(clip[1]-clip[0],rate)>18000)throw new IllegalArgumentException("片段过长，请缩短片段或降低帧率（最多 18,000 帧）。");
            int[] size=selectedSize();ExportPolicy.validateDimensions(size[0],size[1]);
            saveForm();video.pause();previewing=false;preview.setText("播放所选片段");
            Intent task=new Intent(this,ExportService.class).setData(source).putExtra("job",Long.toString(System.currentTimeMillis())).putExtra("start",clip[0]).putExtra("end",clip[1]).putExtra("fps",rate).putExtra("colors",colors).putExtra("budget",(long)(mb*1000000)).putExtra("width",size[0]).putExtra("height",size[1]).putExtra("mode",framing.getSelectedItemPosition()).putExtra("shrink",allowShrink.isChecked());
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!getPreferences(MODE_PRIVATE).getBoolean("askedNotifications",false)){
                pendingExport=task;getPreferences(MODE_PRIVATE).edit().putBoolean("askedNotifications",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},20);
            }else launchExport(task);
        }catch(Exception e){status.setText(e instanceof NumberFormatException?"请填写有效的秒数、体积与整数宽高。":message(e));}
    }
    public void onRequestPermissionsResult(int req,String[] permissions,int[] results){super.onRequestPermissionsResult(req,permissions,results);if(req==20&&pendingExport!=null){Intent task=pendingExport;pendingExport=null;launchExport(task);}}
    private void launchExport(Intent task){
        try{pendingJob=task.getStringExtra("job");pendingSince=SystemClock.elapsedRealtime();stopGif();resultCard.setVisibility(View.GONE);setBusy(true);progress.setProgress(0);status.setText("正在启动后台转换…");startForegroundService(task);}
        catch(Exception e){pendingJob="";setBusy(false);status.setText("启动失败："+message(e));}
    }
    private void syncExport(){
        if(isDestroyed()||loading||saving)return;
        SharedPreferences st=ExportService.state(this);String job=st.getString("job","");
        if(!pendingJob.isEmpty()&&!pendingJob.equals(job)){
            if(SystemClock.elapsedRealtime()-pendingSince<10000)return;
            pendingJob="";setBusy(false);status.setText("后台任务未能启动，请重新尝试。");return;
        }
        if(!pendingJob.isEmpty())pendingJob="";
        if(st.getBoolean("running",false)){
            setBusy(true);resultCard.setVisibility(View.GONE);progress.setProgress(st.getInt("progress",0));
            String hint=getSystemService(NotificationManager.class).areNotificationsEnabled()?"可以切换到其他应用或锁屏，通知栏会显示本轮进度。":"通知未允许，后台转换仍在进行；可在系统设置中允许通知。";
            status.setText(st.getString("message","")+"\n"+hint);return;
        }
        if(job.isEmpty()||job.equals(seenJob))return;
        seenJob=job;setBusy(false);status.setText(st.getString("message",""));
        if("done".equals(st.getString("status",""))){
            String name=st.getString("file","");File file=new File(getCacheDir(),name);
            if(name.matches("gif-[0-9]+\\.gif")&&file.isFile())showResult(file,st.getInt("width",0),st.getInt("height",0),st.getInt("fps",15),st.getInt("colors",256),st.getLong("durationMs",0)/1000.0);
            else status.setText("结果缓存已被清理，请重新生成。");
        }
    }
    private int[] selectedSize(){
        if(customSize.isChecked())return new int[]{Integer.parseInt(targetWidth.getText().toString()),Integer.parseInt(targetHeight.getText().toString())};
        return ExportPolicy.fitRatio(ExportPolicy.ratio(ratio.getSelectedItemPosition(),sourceWidth,sourceHeight),Math.min(1280,Math.max(sourceWidth,sourceHeight)));
    }
    private void refreshSize(){
        if(customSize==null||sizeFields==null)return;
        sizeFields.setVisibility(customSize.isChecked()?View.VISIBLE:View.GONE);
        enabled(sizeFields,!busy&&source!=null&&customSize.isChecked());
        if(framingFields!=null){boolean changed=false;try{int[] size=selectedSize();changed=source!=null&&UiPolicy.changesRatio(sourceWidth,sourceHeight,size[0],size[1]);}catch(Exception ignored){}
            framingFields.setVisibility(changed?View.VISIBLE:View.GONE);
        }
        if(sizeInfo!=null)try{int[] d=selectedSize();sizeInfo.setText((customSize.isChecked()?"自定义":"自动")+" · "+d[0]+" × "+d[1]+(allowShrink!=null&&allowShrink.isChecked()?"（超限可等比缩小）":""));}catch(Exception e){sizeInfo.setText("填写宽高；原比例或预设比例下，另一边会联动。");}
    }
    private void linkSize(boolean widthEdited){
        if(adjustingSize||ratio==null||ratio.getSelectedItemPosition()==5)return;
        adjustingSize=true;
        try{double r=ExportPolicy.ratio(ratio.getSelectedItemPosition(),sourceWidth,sourceHeight);if(widthEdited){int w=Integer.parseInt(targetWidth.getText().toString());targetHeight.setText(Integer.toString(Math.max(1,(int)Math.round(w/r))));}else{int h=Integer.parseInt(targetHeight.getText().toString());targetWidth.setText(Integer.toString(Math.max(1,(int)Math.round(h*r))));}}catch(Exception ignored){}finally{adjustingSize=false;}
    }
    private TextWatcher sizeWatcher(boolean width){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){if(!adjustingSize){linkSize(width);refreshSize();}}public void afterTextChanged(Editable e){}};}
    private void resetSize(){int[] d=ExportPolicy.dimensions(sourceWidth,sourceHeight,1280);adjustingSize=true;ratio.setSelection(0);customSize.setChecked(false);targetWidth.setText(Integer.toString(d[0]));targetHeight.setText(Integer.toString(d[1]));adjustingSize=false;linkSize(true);refreshSize();}
    private void saveForm(){
        if(start==null)return;
        getPreferences(MODE_PRIVATE).edit().putString("limit",limit.getText().toString()).putInt("fps",fps.getSelectedItemPosition()).putInt("quality",quality.getSelectedItemPosition()).putString("source",source==null?"":source.toString()).putLong("duration",Math.round(duration*1000)).putInt("sourceWidth",sourceWidth).putInt("sourceHeight",sourceHeight).putString("start",start.getText().toString()).putString("end",end.getText().toString()).putBoolean("customSize",customSize.isChecked()).putInt("ratio",ratio.getSelectedItemPosition()).putInt("framing",framing.getSelectedItemPosition()).putString("targetWidth",targetWidth.getText().toString()).putString("targetHeight",targetHeight.getText().toString()).putBoolean("allowShrink",allowShrink.isChecked()).apply();
    }
    private void restoreForm(){
        SharedPreferences p=getPreferences(MODE_PRIVATE);String uri=p.getString("source","");
        if(!uri.isEmpty()){
            source=Uri.parse(uri);duration=p.getLong("duration",0)/1000.0;sourceWidth=p.getInt("sourceWidth",720);sourceHeight=p.getInt("sourceHeight",1280);
            adjustingTime=true;start.setText(p.getString("start","0.00"));end.setText(p.getString("end","5.00"));adjustingTime=false;updateTimes(false,true);pick.setText("更换视频");
            sourceInfo.setText(String.format(Locale.CHINA,"视频时长 %.2f 秒 · %d × %d",duration,sourceWidth,sourceHeight));videoFrame.setVisibility(View.VISIBLE);video.setVisibility(View.VISIBLE);video.setVideoURI(source);video.seekTo((int)Math.round(parseStart()*1000));

            setBusy(false);
        }
        adjustingSize=true;ratio.setSelection(Math.max(0,Math.min(5,p.getInt("ratio",0))));customSize.setChecked(p.getBoolean("customSize",false));targetWidth.setText(p.getString("targetWidth","720"));targetHeight.setText(p.getString("targetHeight","1280"));framing.setSelection(Math.max(0,Math.min(2,p.getInt("framing",0))));allowShrink.setChecked(p.getBoolean("allowShrink",true));adjustingSize=false;refreshSize();
    }
    private double parseStart(){try{return Math.max(0,Double.parseDouble(start.getText().toString()));}catch(Exception e){return 0;}}
    private void showResult(File output,int w,int h,int rate,int colors,double seconds){if(isDestroyed())return;result=output;setBusy(false);resultCard.setVisibility(View.VISIBLE);save.setEnabled(true);save.setText("保存到相册");resultInfo.setText(String.format(Locale.CHINA,"%.2f MB · %d × %d\n%.2f 秒 · %d 帧/秒 · %d 色",output.length()/1_000_000.0,w,h,seconds,rate,colors));status.setText("已生成，实际文件大小在设定上限内。");try{Drawable d=ImageDecoder.decodeDrawable(ImageDecoder.createSource(output));gif.setImageDrawable(d);if(d instanceof AnimatedImageDrawable)((AnimatedImageDrawable)d).start();}catch(Exception e){status.setText("已生成，但预览失败；可以保存后在相册查看。");}}
    private void saveResult(){
        if(result==null||saving)return;
        File chosen=result;saving=true;pick.setEnabled(false);convert.setEnabled(false);save.setEnabled(false);save.setText("正在保存…");
        worker.execute(()->{
            Uri dest=null;
            try{
                ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,chosen.getName());v.put(MediaStore.Images.Media.MIME_TYPE,"image/gif");v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/GifLite");v.put(MediaStore.Images.Media.IS_PENDING,1);
                dest=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(dest==null)throw new IOException("无法创建相册文件");
                try(InputStream in=new FileInputStream(chosen);OutputStream out=getContentResolver().openOutputStream(dest)){
                    if(out==null)throw new IOException("无法写入相册");byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                }
                v.clear();v.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(dest,v,null,null);
                runOnUiThread(()->{saving=false;if(isDestroyed())return;setBusy(false);save.setText("已保存到相册");status.setText("已保存至 Pictures/GifLite。");});
            }catch(Exception e){
                if(dest!=null)try{getContentResolver().delete(dest,null,null);}catch(Exception ignored){}
                runOnUiThread(()->{saving=false;if(isDestroyed())return;setBusy(false);save.setEnabled(true);save.setText("重试保存");status.setText("保存失败："+message(e));});
            }
        });
    }
    private void shareResult(){if(result==null)return;Uri u=Uri.parse("content://com.izumikonata.giflite.result/"+result.getName());Intent i=new Intent(Intent.ACTION_SEND);i.setType("image/gif");i.putExtra(Intent.EXTRA_STREAM,u);i.setClipData(ClipData.newUri(getContentResolver(),"GIF",u));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivity(Intent.createChooser(i,"分享 GIF"));}catch(ActivityNotFoundException e){status.setText("没有可用的分享应用，请先保存到相册。");}}
    private void stopGif(){Drawable d=gif.getDrawable();if(d instanceof AnimatedImageDrawable)((AnimatedImageDrawable)d).stop();gif.setImageDrawable(null);}
    protected void onSaveInstanceState(Bundle b){saveForm();b.putString("processToken",PROCESS_TOKEN);b.putBoolean("displayResult",resultCard.getVisibility()==View.VISIBLE);b.putString("seenJob",seenJob);b.putParcelable("pendingExport",pendingExport);b.putString("pendingJob",pendingJob);b.putLong("pendingSince",pendingSince);super.onSaveInstanceState(b);}
    protected void onPause(){saveForm();handler.removeCallbacks(poll);super.onPause();pauseSelection();Drawable d=gif.getDrawable();if(d instanceof AnimatedImageDrawable)((AnimatedImageDrawable)d).stop();}
    protected void onStop(){video.suspendPreview();super.onStop();}
    protected void onResume(){super.onResume();video.resumePreview();if(AppPalette.read(this).signature!=paletteSignature)applyPalette();if(gif!=null&&gif.getDrawable() instanceof AnimatedImageDrawable)((AnimatedImageDrawable)gif.getDrawable()).start();handler.removeCallbacks(poll);handler.post(poll);}
    protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);if(ExportService.OPEN.equals(i.getAction())){seenJob="";syncExport();}}
    protected void onDestroy(){if(appearanceDialog!=null)appearanceDialog.dismiss();handler.removeCallbacksAndMessages(null);video.stopPlayback();stopGif();worker.shutdown();super.onDestroy();}
}
