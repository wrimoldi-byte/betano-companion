package com.walter.betanocompanion;

import android.app.*;import android.content.*;import android.content.pm.ServiceInfo;import android.graphics.*;import android.hardware.display.DisplayManager;import android.hardware.display.VirtualDisplay;import android.media.Image;import android.media.ImageReader;import android.media.projection.MediaProjection;import android.media.projection.MediaProjectionManager;import android.os.*;import android.view.WindowManager;import android.widget.Toast;
import com.google.mlkit.vision.common.InputImage;import com.google.mlkit.vision.text.TextRecognition;import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.nio.ByteBuffer;import java.util.*;import java.util.regex.*;

public class ScreenScanService extends Service {
  MediaProjection projection; int w,h,density; boolean ready=false;
  public android.os.IBinder onBind(Intent i){return null;}
  @Override public int onStartCommand(Intent i,int flags,int id){
    if(i!=null && "SCAN".equals(i.getAction())) { if(ready) capture(); else Toast.makeText(this,"Primero activá la lectura desde la app",Toast.LENGTH_SHORT).show(); return START_NOT_STICKY; }
    if(i!=null && i.hasExtra("data")){ MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);projection=m.getMediaProjection(i.getIntExtra("resultCode",-1),(Intent)i.getParcelableExtra("data")); WindowManager wm=(WindowManager)getSystemService(WINDOW_SERVICE);android.util.DisplayMetrics dm=new android.util.DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(dm);w=dm.widthPixels;h=dm.heightPixels;density=dm.densityDpi;startForeground(7,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);ready=projection!=null; }
    return START_NOT_STICKY;
  }
  Notification notification(){String channel="screen_scan";NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel(channel,"Lectura de pantalla",NotificationManager.IMPORTANCE_LOW));return new Notification.Builder(this,channel).setContentTitle("Betano Companion activo").setContentText("Tocá LEER para detectar y comparar juegos visibles").setSmallIcon(android.R.drawable.ic_menu_view).build();}

  void capture(){
    SharedPreferences p=getSharedPreferences("session",MODE_PRIVATE);
    p.edit().putString("analysis_summary","Leyendo pantalla…").apply();
    ImageReader reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
    final VirtualDisplay display=projection.createVirtualDisplay("CompanionScan",w,h,density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,null);
    new Handler(Looper.getMainLooper()).postDelayed(()->{
      Image image=reader.acquireLatestImage();
      if(image==null){ p.edit().putString("analysis_summary","No se pudo capturar la pantalla").apply(); Toast.makeText(this,"No se pudo leer la pantalla. El juego puede bloquear capturas.",Toast.LENGTH_LONG).show(); display.release();reader.close();return; }
      Bitmap b=bitmap(image); image.close(); display.release(); reader.close();
      TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromBitmap(b,0))
        .addOnSuccessListener(t->saveText(t.getText()))
        .addOnFailureListener(e->{p.edit().putString("analysis_summary","OCR: no se detectó texto").apply();Toast.makeText(this,"No se detectó texto visible",Toast.LENGTH_SHORT).show();});
    },500);
  }

  Bitmap bitmap(Image img){Image.Plane p=img.getPlanes()[0];ByteBuffer buf=p.getBuffer();Bitmap b=Bitmap.createBitmap(w+p.getRowStride()/p.getPixelStride()-w,h,Bitmap.Config.ARGB_8888);b.copyPixelsFromBuffer(buf);return Bitmap.createBitmap(b,0,0,w,h);}

  void saveText(String text){
    SharedPreferences p=getSharedPreferences("session",MODE_PRIVATE);
    Matcher m=Pattern.compile("(?i)(?:rtp|return to player)\\s*[:\\-]?\\s*(\\d{2}[,.]\\d{1,2})").matcher(text);
    String visibleRtp=m.find()?m.group(1):"";
    List<String> games=gameCandidates(text);
    p.edit().putString("last_scan",text).putString("detected_games",String.join(" | ",games)).putString("analysis_summary",games.isEmpty()?"No detecté nombres de juegos":"Buscando datos de "+games.size()+" juego(s)…").apply();
    if(!visibleRtp.isEmpty()) p.edit().putString("rtp",visibleRtp).apply();
    if(games.isEmpty()){Toast.makeText(this,"Leí la pantalla, pero no pude aislar nombres de juegos",Toast.LENGTH_LONG).show();return;}
    new Thread(()->compareGames(games)).start();
  }

  void compareGames(List<String> games){
    SharedPreferences p=getSharedPreferences("session",MODE_PRIVATE);
    ArrayList<WebGameLookup.Result> found=new ArrayList<>();
    int limit=Math.min(5,games.size());
    for(int i=0;i<limit;i++){
      String game=games.get(i);
      try{WebGameLookup.Result r=WebGameLookup.lookup(game);if(r!=null)found.add(r);}catch(Exception ignored){}
    }
    if(found.isEmpty()){
      p.edit().putString("analysis_summary","Sin datos web verificables para los nombres detectados").apply();
      new Handler(Looper.getMainLooper()).post(()->Toast.makeText(this,"No encontré RTP publicado para esos nombres",Toast.LENGTH_LONG).show());
      return;
    }
    found.sort((a,b)->Double.compare(b.rtp,a.rtp));
    WebGameLookup.Result best=found.get(0);
    StringBuilder s=new StringBuilder();
    s.append("MEJOR RTP: ").append(best.game).append(" · ").append(String.format(Locale.US,"%.2f",best.rtp)).append("% · vol. ").append(best.volatility);
    if(found.size()>1){s.append("\n");for(int i=1;i<Math.min(3,found.size());i++){WebGameLookup.Result r=found.get(i);s.append(i+1).append(") ").append(r.game).append(" ").append(String.format(Locale.US,"%.2f",r.rtp)).append("%  ");}}
    s.append("\nRTP = promedio teórico, no predice el próximo giro.");
    p.edit().putString("game",best.game).putString("rtp",String.format(Locale.US,"%.2f",best.rtp)).putString("vol",best.volatility).putString("analysis_summary",s.toString()).putString("analysis_source",best.source).apply();
    new Handler(Looper.getMainLooper()).post(()->Toast.makeText(this,"Comparación lista: "+best.game+" "+String.format(Locale.US,"%.2f",best.rtp)+"%",Toast.LENGTH_LONG).show());
  }

  List<String> gameCandidates(String text){
    LinkedHashSet<String> out=new LinkedHashSet<>();
    String[] stop={"betano","casino","cassino","jugar","jogue","jogos","juegos","inicio","home","buscar","search","saldo","depositar","deposit","menu","premios","apuestas","aposta","gratis","free spins","popular","favoritos","favoritos","slots","slot","rtp","volatilidad","volatilidade","jackpot","en vivo","ao vivo","leer"};
    for(String raw:text.split("\\r?\\n")){
      String line=raw.trim().replaceAll("\\s+"," ");
      if(line.length()<4 || line.length()>42)continue;
      if(!line.matches(".*[A-Za-zÁÉÍÓÚÜÑáéíóúüñÃÕÇãõç].*"))continue;
      if(line.matches(".*\\d{2,}[,.]?\\d*%.*") || line.matches("^[\\d.,$€R$%xX+\\- ]+$"))continue;
      String low=line.toLowerCase(Locale.ROOT);
      boolean bad=false;for(String z:stop){if(low.equals(z)||low.startsWith(z+" ")||low.endsWith(" "+z)){bad=true;break;}}
      if(bad)continue;
      int letters=line.replaceAll("[^A-Za-zÁÉÍÓÚÜÑáéíóúüñÃÕÇãõç]","").length();
      if(letters<4)continue;
      out.add(line);
      if(out.size()>=10)break;
    }
    return new ArrayList<>(out);
  }
}
