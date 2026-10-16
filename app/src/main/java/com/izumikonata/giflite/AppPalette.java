package com.izumikonata.giflite;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import java.util.Arrays;

public final class AppPalette {
    public final int page,surface,ink,muted,accent,onAccent,soft;
    public final boolean dynamic;
    public final int signature;
    private AppPalette(int[] c,boolean dynamic){page=c[0];surface=c[1];ink=c[2];muted=c[3];accent=c[4];onAccent=c[5];soft=c[6];this.dynamic=dynamic;signature=31*Arrays.hashCode(c)+(dynamic?1:0);}
    public static boolean enabled(Context c){return c.getSharedPreferences("appearance",Context.MODE_PRIVATE).getBoolean("dynamicColor",true);}
    public static AppPalette read(Context c){
        int[] fallback={c.getColor(R.color.page),c.getColor(R.color.surface),c.getColor(R.color.ink),c.getColor(R.color.muted),c.getColor(R.color.accent),c.getColor(R.color.on_accent),c.getColor(R.color.soft)};
        int[] system=null;
        if(enabled(c)&&Build.VERSION.SDK_INT>=31){
            try{
                boolean night=(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
                int[] ids=night?new int[]{android.R.color.system_neutral1_900,android.R.color.system_neutral1_800,android.R.color.system_neutral1_50,android.R.color.system_neutral2_200,android.R.color.system_accent1_200,android.R.color.system_accent1_800,android.R.color.system_accent2_800}
                    :new int[]{android.R.color.system_neutral1_10,android.R.color.system_neutral1_0,android.R.color.system_neutral1_900,android.R.color.system_neutral2_700,android.R.color.system_accent1_600,android.R.color.system_accent1_0,android.R.color.system_accent2_100};
                system=new int[7];for(int i=0;i<7;i++)system[i]=c.getColor(ids[i]);
            }catch(RuntimeException unavailable){system=null;}
        }
        int[] selected=ColorPolicy.select(enabled(c),system,fallback);
        return new AppPalette(selected,selected==system);
    }
}
