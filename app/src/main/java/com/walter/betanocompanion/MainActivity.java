package com.walter.betanocompanion;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

public class MainActivity extends android.app.Activity {
  EditText game, rtp, volatility, bankroll, wager; TextView result; SharedPreferences prefs;
  int pad;
  @Override public void onCreate(Bundle state) { super.onCreate(state); prefs=getSharedPreferences("session",MODE_PRIVATE); pad=dp(16); build(); }
  TextView title(String s){ TextView t=new TextView(this); t.setText(s);t.setTextSize(22);t.setTextColor(Color.rgb(20,35,60));t.setPadding(0,0,0,dp(8));return t; }
  EditText input(String hint,String key){ EditText e=new EditText(this);e.setHint(hint);e.setText(prefs.getString(key,""));e.setTextSize(16);e.setPadding(pad,dp(10),pad,dp(10));return e; }
  Button button(String s){ Button b=new Button(this);b.setText(s);return b; }
  void build(){ ScrollView sv=new ScrollView(this); LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);box.setPadding(pad,dp(28),pad,pad);sv.addView(box); setContentView(sv);
    box.addView(title("Betano Companion")); TextView note=new TextView(this); note.setText("Registro personal. El RTP no predice el próximo giro ni garantiza ganancia.");note.setTextSize(15);box.addView(note);
    game=input("Juego (ej.: Book of Dead)","game"); rtp=input("RTP publicado % (ej.: 96.2)","rtp"); volatility=input("Volatilidad: baja / media / alta","vol"); bankroll=input("Saldo de sesión $","bank"); wager=input("Total apostado $","wager");
    box.addView(game);box.addView(rtp);box.addView(volatility);box.addView(bankroll);box.addView(wager);
    Button save=button("Guardar y analizar"); Button overlay=button("Activar burbuja flotante"); Button stop=button("Ocultar burbuja"); box.addView(save);box.addView(overlay);box.addView(stop);
    result=new TextView(this);result.setTextSize(16);result.setPadding(0,pad,0,0);box.addView(result); analyze();
    save.setOnClickListener(v->{ save(); analyze(); }); overlay.setOnClickListener(v->startOverlay()); stop.setOnClickListener(v->stopService(new Intent(this,OverlayService.class)));
  }
  void save(){ prefs.edit().putString("game",game.getText().toString()).putString("rtp",rtp.getText().toString()).putString("vol",volatility.getText().toString()).putString("bank",bankroll.getText().toString()).putString("wager",wager.getText().toString()).apply(); }
  void analyze(){ try { double value=Double.parseDouble(rtp.getText().toString().replace(',','.')); String msg=value>=96?"RTP alto dentro de lo publicado.":"RTP por debajo de 96%."; msg+="\nNo significa que vaya a pagar ahora. Usá un límite y frená al alcanzarlo."; result.setText(msg); } catch(Exception e){result.setText("Cargá el RTP publicado para ver una referencia. No uses estimaciones de ‘racha’.");} }
  void startOverlay(){ save(); if(!Settings.canDrawOverlays(this)){ Intent i=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()));startActivity(i);Toast.makeText(this,"Habilitá el permiso y tocá de nuevo Activar",Toast.LENGTH_LONG).show();return;} startService(new Intent(this,OverlayService.class)); }
  int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
}
