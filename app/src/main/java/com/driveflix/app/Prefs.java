package com.driveflix.app;
import android.content.*;

public class Prefs{
 static SharedPreferences p(Context c){return c.getSharedPreferences("driveflix",0);} 
 static boolean fav(Context c,String id){return p(c).getBoolean("fav_"+id,false);} 
 static void fav(Context c,String id,boolean v){p(c).edit().putBoolean("fav_"+id,v).apply();}
 static long pos(Context c,String id){return p(c).getLong("pos_"+id,0);} 
 static void pos(Context c,String id,long v){p(c).edit().putLong("pos_"+id,v).apply();}
 static String library(Context c){return p(c).getString("library_url","");}
 static void library(Context c,String u){p(c).edit().putString("library_url",u).apply();}
 static String libraryCache(Context c){return p(c).getString("library_cache","");}
 static void libraryCache(Context c,String json){p(c).edit().putString("library_cache",json).apply();}
 static String local(Context c,String id){return p(c).getString("local_"+id,"");}
 static void local(Context c,String id,String path){p(c).edit().putString("local_"+id,path).apply();}
 static void clearLocal(Context c,String id){p(c).edit().remove("local_"+id).apply();}
 static void pending(Context c,long downloadId,String movieId,String path){p(c).edit().putString("pending_"+downloadId,movieId+"|"+path).apply();}
 static String pending(Context c,long downloadId){return p(c).getString("pending_"+downloadId,"");}
 static void clearPending(Context c,long downloadId){p(c).edit().remove("pending_"+downloadId).apply();}
}
