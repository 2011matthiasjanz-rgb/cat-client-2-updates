package dev.catclient2.launcher.home;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Bundled "what's new" list shown on the home screen - ships inside the launcher jar. */
public class NewsFeed {
    private static final Gson GSON = new Gson();

    private NewsFeed() {
    }

    public static List<NewsItem> list() {
        try (InputStream in = NewsFeed.class.getResourceAsStream("/home-content/news.json")) {
            if (in == null) return List.of();
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                List<NewsItem> items = GSON.fromJson(reader, new TypeToken<List<NewsItem>>() {}.getType());
                return items == null ? List.of() : items;
            }
        } catch (IOException e) {
            System.err.println("[cat-client] Could not read bundled news: " + e.getMessage());
            return List.of();
        }
    }
}
