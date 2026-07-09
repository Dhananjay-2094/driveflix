package com.driveflix.app;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MovieAdapter extends RecyclerView.Adapter<MovieAdapter.VH> {
    interface OnOpen {
        void open(Movie m);
    }

    private static final List<String> VIDEO_EXTENSIONS = Arrays.asList("mkv", "mp4", "webm", "mov", "m4v");

    private final Context c;
    private final OnOpen cb;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ConcurrentHashMap<String, Integer> progressMap = new ConcurrentHashMap<>();
    private final Set<String> downloading = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private List<Movie> data = new ArrayList<>();

    MovieAdapter(Context c, OnOpen cb) {
        this.c = c.getApplicationContext();
        this.cb = cb;
    }

    void set(List<Movie> d) {
        data = d;
        notifyDataSetChanged();
    }

    @NonNull
    public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_movie, p, false));
    }

    public void onBindViewHolder(@NonNull VH h, int i) {
        Movie m = data.get(i);
        boolean offline = isDownloaded(m);
        File part = partFile(m);
        boolean partial = part.exists() && part.length() > 0;
        boolean isDown = downloading.contains(m.id);

        h.title.setText(m.title);
        h.meta.setText((m.year.isEmpty() ? "" : m.year + " - ") + m.category + (offline ? " - Downloaded" : partial ? " - Partial download" : " - Download required"));

        long pos = Prefs.pos(c, m.id);
        if (isDown) {
            int pct = progressMap.containsKey(m.id) ? progressMap.get(m.id) : 0;
            h.progress.setText(pct > 0 ? "Downloading " + pct + "%" : "Downloading...");
            h.bar.setVisibility(View.VISIBLE);
            h.bar.setIndeterminate(pct <= 0);
            h.bar.setProgress(pct);
        } else {
            h.bar.setVisibility(View.GONE);
            h.progress.setText(pos > 0 ? "Resume from " + (pos / 60000) + " min" : partial ? "Download can resume from " + formatBytes(part.length()) : "Not started");
        }

        String posterUrl = m.posterUrl();
        if (!posterUrl.isEmpty()) {
            Glide.with(h.poster.getContext())
                    .load(posterUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(h.poster);
        } else {
            h.poster.setImageResource(android.R.drawable.ic_media_play);
        }

        h.play.setText(offline ? "Play" : isDown ? "Downloading" : partial ? "Resume DL" : "Download");
        h.play.setEnabled(!isDown);
        h.play.setOnClickListener(v -> {
            if (isDownloaded(m)) cb.open(m);
            else download(m);
        });

        h.fav.setImageResource(Prefs.fav(c, m.id) ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        h.fav.setOnClickListener(v -> {
            Prefs.fav(c, m.id, !Prefs.fav(c, m.id));
            notifyItemChanged(h.getBindingAdapterPosition());
        });

        h.down.setImageResource(offline ? R.drawable.ic_done : R.drawable.ic_download);
        h.down.setEnabled(!isDown);
        h.down.setOnClickListener(v -> {
            if (isDownloaded(m)) toast("Already downloaded");
            else download(m);
        });
    }

    public int getItemCount() {
        return data.size();
    }

    boolean isDownloaded(Movie m) {
        String p = Prefs.local(c, m.id);
        if (!p.isEmpty()) {
            File stored = new File(p);
            if (stored.exists()) {
                File normalized = normalizeStoredExtension(m, stored);
                if (!normalized.equals(stored)) Prefs.local(c, m.id, normalized.getAbsolutePath());
                return true;
            }
        }

        File existing = existingDownload(m);
        if (existing != null) {
            Prefs.local(c, m.id, existing.getAbsolutePath());
            return true;
        }
        return false;
    }

    File movieDir() {
        File dir = c.getExternalFilesDir("movies");
        if (dir != null && !dir.exists()) dir.mkdirs();
        return dir == null ? c.getFilesDir() : dir;
    }

    File destFile(Movie m) {
        File existing = existingDownload(m);
        return existing != null ? existing : new File(movieDir(), m.safeFileName());
    }

    File destFile(Movie m, String ext) {
        return new File(movieDir(), m.safeFileName(ext));
    }

    File partFile(Movie m) {
        return new File(movieDir(), baseName(m) + ".part");
    }

    private String baseName(Movie m) {
        return m.id.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private File existingDownload(Movie m) {
        for (String ext : VIDEO_EXTENSIONS) {
            File f = destFile(m, ext);
            if (f.exists()) return f;
        }

        File legacy = new File(movieDir(), m.safeFileName());
        return legacy.exists() ? normalizeStoredExtension(m, legacy) : null;
    }


    private File normalizeStoredExtension(Movie m, File file) {
        String ext = extensionFromFileSignature(file);
        if (ext == null || file.getName().toLowerCase(Locale.US).endsWith("." + ext)) return file;

        File renamed = destFile(m, ext);
        if (renamed.equals(file)) return file;
        if (renamed.exists()) renamed.delete();
        return file.renameTo(renamed) ? renamed : file;
    }

    private String extensionFromFileSignature(File file) {
        byte[] header = new byte[16];
        try (InputStream in = new FileInputStream(file)) {
            int n = in.read(header);
            if (n >= 4 && (header[0] & 0xFF) == 0x1A && (header[1] & 0xFF) == 0x45 && (header[2] & 0xFF) == 0xDF && (header[3] & 0xFF) == 0xA3) return "mkv";
            if (n >= 12 && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p') return "mp4";
        } catch (Exception ignored) {
        }
        return null;
    }
    private void deleteExistingDownloads(Movie m) {
        Set<String> names = new HashSet<>();
        for (String ext : VIDEO_EXTENSIONS) names.add(m.safeFileName(ext));
        names.add(m.safeFileName());

        for (String name : names) {
            File f = new File(movieDir(), name);
            if (f.exists()) f.delete();
        }
    }

    int posOf(String id) {
        for (int x = 0; x < data.size(); x++) {
            if (data.get(x).id.equals(id)) return x;
        }
        return -1;
    }

    void refresh(String id) {
        ui.post(() -> {
            int p = posOf(id);
            if (p >= 0) notifyItemChanged(p);
        });
    }

    void download(Movie m) {
        File part = partFile(m);
        if (downloading.contains(m.id)) return;

        downloading.add(m.id);
        progressMap.put(m.id, 0);
        refresh(m.id);
        toast(part.exists() ? "Resuming download: " + m.title : "Download started: " + m.title);

        new Thread(() -> {
            try {
                String ext = downloadWithGoogleDriveConfirm(m.movieUrl(), part, m.id);
                if (looksLikeHtmlFile(part)) throw new IOException("Downloaded file is a Google Drive page, not a movie");

                String signatureExt = extensionFromFileSignature(part);
                if (signatureExt != null) ext = signatureExt;
                File dest = destFile(m, ext);
                deleteExistingDownloads(m);
                if (!part.renameTo(dest)) copy(part, dest);

                Prefs.local(c, m.id, dest.getAbsolutePath());
                progressMap.put(m.id, 100);
                toast("Download complete: " + m.title);
            } catch (Exception e) {
                toast("Download paused/failed: " + e.getMessage() + ". Tap Resume to continue.");
            } finally {
                downloading.remove(m.id);
                refresh(m.id);
            }
        }).start();
    }

    String downloadWithGoogleDriveConfirm(String rawUrl, File out, String movieId) throws Exception {
        String url = normalizeGoogleDriveUrl(rawUrl);
        long existing = out.exists() ? out.length() : 0;
        DownloadResponse r = openForDownload(url, null, existing);
        int safety = 0;

        while (r.isHtml && safety++ < 4) {
            String next = findGoogleDriveConfirmUrl(r.body, url);
            if (next == null) {
                String fileId = extractDriveFileId(url);
                if (fileId != null) {
                    next = "https://drive.usercontent.google.com/download?id=" + URLEncoder.encode(fileId, "UTF-8") + "&export=download&confirm=t";
                }
            }
            if (next == null) throw new IOException("Download anyway link not detected");
            url = next;
            r = openForDownload(url, mergeCookies(r.cookie, null), existing);
        }

        if (r.isHtml) throw new IOException("Google returned a web page instead of movie");
        stream(r.conn, out, r.append, movieId, existing);
        return extensionForDownload(url, r);
    }

    String normalizeGoogleDriveUrl(String raw) throws Exception {
        String id = extractDriveFileId(raw);
        if (id == null || id.isEmpty()) return raw;
        return "https://drive.google.com/uc?export=download&id=" + URLEncoder.encode(id, "UTF-8");
    }

    String extractDriveFileId(String url) {
        try {
            Uri u = Uri.parse(url);
            String id = u.getQueryParameter("id");
            if (id != null && !id.isEmpty()) return id;
        } catch (Exception ignored) {
        }

        Matcher m = Pattern.compile("/file/d/([^/]+)").matcher(url);
        if (m.find()) return m.group(1);
        m = Pattern.compile("/d/([^/]+)").matcher(url);
        if (m.find()) return m.group(1);
        return null;
    }

    DownloadResponse openForDownload(String url, String cookie, long existing) throws Exception {
        URL u = new URL(url);
        HttpURLConnection cn = (HttpURLConnection) u.openConnection();
        cn.setInstanceFollowRedirects(true);
        cn.setConnectTimeout(30000);
        cn.setReadTimeout(60000);
        cn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
        cn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,video/*,*/*;q=0.8");
        cn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        if (cookie != null && !cookie.isEmpty()) cn.setRequestProperty("Cookie", cookie);
        if (existing > 0) cn.setRequestProperty("Range", "bytes=" + existing + "-");

        int code = cn.getResponseCode();
        String type = cn.getContentType();
        String disp = cn.getHeaderField("Content-Disposition");
        String ck = joinCookies(cn);
        boolean append = existing > 0 && code == 206;
        boolean html = (type != null && type.toLowerCase(Locale.US).contains("text/html")) || (disp == null && looksLikeDrivePage(cn.getURL().toString()));

        if (html) {
            InputStream es = code >= 400 ? cn.getErrorStream() : cn.getInputStream();
            DownloadResponse dr = new DownloadResponse();
            dr.conn = cn;
            dr.cookie = ck;
            dr.body = readSmall(es);
            dr.isHtml = true;
            dr.append = false;
            return dr;
        }

        if (code >= 400) throw new IOException("HTTP " + code);

        DownloadResponse dr = new DownloadResponse();
        dr.conn = cn;
        dr.cookie = ck;
        dr.isHtml = false;
        dr.append = append;
        dr.contentLength = cn.getContentLengthLong();
        dr.contentType = type;
        dr.disposition = disp;
        return dr;
    }

    private String extensionForDownload(String url, DownloadResponse r) {
        String ext = extensionFromDisposition(r.disposition);
        if (ext != null) return ext;

        ext = extensionFromUrl(url);
        if (ext != null) return ext;

        String type = r.contentType == null ? "" : r.contentType.toLowerCase(Locale.US);
        if (type.contains("matroska") || type.contains("x-matroska")) return "mkv";
        if (type.contains("webm")) return "webm";
        if (type.contains("quicktime")) return "mov";
        if (type.contains("mp4") || type.contains("mpeg4")) return "mp4";
        return "mp4";
    }

    private String extensionFromUrl(String url) {
        try {
            String path = Uri.parse(url).getPath();
            if (path == null) return null;
            Matcher m = Pattern.compile("\\.([A-Za-z0-9]{2,5})$").matcher(path);
            return m.find() ? cleanVideoExt(m.group(1)) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extensionFromDisposition(String disposition) {
        if (disposition == null) return null;

        Matcher encoded = Pattern.compile("filename\\*=([^;]+)", Pattern.CASE_INSENSITIVE).matcher(disposition);
        if (encoded.find()) {
            String value = encoded.group(1).trim().replace("\"", "");
            int charsetSep = value.indexOf("''");
            if (charsetSep >= 0) value = value.substring(charsetSep + 2);
            try {
                String ext = extensionFromName(URLDecoder.decode(value, "UTF-8"));
                if (ext != null) return ext;
            } catch (Exception ignored) {
            }
        }

        Matcher normal = Pattern.compile("filename=\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE).matcher(disposition);
        return normal.find() ? extensionFromName(normal.group(1)) : null;
    }

    private String extensionFromName(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot >= 0 ? cleanVideoExt(name.substring(dot + 1)) : null;
    }

    private String cleanVideoExt(String ext) {
        if (ext == null) return null;
        ext = ext.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.US);
        return VIDEO_EXTENSIONS.contains(ext) ? ext : null;
    }

    boolean looksLikeDrivePage(String finalUrl) {
        return finalUrl.contains("drive.google.com") && !finalUrl.contains("export=download");
    }

    String findGoogleDriveConfirmUrl(String html, String original) throws Exception {
        if (html == null) return null;

        html = html.replace("&amp;", "&").replace("\\u003d", "=").replace("\\u0026", "&");
        Matcher form = Pattern.compile("<form[^>]+action=\\\"([^\\\"]*download[^\\\"]*)\\\"[^>]*>(.*?)</form>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        while (form.find()) {
            String action = form.group(1);
            String fields = form.group(2);
            String q = hiddenInputsToQuery(fields);
            if (q.contains("confirm=") || q.contains("uuid=") || q.contains("id=")) {
                if (action.startsWith("/")) action = "https://drive.usercontent.google.com" + action;
                return action + (action.contains("?") ? "&" : "?") + q;
            }
        }

        Matcher m = Pattern.compile("href=\\\"(https://drive\\.usercontent\\.google\\.com/download[^\\\"]+)\\\"").matcher(html);
        if (m.find()) return m.group(1);

        m = Pattern.compile("href=\\\"(/uc\\?export=download[^\\\"]*)\\\"").matcher(html);
        if (m.find()) return "https://drive.google.com" + m.group(1);

        m = Pattern.compile("confirm=([0-9A-Za-z_%-]+)").matcher(html);
        if (m.find()) {
            String token = URLEncoder.encode(m.group(1), "UTF-8");
            String id = extractDriveFileId(original);
            if (id != null && !id.isEmpty()) {
                return "https://drive.google.com/uc?export=download&confirm=" + token + "&id=" + URLEncoder.encode(id, "UTF-8");
            }
        }
        return null;
    }

    String hiddenInputsToQuery(String form) throws Exception {
        StringBuilder sb = new StringBuilder();
        Matcher in = Pattern.compile("<input[^>]+>", Pattern.CASE_INSENSITIVE).matcher(form);
        while (in.find()) {
            String tag = in.group();
            String name = attr(tag, "name");
            String value = attr(tag, "value");
            if (name != null && !name.isEmpty()) {
                if (sb.length() > 0) sb.append('&');
                sb.append(URLEncoder.encode(name, "UTF-8")).append('=').append(URLEncoder.encode(value == null ? "" : value, "UTF-8"));
            }
        }

        String q = sb.toString();
        if (!q.contains("confirm=")) {
            if (q.length() > 0) q += '&';
            q += "confirm=t";
        }
        return q;
    }

    String attr(String tag, String name) {
        Matcher m = Pattern.compile(name + "=['\\\"]([^'\\\"]*)['\\\"]", Pattern.CASE_INSENSITIVE).matcher(tag);
        return m.find() ? m.group(1) : null;
    }

    boolean looksLikeHtmlFile(File f) {
        byte[] b = new byte[256];
        try (InputStream in = new FileInputStream(f)) {
            int n = in.read(b);
            if (n <= 0) return true;
            String h = new String(b, 0, n).trim().toLowerCase(Locale.US);
            return h.startsWith("<!doctype") || h.startsWith("<html") || h.contains("<html");
        } catch (Exception ignored) {
            return false;
        }
    }

    void stream(HttpURLConnection cn, File out, boolean append, String movieId, long existing) throws Exception {
        try (InputStream in = new BufferedInputStream(cn.getInputStream());
             OutputStream os = new BufferedOutputStream(new FileOutputStream(out, append))) {
            byte[] buf = new byte[1024 * 128];
            int n;
            long downloaded = append ? existing : 0;
            long len = cn.getContentLengthLong();
            long total = len > 0 ? downloaded + len : -1;
            long last = 0;

            while ((n = in.read(buf)) != -1) {
                os.write(buf, 0, n);
                downloaded += n;
                if (total > 0) {
                    int pct = (int) Math.min(100, (downloaded * 100) / total);
                    progressMap.put(movieId, pct);
                    long now = System.currentTimeMillis();
                    if (now - last > 400) {
                        last = now;
                        refresh(movieId);
                    }
                }
            }
            os.flush();
        }

        if (out.length() < 1024) throw new IOException("Downloaded file is too small; Google may have returned an error page.");
        progressMap.put(movieId, 100);
        refresh(movieId);
    }

    String joinCookies(HttpURLConnection cn) {
        Map<String, List<String>> h = cn.getHeaderFields();
        List<String> cs = h.get("Set-Cookie");
        if (cs == null) return null;

        StringBuilder sb = new StringBuilder();
        for (String s : cs) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(s.split(";", 2)[0]);
        }
        return sb.toString();
    }

    String mergeCookies(String a, String b) {
        if (a == null || a.isEmpty()) return b;
        if (b == null || b.isEmpty()) return a;
        return a + "; " + b;
    }

    String readSmall(InputStream in) throws Exception {
        if (in == null) return "";

        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        String line;
        int limit = 0;
        while ((line = br.readLine()) != null && limit < 500000) {
            sb.append(line).append('\n');
            limit += line.length();
        }
        return sb.toString();
    }

    void copy(File a, File b) throws Exception {
        try (InputStream in = new FileInputStream(a);
             OutputStream out = new FileOutputStream(b)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        a.delete();
    }

    String formatBytes(long b) {
        if (b < 1024 * 1024) return (b / 1024) + " KB";
        return (b / (1024 * 1024)) + " MB";
    }

    void toast(String s) {
        ui.post(() -> Toast.makeText(c, s, Toast.LENGTH_LONG).show());
    }

    static class DownloadResponse {
        HttpURLConnection conn;
        String cookie, body, contentType, disposition;
        boolean isHtml, append;
        long contentLength = -1;
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView poster;
        TextView title, meta, progress;
        ProgressBar bar;
        Button play;
        ImageButton fav, down;

        VH(View v) {
            super(v);
            poster = v.findViewById(R.id.poster);
            title = v.findViewById(R.id.title);
            meta = v.findViewById(R.id.meta);
            progress = v.findViewById(R.id.progress);
            bar = v.findViewById(R.id.downloadProgress);
            play = v.findViewById(R.id.playBtn);
            fav = v.findViewById(R.id.favBtn);
            down = v.findViewById(R.id.downloadBtn);
        }
    }
}

