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

    SharedPreferences prefs=getSharedPreferences("session",MODE_PRIVATE);
    String oldSummary=prefs.getString("analysis_summary","");
    if(isLegacyServerError(oldSummary)) prefs.edit().remove("analysis_summary").apply();

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

    Button chatgpt=new Button(this);
    chatgpt.setText("ANALIZAR CON CHATGPT");
    chatgpt.setTextSize(11);
    chatgpt.setAllCaps(true);
    chatgpt.setOnClickListener(v->openChatGPT());
    LinearLayout.LayoutParams chatParams=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(42));
    chatParams.topMargin=dp(4);
    panel.addView(chatgpt,chatParams);

    TextView hint=new TextView(this);
    hint.setText("ChatGPT puede contrastar varias fuentes; RTP no predice premios");
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

  void openChatGPT(){
    SharedPreferences p=getSharedPreferences("session",MODE_PRIVATE);
    String games=p.getString("detected_games","").trim();
    String scan=p.getString("last_scan","").trim();
    if(games.isEmpty() && scan.isEmpty()){
      Toast.makeText(this,"Primero tocá LEER para detectar los juegos",Toast.LENGTH_LONG).show();
      return;
    }

    StringBuilder prompt=new StringBuilder();
    prompt.append("Analizá estos juegos de casino que detectó mi app en pantalla. ");
    prompt.append("Buscá información actual en varias páginas web confiables y compará, cuando esté disponible: RTP publicado, volatilidad, proveedor, mecánica y cualquier diferencia de RTP entre versiones. ");
    prompt.append("No intentes predecir el próximo giro ni afirmar que un juego va a pagar. ");
    prompt.append("Al final ordenalos por mejor RTP publicado y explicame brevemente cuál elegirías solo por esos datos teóricos.\n\n");
    if(!games.isEmpty()) prompt.append("Juegos detectados:\n").append(games).append("\n\n");
    if(!scan.isEmpty()){
      String clipped=scan.length()>5000?scan.substring(0,5000):scan;
      prompt.append("Texto OCR de la pantalla (puede contener errores):\n").append(clipped);
    }

    Intent direct=new Intent(Intent.ACTION_SEND);
    direct.setType("text/plain");
    direct.putExtra(Intent.EXTRA_TEXT,prompt.toString());
    direct.setPackage("com.openai.chatgpt");
    direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

    try{
      startActivity(direct);
    }catch(Exception e){
      Intent share=new Intent(Intent.ACTION_SEND);
      share.setType("text/plain");
      share.putExtra(Intent.EXTRA_TEXT,prompt.toString());
      Intent chooser=Intent.createChooser(share,"Abrir análisis en ChatGPT");
      chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(chooser);
    }
  }

  boolean isLegacyServerError(String s){
    if(s==null)return false;
    String low=s.toLowerCase();
    return low.contains("ai gateway") ||
           low.contains("ia del servidor") ||
           low.contains("gateway en vercel") ||
           low.contains("servidor está bloqueada") ||
           low.contains("servidor esta bloqueada");
  }

  void refresh(){
    if(info==null)return;
    SharedPreferences p=getSharedPreferences("session",MODE_PRIVATE);
    String summary=p.getString("analysis_summary","");
    if(isLegacyServerError(summary)){
      p.edit().remove("analysis_summary").apply();
      summary="";
    }
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
