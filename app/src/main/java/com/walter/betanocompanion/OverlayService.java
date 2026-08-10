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
  TextView info;
  Handler handler=new Handler(Looper.getMainLooper());
  Runnable refresher;

  public IBinder onBind(Intent i){ return null; }
  int dp(int n){ return (int)(n * getResources().getDisplayMetrics().density); }

  @Override public int onStartCommand(Intent i,int f,int id){
    if(bubble!=null) return START_NOT_STICKY;
    wm=(WindowManager)getSystemService(WINDOW_SERVICE);

    LinearLayout panel=new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(10),dp(8),dp(8),dp(8));
    panel.setBackgroundColor(Color.rgb(30,73,160));

    LinearLayout top=new LinearLayout(this);
    top.setOrientation(LinearLayout.HORIZONTAL);
    top.setGravity(Gravity.CENTER_VERTICAL);

    info=new TextView(this);
    info.setTextSize(13);
    info.setTextColor(Color.WHITE);
    info.setMaxWidth(dp(260));
    info.setPadding(0,0,dp(10),0);
    top.addView(info,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));

    Button read=new Button(this);
    read.setText("LEER");
    read.setTextSize(12);
    read.setAllCaps(true);
    read.setMinWidth(0);
    read.setMinimumWidth(0);
    read.setPadding(dp(10),0,dp(10),0);
    read.setOnClickListener(v->{
      getSharedPreferences("session",MODE_PRIVATE).edit().putString("analysis_summary","Leyendo pantalla…").apply();
      refresh();
      Intent scan=new Intent(this,ScreenScanService.class).setAction("SCAN");
      startService(scan);
    });
    top.addView(read,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,dp(44)));
    panel.addView(top);

    TextView hint=new TextView(this);
    hint.setText("Compara RTP publicado; no predice premios");
    hint.setTextSize(9);
    hint.setTextColor(Color.LTGRAY);
    panel.addView(hint);

    bubble=panel;
    WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
      dp(330),WindowManager.LayoutParams.WRAP_CONTENT,
      Build.VERSION.SDK_INT>=26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT
    );
    lp.gravity=Gravity.TOP|Gravity.END; lp.x=20; lp.y=150;
    wm.addView(bubble,lp);

    refresher=()->{refresh();handler.postDelayed(refresher,800);};
    handler.post(refresher);
    return START_NOT_STICKY;
  }

  void refresh(){
    if(info==null)return;
    SharedPreferences p=getSharedPreferences("session",MODE_PRIVATE);
    String summary=p.getString("analysis_summary","");
    if(summary.isEmpty()){
      String g=p.getString("game","Sin juego");String r=p.getString("rtp","—");
      info.setText("✦ "+g+"\nRTP: "+r+"%");
    } else info.setText(summary);
  }

  @Override public void onDestroy(){
    if(refresher!=null)handler.removeCallbacks(refresher);
    if(bubble!=null){wm.removeView(bubble);bubble=null;}
    super.onDestroy();
  }
}
