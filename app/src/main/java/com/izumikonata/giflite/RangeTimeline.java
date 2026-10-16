package com.izumikonata.giflite;

import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.*;
import java.util.Locale;

/** A single two-handle timeline; virtual SeekBar nodes expose both ends to TalkBack. */
public final class RangeTimeline extends View {
    public interface Listener {void changed(double start,double end,boolean first);}
    private Listener listener;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private AppPalette colors;
    public void refreshPalette(){colors=AppPalette.read(getContext());invalidate();}
    private double duration=5,start=0,end=5;
    private boolean dragging,first=true,atBoundary;
    private float downX,downY;
    private boolean ambiguous,relative;
    private double downStart,downEnd;
    private long lastHaptic;
    private int lastTick,accessibilityFocus=-1,hover=-1;
    public RangeTimeline(Context c){super(c);colors=AppPalette.read(c);setFocusable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);setHapticFeedbackEnabled(true);setLayoutDirection(LAYOUT_DIRECTION_LTR);}
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    private float left(){return dp(28);}
    private float right(){return Math.max(left()+1,getWidth()-dp(28));}
    private float x(double seconds){return left()+(right()-left())*(float)(seconds/duration);}
    public void setListener(Listener value){listener=value;}
    public void setRange(double total,double a,double b){if(!SelectionPolicy.valid(total,a,b))return;duration=total;start=a;end=b;invalidate();}
    private String value(double n){return String.format(Locale.US,"%.2f",n);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float y=dp(59),a=x(start),b=x(end);
        paint.setTextSize(dp(13));paint.setColor(colors.muted);paint.setAlpha(isEnabled()?255:100);
        c.drawText("0:00",left(),dp(18),paint);String total=value(duration)+" s";c.drawText(total,right()-paint.measureText(total),dp(18),paint);
        String selected="选中 "+value(end-start)+" 秒";float room=(right()-left())-paint.measureText("0:00")-paint.measureText(total)-dp(20);float normal=paint.getTextSize();if(paint.measureText(selected)>room&&room>0)paint.setTextSize(Math.max(dp(10),normal*room/paint.measureText(selected)));paint.setColor(colors.accent);c.drawText(selected,(left()+right()+paint.measureText("0:00")-paint.measureText(total)-paint.measureText(selected))/2,dp(18),paint);paint.setTextSize(normal);
        paint.setColor(colors.soft);c.drawRoundRect(left(),y-dp(7),right(),y+dp(7),dp(5),dp(5),paint);
        paint.setColor(colors.accent);c.drawRect(a,y-dp(7),b,y+dp(7),paint);
        handle(c,a,y,0);handle(c,b,y,1);
        paint.setTextSize(dp(13));paint.setColor(colors.accent);paint.setAlpha(isEnabled()?255:100);
        String av=value(start),bv=value(end);float aw=paint.measureText(av),bw=paint.measureText(bv);
        float ax=Math.max(0,a-aw/2),bx=Math.min(getWidth()-bw,b-bw/2);
        if(bx<ax+aw+dp(12)){ax=Math.max(0,(a+b-aw-bw-dp(12))/2);bx=Math.min(getWidth()-bw,ax+aw+dp(12));}
        c.drawText(av,ax,dp(104),paint);c.drawText(bv,bx,dp(104),paint);
    }
    private void handle(Canvas c,float x,float y,int id){
        // Bodies extend OUTSIDE the interval: close endpoints remain two separate handles.
        float a=id==0?x-dp(12):x+dp(1),b=id==0?x-dp(1):x+dp(12),center=(a+b)/2;
        paint.setColor(colors.accent);paint.setAlpha(isEnabled()?255:100);
        c.drawRoundRect(a,y-dp(18),b,y+dp(18),dp(4),dp(4),paint);
        paint.setColor(colors.onAccent);c.drawRoundRect(center-dp(1),y-dp(6),center+dp(1),y+dp(6),dp(1),dp(1),paint);
        if((dragging&&(first==(id==0)))||(isFocused()&&(first==(id==0)))||accessibilityFocus==id){paint.setColor(colors.accent);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));c.drawRoundRect(a-dp(3),y-dp(21),b+dp(3),y+dp(21),dp(6),dp(6),paint);paint.setStyle(Paint.Style.FILL);}
    }
    private void move(double requested,boolean haptic){
        double old=first?start:end;
        double result=SelectionPolicy.move(duration,start,end,first,requested);
        if(Math.abs(old-result)<.000001)return;
        if(first)start=result;else end=result;
        if(haptic){boolean boundary=SelectionPolicy.boundary(duration,start,end,first);long now=SystemClock.elapsedRealtime();int tick=SelectionPolicy.tick(result);
            int feedback=SelectionPolicy.feedback(atBoundary,boundary,lastTick,tick,now-lastHaptic);
            if(feedback!=0){performHapticFeedback(feedback==2?HapticFeedbackConstants.CONTEXT_CLICK:HapticFeedbackConstants.CLOCK_TICK);lastHaptic=now;}
            atBoundary=boundary;lastTick=tick;
        }
        invalidate();if(listener!=null)listener.changed(start,end,first);
    }
    private void moveAt(float px){double t=relative?(first?downStart:downEnd)+(px-downX)/(right()-left())*duration:Math.max(0,Math.min(1,(px-left())/(right()-left())))*duration;move(Math.min(duration,Math.round(t*100)/100.0),true);}
    @Override public boolean onTouchEvent(MotionEvent e){
        if(!isEnabled())return false;
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                if(e.getY()<dp(28)||e.getY()>dp(88))return false;
                downX=e.getX();downY=e.getY();dragging=true;float a=x(start),b=x(end);
                first=downX<=(a+b)/2;downStart=start;downEnd=end;
                ambiguous=TimelineTouchPolicy.ambiguous(a,b,downX,dp(48),dp(16));
                relative=ambiguous||Math.min(Math.abs(downX-(a-dp(11))),Math.abs(downX-(b+dp(11))))<=dp(28);
                lastTick=SelectionPolicy.tick(first?start:end);atBoundary=SelectionPolicy.boundary(duration,start,end,first);lastHaptic=SystemClock.elapsedRealtime();
                setPressed(true);getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;
            case MotionEvent.ACTION_MOVE:
                if(!dragging)return false;
                if(Math.abs(e.getY()-downY)>dp(12)&&Math.abs(e.getY()-downY)>Math.abs(e.getX()-downX)*1.5){finish();return true;}
                if(ambiguous){if(Math.abs(e.getX()-downX)<dp(4))return true;first=TimelineTouchPolicy.firstFromDirection(e.getX()-downX);ambiguous=false;lastTick=SelectionPolicy.tick(first?downStart:downEnd);atBoundary=SelectionPolicy.boundary(duration,downStart,downEnd,first);}
                moveAt(e.getX());return true;
            case MotionEvent.ACTION_UP:if(dragging){if(!ambiguous)moveAt(e.getX());finish();performClick();}return true;
            case MotionEvent.ACTION_CANCEL:finish();return true;
            default:return dragging;
        }
    }
    private void finish(){dragging=false;setPressed(false);getParent().requestDisallowInterceptTouchEvent(false);invalidate();sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onKeyDown(int code,KeyEvent event){
        if(!isEnabled())return super.onKeyDown(code,event);
        if(code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN){first=!first;invalidate();return true;}
        if(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT){move((first?start:end)+(code==KeyEvent.KEYCODE_DPAD_LEFT?-.1:.1),false);return true;}
        return super.onKeyDown(code,event);
    }
    private void event(int id,int type){AccessibilityEvent e=AccessibilityEvent.obtain(type);e.setPackageName(getContext().getPackageName());e.setClassName("android.widget.SeekBar");e.setSource(this,id);e.setEnabled(isEnabled());getParent().requestSendAccessibilityEvent(this,e);}
    @Override public boolean dispatchHoverEvent(MotionEvent e){
        AccessibilityManager manager=(AccessibilityManager)getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if(manager.isTouchExplorationEnabled()){
            int next=e.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT?-1:(e.getX()<=(x(start)+x(end))/2?0:1);
            if(next!=hover){if(hover!=-1)event(hover,AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);hover=next;if(hover!=-1)event(hover,AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);}return true;
        }return super.dispatchHoverEvent(e);
    }
    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider(){return new AccessibilityNodeProvider(){
        @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int id){
            if(id==View.NO_ID){AccessibilityNodeInfo n=AccessibilityNodeInfo.obtain(RangeTimeline.this);onInitializeAccessibilityNodeInfo(n);n.addChild(RangeTimeline.this,0);n.addChild(RangeTimeline.this,1);return n;}
            if(id!=0&&id!=1)return null;
            AccessibilityNodeInfo n=AccessibilityNodeInfo.obtain();n.setSource(RangeTimeline.this,id);n.setParent(RangeTimeline.this);n.setPackageName(getContext().getPackageName());n.setClassName("android.widget.SeekBar");n.setContentDescription((id==0?"开始时间 ":"结束时间 ")+value(id==0?start:end)+" 秒");n.setEnabled(isEnabled());n.setVisibleToUser(isShown());n.setFocusable(true);n.setAccessibilityFocused(accessibilityFocus==id);
            float split=(x(start)+x(end))/2;Rect bounds=new Rect(id==0?0:(int)split,0,id==0?(int)split:getWidth(),getHeight());n.setBoundsInParent(bounds);int[] location=new int[2];getLocationOnScreen(location);bounds.offset(location[0],location[1]);n.setBoundsInScreen(bounds);
            double gap=Math.min(.01,duration);n.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,(float)(id==0?0:start+gap),(float)(id==0?Math.max(0,end-gap):duration),(float)(id==0?start:end)));
            n.addAction(accessibilityFocus==id?AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS:AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
            if(isEnabled()){n.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);n.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);n.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);}return n;
        }
        @Override public boolean performAction(int id,int action,Bundle args){
            if(id!=0&&id!=1)return false;
            if(action==AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS){accessibilityFocus=id;first=id==0;invalidate();event(id,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);return true;}
            if(action==AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS){accessibilityFocus=-1;invalidate();event(id,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);return true;}
            if(!isEnabled())return false;first=id==0;
            if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()&&args!=null){move(args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE),false);}
            else if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){move((first?start:end)+(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?.1:-.1),false);}
            else return false;event(id,AccessibilityEvent.TYPE_VIEW_SELECTED);return true;
        }
    };}
}
