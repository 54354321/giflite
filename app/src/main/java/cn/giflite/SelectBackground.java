package cn.giflite;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** One continuous field surface, including the trailing dropdown affordance. */
public final class SelectBackground extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int fill,ink,accent;private int alpha=255;private final float density;
    public SelectBackground(int fill,int ink,int accent,float density){this.fill=fill;this.ink=ink;this.accent=accent;this.density=density;}
    public void draw(Canvas canvas){
        Rect b=getBounds();float radius=12*density;boolean enabled=false,focused=false;for(int s:getState()){enabled|=s==android.R.attr.state_enabled;focused|=s==android.R.attr.state_focused||s==android.R.attr.state_pressed;}
        paint.setStyle(Paint.Style.FILL);paint.setColor(fill);paint.setAlpha(enabled?alpha:Math.round(alpha*.38f));canvas.drawRoundRect(new RectF(b),radius,radius,paint);
        if(focused){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*density);paint.setColor(accent);RectF outline=new RectF(b);outline.inset(density,density);canvas.drawRoundRect(outline,radius,radius,paint);}
        float x=b.right-24*density,y=b.exactCenterY();
        paint.setColor(ink);paint.setAlpha(enabled?alpha:Math.round(alpha*.38f));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*density);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        Path arrow=new Path();arrow.moveTo(x-4*density,y-2*density);arrow.lineTo(x,y+2*density);arrow.lineTo(x+4*density,y-2*density);canvas.drawPath(arrow,paint);
    }
    public boolean getPadding(Rect p){p.set(0,0,(int)(44*density),0);return true;}
    public boolean isStateful(){return true;}
    protected boolean onStateChange(int[] state){invalidateSelf();return true;}
    public void setAlpha(int alpha){this.alpha=alpha;invalidateSelf();}
    public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}
    public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
