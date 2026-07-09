# Google Drive large file download fix

This version uses a custom downloader for public Google Drive links.

It now tries these steps:

1. Convert any Google Drive share link to a direct download URL.
2. Request the file.
3. If Google returns the page saying **Google Drive can't scan this file for viruses**, parse the **Download anyway** confirmation form.
4. Follow the generated `drive.usercontent.google.com/download` URL with the required hidden fields such as `id`, `export`, `confirm`, and `uuid`.
5. Save the file locally as `.part` first.
6. Rename it to the final local movie file after completion.
7. Play only from the local downloaded file.

## Recommended movie link format

You can paste either the normal shared link:

```json
"movie": "https://drive.google.com/file/d/FILE_ID/view?usp=sharing"
```

or the direct download link:

```json
"movie": "https://drive.google.com/uc?export=download&id=FILE_ID"
```

The app will normalize both formats.

## Important limitation

This works for public Google Drive files when Google shows the standard large-file confirmation page. If Google blocks the file because of quota/rate limiting, account restrictions, missing public sharing, or abuse checks, the app cannot bypass that.
