package com.walter.betanocompanion;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

public class WebGameLookup {
  public static class Result {
    public final String game;
    public final double rtp;
    public final String volatility;
    public final String source;
    public Result(String game,double rtp,String volatility,String source){this.game=game;this.rtp=rtp;this.volatility=volatility;this.source=source;}
  }

  static final Pattern LINK=Pattern.compile("href=\\\"(https://blog-br\\.betano\\.com/[^\\\"]+)\\\"",Pattern.CASE_INSENSITIVE);
  static final Pattern RTP=Pattern.compile("(?i)RTP[^0-9]{0,25}(\\d{2}[,.]\\d{1,2})\\s*%?");
  static final Pattern VOL=Pattern.compile("(?i)volatilidade[^a-zA-Záéíóúãõç]{0,20}(baixa|m[eé]dia(?:-alta)?|alta)");

  public static Result lookup(String game) throws Exception {
    String search="https://blog-br.betano.com/?s="+URLEncoder.encode(game,StandardCharsets.UTF_8.name());
    String html=get(search);
    LinkedHashSet<String> links=new LinkedHashSet<>();
    Matcher lm=LINK.matcher(html);
    while(lm.find() && links.size()<4){
      String u=lm.group(1).replace("&amp;","&");
      if(u.contains("/tutoriais/") || u.contains("/cassino/")) links.add(u);
    }
    Result best=null;
    for(String u:links){
      String page=get(u);
      String text=strip(page);
      if(!looselyMatches(text,game)) continue;
      Matcher rm=RTP.matcher(text);
      if(!rm.find()) continue;
      double rtp=Double.parseDouble(rm.group(1).replace(',','.'));
      String vol="no informada";
      Matcher vm=VOL.matcher(text);
      if(vm.find()) vol=vm.group(1).replace('é','e');
      Result r=new Result(game,rtp,vol,"Betano Blog");
      if(best==null || r.rtp>best.rtp) best=r;
    }
    return best;
  }

  static boolean looselyMatches(String page,String game){
    String a=norm(page), b=norm(game);
    String[] words=b.split(" "); int hit=0,total=0;
    for(String w:words){ if(w.length()<3) continue; total++; if(a.contains(w)) hit++; }
    return total>0 && hit>=Math.max(1,(int)Math.ceil(total*0.6));
  }

  static String norm(String s){
    String n=Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}+","");
    return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]+"," ").replaceAll("\\s+"," ");
  }

  static String strip(String html){
    return html.replaceAll("(?is)<script.*?</script>"," ").replaceAll("(?is)<style.*?</style>"," ").replaceAll("(?s)<[^>]+>"," ").replace("&nbsp;"," ").replace("&amp;","&").replaceAll("\\s+"," ");
  }

  static String get(String url) throws Exception {
    HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
    c.setConnectTimeout(7000); c.setReadTimeout(7000); c.setInstanceFollowRedirects(true);
    c.setRequestProperty("User-Agent","Mozilla/5.0 BetanoCompanion/1.2");
    try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()){
      byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1 && out.size()<1500000) out.write(b,0,n);
      return out.toString(StandardCharsets.UTF_8.name());
    } finally { c.disconnect(); }
  }
}
