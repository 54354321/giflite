package com.izumikonata.giflite;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.widget.CheckBox;
import android.widget.Switch;
import android.widget.CompoundButton;
import android.animation.ValueAnimator;

/** Native controls retain semantics, focus, keyboard and Switch thumb animation. */
public final class ExpressiveControls {
    private ExpressiveControls(){}
    private static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    public static CheckBox checkBox(Context c){
        CheckBox v=new CheckBox(c){
            @Override public int getCompoundPaddingLeft(){return super.getCompoundPaddingLeft()+(getLayoutDirection()==android.view.View.LAYOUT_DIRECTION_LTR?dp(c,12):0);}
            @Override public int getCompoundPaddingRight(){return super.getCompoundPaddingRight()+(getLayoutDirection()==android.view.View.LAYOUT_DIRECTION_RTL?dp(c,12):0);}
        };v.setButtonTintList(null);v.setButtonDrawable(new Glyph(c,0));
        v.setMinHeight(dp(c,48));v.setTextSize(16);v.setPadding(dp(c,4),0,dp(c,4),0);v.setCompoundDrawablePadding(dp(c,12));return v;
    }
    public static Switch toggle(Context c){
        Switch v=new Switch(c);v.setShowText(false);v.setSplitTrack(false);v.setSwitchMinWidth(dp(c,52));v.setSwitchPadding(dp(c,16));
        v.setThumbTintList(null);v.setTrackTintList(null);v.setTrackDrawable(new Glyph(c,1));v.setThumbDrawable(new Glyph(c,2));
        v.setMinHeight(dp(c,48));v.setTextSize(16);return v;
    }
    public static Drawable selectedMark(Context c,boolean checked){Glyph g=new Glyph(c,3);g.setState(checked?new int[]{android.R.attr.state_enabled,android.R.attr.state_checked}:new int[]{android.R.attr.state_enabled});return g;}
    public static void recolor(CompoundButton v){
        if(v instanceof Switch){Drawable a=((Switch)v).getThumbDrawable(),b=((Switch)v).getTrackDrawable();if(a instanceof Glyph)((Glyph)a).refresh();if(b instanceof Glyph)((Glyph)b).refresh();}
        else if(v.getButtonDrawable() instanceof Glyph)((Glyph)v.getButtonDrawable()).refresh();
    }
    private static final class Glyph extends Drawable {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private AppPalette colors;private final Context context;private float progress;private boolean initialized;private ValueAnimator animation;private final float d;private final int kind;private int alpha=255;
        Glyph(Context c,int kind){context=c;colors=AppPalette.read(c);d=c.getResources().getDisplayMetrics().density;this.kind=kind;}
        public int getIntrinsicWidth(){return Math.round((kind==1?52:24)*d);}
        public int getIntrinsicHeight(){return Math.round((kind==1?32:24)*d);}
        public boolean isStateful(){return true;}
        protected boolean onStateChange(int[] states){
            float target=has(android.R.attr.state_checked)?1:0;
            if(animation!=null)animation.cancel();
            if(kind==0&&initialized&&getCallback()!=null&&ValueAnimator.areAnimatorsEnabled()&&progress!=target){animation=ValueAnimator.ofFloat(progress,target);animation.setDuration(220);animation.setInterpolator(new android.view.animation.PathInterpolator(.4f,0,.2f,1));animation.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidateSelf();});animation.start();}
            else progress=target;initialized=true;invalidateSelf();return true;
        }
        void refresh(){colors=AppPalette.read(context);invalidateSelf();}
        public boolean getPadding(Rect out){if(kind==1){out.set(Math.round(4*d),0,Math.round(4*d),0);return true;}return super.getPadding(out);}
        public void jumpToCurrentState(){if(animation!=null)animation.cancel();progress=has(android.R.attr.state_checked)?1:0;invalidateSelf();}
        private boolean has(int state){for(int s:getState())if(s==state)return true;return false;}
        private void color(int color,boolean enabled){p.setColor(color);p.setAlpha(enabled?alpha:Math.round(alpha*.38f));p.setStyle(Paint.Style.FILL);}
        public void draw(Canvas c){
            Rect b=getBounds();float x=kind==0?b.left+12*d:b.exactCenterX(),y=b.exactCenterY();boolean checked=has(android.R.attr.state_checked),enabled=has(android.R.attr.state_enabled);
            if(kind==1){
                RectF box=new RectF(b.left+d,y-15*d,b.right-d,y+15*d);color(checked?colors.accent:colors.soft,enabled);c.drawRoundRect(box,16*d,16*d,p);
                if(!checked){color(colors.muted,enabled);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2*d);c.drawRoundRect(box,16*d,16*d,p);}return;
            }
            if(kind==2){color(checked?colors.onAccent:colors.muted,enabled);c.drawCircle(x,y,(checked?12:8)*d,p);if(checked)tick(c,x,y,colors.accent,enabled,4);return;}
            if(kind==3){if(checked)tick(c,x,y,colors.accent,enabled,6);return;}
            c.save();float scale=1+.1f*(float)Math.sin(Math.PI*progress);c.scale(scale,scale,x,y);
            RectF box=new RectF(x-10*d,y-10*d,x+10*d,y+10*d);color(colors.muted,enabled);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2*d);c.drawRoundRect(box,3*d,3*d,p);
            if(progress>0){color(colors.accent,enabled);p.setAlpha(Math.round(p.getAlpha()*progress));c.drawRoundRect(box,3*d,3*d,p);c.save();c.clipRect(x-7*d,y-8*d,x-7*d+14*d*progress,y+8*d);tick(c,x,y,colors.onAccent,enabled,5);c.restore();}c.restore();
        }
        private void tick(Canvas c,float x,float y,int color,boolean enabled,float size){color(color,enabled);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2*d);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);Path path=new Path();path.moveTo(x-size*d,y);path.lineTo(x-d,y+size*.65f*d);path.lineTo(x+size*d,y-size*.65f*d);c.drawPath(path,p);}
        public void setAlpha(int a){alpha=a;invalidateSelf();}public void setColorFilter(ColorFilter f){p.setColorFilter(f);invalidateSelf();}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
