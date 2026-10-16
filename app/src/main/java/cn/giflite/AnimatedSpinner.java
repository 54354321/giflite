package cn.giflite;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Same Spinner selection model with an explicitly animated anchored menu. */
public final class AnimatedSpinner extends Spinner {
    private PopupWindow menu;
    public AnimatedSpinner(Context c){super(c,Spinner.MODE_DROPDOWN);}
    @Override public boolean onTouchEvent(MotionEvent e){
        if(!isEnabled())return false;
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:setPressed(true);return true;
            case MotionEvent.ACTION_MOVE:if(e.getX()<0||e.getX()>getWidth()||e.getY()<0||e.getY()>getHeight())setPressed(false);return true;
            case MotionEvent.ACTION_UP:boolean click=isPressed();setPressed(false);if(click)performClick();return true;
            case MotionEvent.ACTION_CANCEL:setPressed(false);return true;
            default:return true;
        }
    }
    @Override public boolean performClick(){
        if(!isEnabled())return false;if(menu!=null&&menu.isShowing())return true;
        SpinnerAdapter source=getAdapter();if(source==null||source.getCount()<2)return false;
        final int selected=getSelectedItemPosition();final int count=source.getCount()-1;
        ListView list=new ListView(getContext());list.setDivider(null);list.setPadding(0,dp(6),0,dp(6));
        list.setAdapter(new BaseAdapter(){private int index(int p){return p>=selected?p+1:p;}public int getCount(){return count;}public Object getItem(int p){return source.getItem(index(p));}public long getItemId(int p){return index(p);}public View getView(int p,View v,ViewGroup parent){return source.getDropDownView(index(p),v,parent);}});
        GradientDrawable bg=new GradientDrawable();bg.setColor(AppPalette.read(getContext()).surface);bg.setCornerRadius(dp(12));
        int contentHeight=list.getPaddingTop()+list.getPaddingBottom();
        int widthSpec=View.MeasureSpec.makeMeasureSpec(getWidth(),View.MeasureSpec.EXACTLY);
        for(int i=0;i<count;i++){View row=list.getAdapter().getView(i,null,list);row.measure(widthSpec,View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));contentHeight+=row.getMeasuredHeight();}
        menu=new PopupWindow(list,getWidth(),Math.min(dp(348),contentHeight),true);menu.setBackgroundDrawable(bg);menu.setElevation(dp(4));menu.setOutsideTouchable(true);menu.setAnimationStyle(R.style.OptionsAnimation);menu.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        list.setOnItemClickListener((parent,v,position,id)->{setSelection((int)id);menu.dismiss();});menu.showAsDropDown(this,0,dp(4));return true;
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDetachedFromWindow(){if(menu!=null)menu.dismiss();super.onDetachedFromWindow();}
}
