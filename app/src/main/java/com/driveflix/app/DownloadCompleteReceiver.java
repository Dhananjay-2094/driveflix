package com.driveflix.app;

import android.app.*;import android.content.*;import android.database.Cursor;import android.widget.Toast;import java.io.File;

public class DownloadCompleteReceiver extends BroadcastReceiver{
 @Override public void onReceive(Context c, Intent intent){
  long id=intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-1); if(id==-1)return;
  String data=Prefs.pending(c,id); if(data.isEmpty())return;
  String[] parts=data.split("\\|",2); if(parts.length<2)return;
  String movieId=parts[0], path=parts[1];
  DownloadManager dm=(DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);
  Cursor cur=dm.query(new DownloadManager.Query().setFilterById(id));
  boolean ok=false; String reason="";
  if(cur!=null && cur.moveToFirst()){
   int status=cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
   ok=status==DownloadManager.STATUS_SUCCESSFUL;
   if(!ok) reason="Download failed";
   cur.close();
  }
  if(ok && new File(path).exists()){
   Prefs.local(c,movieId,path);
   Toast.makeText(c,"Movie downloaded. Tap Play to watch offline.",Toast.LENGTH_LONG).show();
  }else{
   Prefs.clearLocal(c,movieId);
   Toast.makeText(c,reason.isEmpty()?"Download not completed":reason,Toast.LENGTH_LONG).show();
  }
  Prefs.clearPending(c,id);
 }
}
