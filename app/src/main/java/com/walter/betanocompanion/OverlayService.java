package com.walter.betanocompanion;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;

public class OverlayService extends Service {
  WindowManager wm;
  View bubble;

  public IBinder onBind(Intent i){ return null; }

  int dp(int n){ return (int)(n * getResources().getDisplayMetrics().density); }

  @Override public int onStartCommand(Intent i,int f,int id){
    if(bubble!=null) return START_NOT_STICKY;

    wm=(WindowManager)getSystemService(WINDOW_SERVICE);
    SharedPreferences p=getSharedPreferences("session",MODE_PRIVATE);

    LinearLayout panel=new LinearLayout(this);
    panel.setOrientation(LinearLayout.HORIZONTAL);
    panel.setGravity(Gravity.CENTER_VERTICAL);
    panel.setPadding(dp(10),dp(8),dp(8),dp(8));
    panel.setBackgroundColor(Color.rgb(30,73,160));

    TextView info=new TextView(this);
    String g=p.getString("game","Sin juego");
    String r=p.getString("rtp","—");
    info.setText("✦  "+g+"\nRTP: "+r+"%");
    info.setTextSize(13);
    info.setTextColor(Color.WHITE);
    info.setPadding(0,0,dp(10),0);
    panel.addView(info,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT));

    Button read=new Button(this);
    read.setText("LEER");
    read.setTextSize(12);
    read.setAllCaps(true);
    read.setMinWidth(0);
    read.setMinimumWidth(0);
    read.setPadding(dp(10),0,dp(10),0);
    read.setOnClickListener(v->{
      Toast.makeText(this,"Leyendo pantalla…",Toast.LENGTH_SHORT).show();
      Intent scan=new Intent(this,ScreenScanService.class).setAction("SCAN");
      startService(scan);
    });
    panel.addView(read,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,dp(44)));

    bubble=panel;
    WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      Build.VERSION.SDK_INT>=26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT
    );
    lp.gravity=Gravity.TOP|Gravity.END;
    lp.x=20;
    lp.y=150;
    wm.addView(bubble,lp);
    return START_NOT_STICKY;
  }

  @Override public void onDestroy(){
    if(bubble!=null){ wm.removeView(bubble); bubble=null; }
    super.onDestroy();
  }
}
