package com.driveflix.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    private RecyclerView rv;
    private MovieAdapter adapter;
    private TextView status;
    private EditText search;
    private LinearLayout cats;
    private SwipeRefreshLayout swipeRefresh;
    private ImageView heroPoster;
    private TextView heroTitle;
    private TextView heroSubtitle;
    private final ArrayList<Movie> all = new ArrayList<>();
    private String current = "All";
    private final Random random = new Random();
    private final Handler heroHandler = new Handler(Looper.getMainLooper());
    private Movie heroMovie;
    private final Runnable heroRotator = new Runnable() {
        @Override
        public void run() {
            rotateHero();
            heroHandler.postDelayed(this, 9000);
        }
    };


    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setStatusBarColor(getResources().getColor(R.color.bg));
        w.setNavigationBarColor(getResources().getColor(R.color.bg));
        setContentView(R.layout.activity_main);
        applySystemBarInsets();

        rv = findViewById(R.id.movieList);
        status = findViewById(R.id.statusText);
        search = findViewById(R.id.searchBox);
        cats = findViewById(R.id.categoryBar);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        heroPoster = findViewById(R.id.heroPoster);
        heroTitle = findViewById(R.id.heroTitle);
        heroSubtitle = findViewById(R.id.heroSubtitle);
        ImageButton settings = findViewById(R.id.settingsBtn);

        adapter = new MovieAdapter(this, this::open);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setHasFixedSize(false);
        rv.setItemAnimator(null); // prevents flashing while progress updates
        rv.setAdapter(adapter);

        settings.setOnClickListener(v -> libraryDialog());
        swipeRefresh.setOnRefreshListener(this::load);

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { filter(); }
            public void afterTextChanged(Editable e) {}
        });
        openLibrary();
    }

    private void applySystemBarInsets() {
        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        filter();
        scheduleHeroRotation();
    }

    @Override
    protected void onPause() {
        heroHandler.removeCallbacks(heroRotator);
        super.onPause();
    }

    private void sample() {
        status.setText("Tap Library and paste your public movies.json URL. Movies are downloaded first, then played offline with resume support.");
        String s = "{\"movies\":[{\"id\":\"sample\",\"title\":\"Sample Movie\",\"category\":\"Demo\",\"year\":\"2026\",\"movie\":\"https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4\",\"poster\":\"https://peach.blender.org/wp-content/uploads/title_anouncement.jpg\"}]}";
        parse(s, false);
    }

    private void libraryDialog() {
        final EditText input = new EditText(this);
        input.setHint("https://.../movies.json");
        input.setText(Prefs.library(this));
        new AlertDialog.Builder(this)
                .setTitle("Movie Library JSON URL")
                .setMessage("Paste a public raw movies.json URL. JSON needs title, poster and movie link.")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String newUrl = input.getText().toString().trim();
                    if (!newUrl.equals(Prefs.library(this))) Prefs.libraryCache(this, "");
                    Prefs.library(this, newUrl);
                    all.clear();
                    adapter.set(new ArrayList<Movie>());
                    openLibrary();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openLibrary() {
        if (Prefs.library(this).isEmpty()) {
            sample();
            return;
        }
        String cached = Prefs.libraryCache(this);
        if (!cached.isEmpty()) {
            parse(cached, false);
            status.setText(all.size() + " movies loaded from saved library. Refreshing...");
        }
        load();
    }

    private void load() {
        status.setText(all.isEmpty() ? "Loading library..." : "Refreshing library...");
        swipeRefresh.setRefreshing(true);
        new Thread(() -> {
            try {
                URL u = new URL(Prefs.library(this));
                HttpURLConnection cn = (HttpURLConnection) u.openConnection();
                cn.setConnectTimeout(15000);
                cn.setReadTimeout(20000);
                InputStream in = cn.getInputStream();
                String text = read(in);
                runOnUiThread(() -> parse(text, true));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    swipeRefresh.setRefreshing(false);
                    showLoadFailure(e);
                });
            }
        }).start();
    }

    private void showLoadFailure(Exception e) {
        if (!all.isEmpty()) {
            status.setText("Offline. Showing saved library.");
            return;
        }
        String cached = Prefs.libraryCache(this);
        if (!cached.isEmpty()) {
            parse(cached, false);
            status.setText("Offline. Showing saved library.");
        } else {
            status.setText("Failed to load library: " + e.getMessage());
        }
    }
    private String read(InputStream in) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private void parse(String text, boolean cacheOnSuccess) {
        try {
            JSONArray arr = new JSONObject(text).getJSONArray("movies");
            ArrayList<Movie> next = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) next.add(Movie.fromJson(arr.getJSONObject(i)));
            all.clear();
            all.addAll(next);
            buildCats();
            pickInitialHero();
            filter();
            if (cacheOnSuccess) Prefs.libraryCache(this, text);
            status.setText(all.size() + " movies loaded. Tap Download, then Play after completion.");
        } catch (Exception e) {
            if (all.isEmpty()) status.setText("Invalid movies.json: " + e.getMessage());
            else status.setText("Refresh failed. Showing saved library.");
        } finally {
            swipeRefresh.setRefreshing(false);
        }
    }

    private void pickInitialHero() {
        heroHandler.removeCallbacks(heroRotator);
        rotateHero();
        scheduleHeroRotation();
    }

    private void scheduleHeroRotation() {
        heroHandler.removeCallbacks(heroRotator);
        if (all.size() > 1) heroHandler.postDelayed(heroRotator, 9000);
    }

    private void rotateHero() {
        ArrayList<Movie> candidates = heroCandidates();
        if (candidates.isEmpty()) return;
        Movie next = candidates.get(random.nextInt(candidates.size()));
        if (candidates.size() > 1 && heroMovie != null) {
            int attempts = 0;
            while (next.id.equals(heroMovie.id) && attempts++ < 5) {
                next = candidates.get(random.nextInt(candidates.size()));
            }
        }
        updateHero(next);
    }

    private ArrayList<Movie> heroCandidates() {
        ArrayList<Movie> out = new ArrayList<>();
        for (Movie m : all) {
            String local = Prefs.local(this, m.id);
            if ((!local.isEmpty() && new File(local).exists()) || Prefs.pos(this, m.id) > 0) out.add(m);
        }
        if (!out.isEmpty()) return out;
        for (Movie m : all) {
            if ("Trending".equalsIgnoreCase(m.category) || "Recently Added".equalsIgnoreCase(m.category)) out.add(m);
        }
        if (!out.isEmpty()) return out;
        out.addAll(all);
        return out;
    }

    private void updateHero(Movie m) {
        heroMovie = m;
        heroTitle.setText(m.title);
        String desc = m.description == null || m.description.isEmpty()
                ? "Download first, watch offline, resume anytime."
                : m.description;
        heroSubtitle.setText(desc);
        if (!m.posterUrl().isEmpty()) {
            Glide.with(this).load(m.posterUrl()).centerCrop().into(heroPoster);
        } else {
            heroPoster.setImageResource(android.R.drawable.ic_media_play);
        }
        findViewById(R.id.heroCard).setOnClickListener(v -> open(m));
    }

    private void buildCats() {
        cats.removeAllViews();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add("All");
        set.add("Continue");
        set.add("Favorites");
        set.add("Downloads");
        for (Movie m : all) set.add(m.category);
        for (String c : set) {
            Button b = new Button(this);
            b.setText(c);
            b.setAllCaps(false);
            b.setTextSize(12);
            b.setGravity(Gravity.CENTER);
            b.setIncludeFontPadding(false);
            b.setMinHeight(0);
            b.setMinimumHeight(0);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setPadding(dp(12), 0, dp(12), 0);
            b.setTextColor(getResources().getColor(R.color.text));
            b.setBackgroundResource(R.drawable.chip_bg);
            b.setSelected(c.equals(current));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(32)
            );
            lp.setMargins(0, 0, dp(8), 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                current = c;
                for (int i = 0; i < cats.getChildCount(); i++) cats.getChildAt(i).setSelected(false);
                v.setSelected(true);
                filter();
            });
            cats.addView(b);
        }
    }

    private void filter() {
        if (adapter == null) return;
        String q = search == null ? "" : search.getText().toString().toLowerCase(Locale.ROOT).trim();
        ArrayList<Movie> out = new ArrayList<>();
        for (Movie m : all) {
            boolean ok = m.title.toLowerCase(Locale.ROOT).contains(q)
                    || m.category.toLowerCase(Locale.ROOT).contains(q)
                    || (m.description != null && m.description.toLowerCase(Locale.ROOT).contains(q));
            if (!current.equals("All")) {
                if (current.equals("Favorites")) ok &= Prefs.fav(this, m.id);
                else if (current.equals("Continue")) ok &= Prefs.pos(this, m.id) > 0;
                else if (current.equals("Downloads")) {
                    String p = Prefs.local(this, m.id);
                    ok &= !p.isEmpty() && new File(p).exists();
                } else ok &= m.category.equals(current);
            }
            if (ok) out.add(m);
        }
        adapter.set(out);
    }

    private void open(Movie m) {
        String local = Prefs.local(this, m.id);
        if (local.isEmpty() || !new File(local).exists()) {
            Toast.makeText(this, "Please download the movie first.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("id", m.id);
        i.putExtra("title", m.title);
        i.putExtra("url", "file://" + local);
        i.putExtra("subtitle", m.subtitleUrl);
        startActivity(i);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
