# DriveFlix

DriveFlix is a native Android movie library app built around a public `movies.json` file. It reads movie metadata from a URL, shows a compact dark movie catalog, downloads movies locally, and plays them offline with Media3 / ExoPlayer.

## Features

- Public `movies.json` library support
- Google Drive movie, poster, and subtitle link support
- Download-first playback for reliable offline watching
- Offline library cache, so downloaded movies remain visible when internet is unavailable
- Resume interrupted downloads with partial `.part` files when the server supports range requests
- Google Drive large-file confirmation handling
- Download progress display
- Favorites, Continue Watching, and Downloads categories
- Resume playback position
- Subtitle support for `.srt` and `.vtt` links
- Compact dark UI with rotating hero card, search, category chips, and rounded movie cards
- Media3 / ExoPlayer playback
- Player gestures:
  - Left-side vertical swipe for brightness
  - Right-side vertical swipe for volume
  - Double tap left/right to seek 10 seconds
  - Pinch to zoom
  - Sleep timer
  - Playback speed controls
- GitHub Actions workflow for signed APK builds

## How It Works

1. Add a public `movies.json` URL from the Library button.
2. The app downloads and caches the movie library metadata.
3. Movie posters are loaded from public URLs or Google Drive file links.
4. Movies must be downloaded before playback.
5. Downloaded files are stored in the app's external files directory.
6. If internet is unavailable later, the cached library is shown so downloaded movies can still be opened.

## JSON Format

The root object must contain a `movies` array.

```json
{
  "version": 1,
  "lastUpdated": "2026-07-05T12:00:00Z",
  "appName": "DriveFlix",
  "categories": ["Trending", "Action", "Drama", "Horror"],
  "movies": [
    {
      "id": "sample_movie",
      "title": "Sample Movie",
      "year": 2026,
      "category": "Action",
      "genres": ["Action"],
      "description": "Short movie description.",
      "duration": 10140,
      "poster": "https://drive.google.com/file/d/POSTER_FILE_ID/view",
      "movie": "https://drive.usercontent.google.com/download?id=MOVIE_FILE_ID",
      "subtitles": [
        {
          "language": "English",
          "url": "https://example.com/subtitles/sample.srt"
        }
      ]
    }
  ]
}
```

Supported movie URL fields:

- `movie`
- `movieUrl`
- `url`
- `fileId`

Supported poster formats:

- Normal public image URL
- Google Drive share URL
- Google Drive file ID

Supported subtitle formats:

- `subtitle`: single URL string
- `subtitleUrl`: single URL string
- `subtitles`: array of objects with a `url` field

## Build Online With GitHub Actions

Upload this project to GitHub, then run:

`Actions -> Build Signed DriveFlix APK -> Run workflow`

The signed APK will be available in the workflow artifacts.

## Signing Note

If GitHub signing secrets are not configured, the workflow generates a temporary release keystore and uploads it as an artifact.

Keep that keystore safe. Future app updates must be signed with the same keystore.

Recommended GitHub Secrets for production signing:

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

## Notes

- Google Drive files must be publicly accessible or accessible without login.
- Very large Drive files may still be affected by Google Drive limits or abuse/virus confirmation pages.
- Only include content you have the right to distribute or access.
