package com.walter.betanocompanion;

import android.app.*; import android.content.*; import android.graphics.*; import android.os.*; import android.view.*; import android.widget.*;

public class OverlayService extends Service {
  WindowManager wm; View bubble;
  public IBinder onBind(Intent i){return null;}
  @Override public int onStartCommand(Intent i,int f,int id){ if(bubble!=null)return START_NOT_STICKY; wm=(WindowManager)getSystemService(WINDOW_SERVICE); SharedPreferences p=getSharedPreferences("session",MODE_PRIVATE);
    TextView v=new TextView(this); String g=p.getString("game","Sin juego");String r=p.getString("rtp","—");v.setText("  ✦  "+g+"\n     RTP: "+r+"%   "); v.setTextSize(14);v.setTextColor(Color.WHITE);v.setBackgroundColor(Color.rgb(30,73,160));v.setPadding(12,12,18,12);v.setOnClickListener(x->stopSelf());
    bubble=v; WindowManager.LayoutParams lp=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.END;lp.x=20;lp.y=150;wm.addView(bubble,lp); return START_NOT_STICKY; }
  @Override public void onDestroy(){if(bubble!=null){wm.removeView(bubble);bubble=null;}super.onDestroy();}
}
