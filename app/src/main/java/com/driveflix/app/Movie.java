package com.driveflix.app;

import org.json.JSONArray;
import org.json.JSONObject;

public class Movie {
    public String id,title,category,year,poster,fileId,url,subtitleUrl,description;
    public static Movie fromJson(JSONObject o){
        Movie m=new Movie();
        m.id=o.optString("id", o.optString("fileId", o.optString("title").replaceAll("[^A-Za-z0-9_]+","_")));
        m.title=o.optString("title","Untitled");
        m.category=o.optString("category","Movies");
        m.year=o.optString("year","");
        m.poster=o.optString("poster", o.optString("posterUrl",""));
        m.fileId=o.optString("fileId","");
        m.url=o.optString("movie", o.optString("movieUrl", o.optString("url", "")));
        m.subtitleUrl=readSubtitleUrl(o);
        m.description=o.optString("description","");
        return m;
    }

    private static String readSubtitleUrl(JSONObject o){
        String direct=o.optString("subtitle", o.optString("subtitleUrl", "")).trim();
        if(!direct.isEmpty()) return direct;
        JSONArray subtitles=o.optJSONArray("subtitles");
        if(subtitles==null) return "";
        for(int i=0;i<subtitles.length();i++){
            JSONObject item=subtitles.optJSONObject(i);
            if(item==null) continue;
            String url=item.optString("url", item.optString("subtitle", "")).trim();
            if(isUsableSubtitleUrl(url)) return url;
        }
        return "";
    }

    private static boolean isUsableSubtitleUrl(String url){
        if(url==null || url.trim().isEmpty()) return false;
        String value=url.trim();
        String driveId=extractDriveId(value);
        if(driveId!=null && !driveId.isEmpty()) return true;
        String lower=value.toLowerCase(java.util.Locale.US);
        boolean supported=lower.endsWith(".srt") || lower.endsWith(".vtt") || lower.contains(".srt?") || lower.contains(".vtt?");
        boolean scheme=lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file://");
        return scheme && supported;
    }

    public String movieUrl(){
        if(url!=null && !url.isEmpty()) return url;
        if(fileId!=null && !fileId.isEmpty()) return "https://drive.google.com/uc?export=download&id="+fileId;
        return "";
    }

    // Poster links from Google Drive sharing pages do not load reliably in ImageView/Glide.
    // This method accepts any of these formats:
    // 1) Full normal image URL
    // 2) Google Drive file share URL: https://drive.google.com/file/d/FILE_ID/view
    // 3) Google Drive uc URL: https://drive.google.com/uc?export=view&id=FILE_ID
    // 4) Plain Google Drive file id
    // and converts Drive IDs to the public thumbnail endpoint.
    public String posterUrl(){
        if(poster==null || poster.trim().isEmpty()) return "";
        String p=poster.trim();
        String id=extractDriveId(p);
        if(id!=null && !id.isEmpty()) return "https://drive.google.com/thumbnail?id="+id+"&sz=w600";
        return p;
    }

    private static String extractDriveId(String s){
        try{
            android.net.Uri u=android.net.Uri.parse(s);
            String id=u.getQueryParameter("id");
            if(id!=null && id.length()>10) return id;
        }catch(Exception ignored){}
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("/file/d/([^/]+)").matcher(s);
        if(m.find()) return m.group(1);
        m=java.util.regex.Pattern.compile("/d/([^/]+)").matcher(s);
        if(m.find()) return m.group(1);
        // Plain Drive IDs are usually long and contain only these characters.
        if(s.matches("[A-Za-z0-9_-]{20,}")) return s;
        return null;
    }

    public String safeFileName(){return id.replaceAll("[^A-Za-z0-9._-]","_")+".mp4";}
}
