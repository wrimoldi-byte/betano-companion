package com.walter.betanocompanion;

import android.app.*;import android.content.*;import android.content.pm.ServiceInfo;import android.graphics.*;import android.hardware.display.DisplayManager;import android.media.Image;import android.media.ImageReader;import android.media.projection.MediaProjection;import android.media.projection.MediaProjectionManager;import android.os.*;import android.view.WindowManager;import android.widget.Toast;
import com.google.mlkit.vision.common.InputImage;import com.google.mlkit.vision.text.TextRecognition;import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.nio.ByteBuffer;import java.util.regex.*;

public class ScreenScanService extends Service {
  MediaProjection projection; int w,h,density; boolean ready=false;
  public android.os.IBinder onBind(Intent i){return null;}
  @Override public int onStartCommand(Intent i,int flags,int id){
    if(i!=null && "SCAN".equals(i.getAction())) { if(ready) capture(); else Toast.makeText(this,"Primero activá la lectura desde la app",Toast.LENGTH_SHORT).show(); return START_NOT_STICKY; }
    if(i!=null && i.hasExtra("data")){ MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);projection=m.getMediaProjection(i.getIntExtra("resultCode",-1),(Intent)i.getParcelableExtra("data")); WindowManager wm=(WindowManager)getSystemService(WINDOW_SERVICE);android.util.DisplayMetrics dm=new android.util.DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(dm);w=dm.widthPixels;h=dm.heightPixels;density=dm.densityDpi;startForeground(7,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);ready=projection!=null; }
    return START_NOT_STICKY;
  }
  Notification notification(){String channel="screen_scan";NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel(channel,"Lectura de pantalla",NotificationManager.IMPORTANCE_LOW));return new Notification.Builder(this,channel).setContentTitle("Betano Companion activo").setContentText("Tocá LEER en la burbuja para analizar texto visible").setSmallIcon(android.R.drawable.ic_menu_view).build();}
  void capture(){ ImageReader reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);projection.createVirtualDisplay("CompanionScan",w,h,density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,null);new Handler().postDelayed(()->{Image image=reader.acquireLatestImage();if(image==null){Toast.makeText(this,"No se pudo leer la pantalla. El juego puede bloquear capturas.",Toast.LENGTH_LONG).show();reader.close();return;}Bitmap b=bitmap(image);image.close();reader.close();TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromBitmap(b,0)).addOnSuccessListener(t->saveText(t.getText())).addOnFailureListener(e->Toast.makeText(this,"No se detectó texto visible",Toast.LENGTH_SHORT).show());},500);}
  Bitmap bitmap(Image img){Image.Plane p=img.getPlanes()[0];ByteBuffer buf=p.getBuffer();Bitmap b=Bitmap.createBitmap(w+p.getRowStride()/p.getPixelStride()-w,h,Bitmap.Config.ARGB_8888);b.copyPixelsFromBuffer(buf);return Bitmap.createBitmap(b,0,0,w,h);}
  void saveText(String text){SharedPreferences p=getSharedPreferences("session",MODE_PRIVATE);Matcher m=Pattern.compile("(?i)(?:rtp|return to player)\\s*[:\\-]?\\s*(\\d{2}[,.]\\d{1,2})").matcher(text);String r=m.find()?m.group(1):p.getString("rtp","");p.edit().putString("rtp",r).putString("last_scan",text).apply();Toast.makeText(this,r.isEmpty()?"Texto leído; no se encontró RTP visible":"RTP detectado: "+r+"%",Toast.LENGTH_LONG).show();}
}
