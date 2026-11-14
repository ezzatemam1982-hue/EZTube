# ClipFlow — Comprehensive UI Features, Settings & Dialogs Inventory
> **Project:** ClipFlow (High-Performance Media Downloader for Android)  
> **Author / Developer:** Eng. Ezzat Emam (م/ عزت إمام)  
> **Package ID:** `com.clipflow.app`  
> **Base Core:** yt-dlp & FFmpeg Engine  

---

## 1. Navigation Drawer & Top App Bar

### 1.1 Navigation Drawer / Sidebar & Bottom Navigation
The application features a responsive navigation system: a bottom navigation bar for standard phone screens, an extended navigation drawer for tablets/large screens, and a dedicated "More" screen menu.

| Feature / Item Name | Location / Screen | What It Does (Summary) | Current Status & Policy |
| :--- | :--- | :--- | :--- |
| **Home (`homeFragment`)** | Bottom Nav / Nav Rail / Drawer | Navigates to the main search and content discovery feed. | **Visible / Active** |
| **Downloads (`historyFragment`)** | Bottom Nav / Nav Rail / Drawer | Opens downloaded media library and historical download logs. | **Visible / Active** |
| **More (`moreFragment`)** | Bottom Nav (Phones) | Opens the primary menu for settings, cookies, and app termination. | **Visible / Active** |
| **Download Queue (`downloadQueueMainFragment`)** | Bottom Nav / Large Screen Drawer | Direct shortcut to the active download queue and task manager. | **Visible / Active** |
| **Cookies Manager (`cookiesFragment`)** | Large Screen Drawer & "More" Menu | Launches in-app WebView for logging into platforms (YouTube, Facebook, etc.) to export session cookies. | **Visible / Critical Core** |
| **Settings (`activitySettings`)** | Large Screen Drawer & "More" Menu | Opens application configuration screen (`SettingsActivity`). | **Visible / Active** |
| **Exit App (`terminate`)** | Large Screen Drawer & "More" Menu | Pauses active downloads, releases PoToken background server, safely terminates app. | **Visible / Active** |
| **Terminal (`terminalActivity`)** | Large Screen Drawer & "More" Menu | Interactive CLI shell for executing manual yt-dlp commands. | **Hidden** (Developer Tool) |
| **Logs (`downloadLogListFragment`)** | Large Screen Drawer & "More" Menu | Diagnostic execution logs and crash reporting for troubleshooting. | **Hidden** (Developer Tool) |
| **Command Templates (`commandTemplatesFragment`)** | Large Screen Drawer & "More" Menu | Library of saved custom CLI command templates. | **Hidden** (Developer Tool) |
| **Source Monitoring (`observeSourcesFragment`)** | Large Screen Drawer & "More" Menu | Periodic channel observer that automatically downloads newly released videos. | **Hidden** (Resource Intensive) |

---

### 1.2 Top App Bars & Action Menus

| Feature / Item Name | Location / Screen | What It Does (Summary) | Current Status |
| :--- | :--- | :--- | :--- |
| **App Branding Title** | Home Screen Top Bar | Displays application branding title ("ClipFlow"). | **Visible ("ClipFlow")** |
| **App Updates Badge (`app_updates`)** | Home Screen Top Bar | Alerts when a newer release of ClipFlow or yt-dlp is available. | **Dynamic** (Linked to custom repo) |
| **Failed Fetches Icon (`failed_fetches`)** | Home Screen Top Bar | Alerts on network/metadata fetch failures; opens retry sheet. | **Dynamic** |
| **Search Bar (`search_bar`)** | Home Screen Header | Primary input for YouTube/URL search; opens expanded search overlay. | **Visible / Active** |
| **Clear Results (`delete_results`)** | Search Bar Menu | Clears all currently displayed search result cards. | **Visible in Menu** |
| **Clear Search History (`delete_search`)** | Search Bar Menu | Purges stored search queries and recent keyword history. | **Visible in Menu** |
| **Copy URLs (`copy_urls`)** | Search Bar Menu | Copies URLs of all loaded search results to clipboard. | **Visible in Menu** |
| **Select Range (`select_range`)** | Search Bar Menu | Allows batch selection between two checked items. | **Active on Selection** |
| **Search History (`search_history`)** | Downloads / History Top Bar | Search downloaded media by title or author name. | **Visible / Active** |
| **History Filters (`filters`)** | Downloads / History Top Bar | Opens filter sheet (Filter by Video, Audio, Platform, Date, Duration). | **Visible / Active** |
| **History Queue Shortcut (`download_queue`)** | Downloads / History Top Bar | Quick switch to the active download queue. | **Visible / Active** |
| **Remove All History (`remove_history`)** | Downloads Top Bar Menu | Clears the entire download history database. | **Visible in Menu** |
| **Remove Deleted Files (`remove_deleted_history`)** | Downloads Top Bar Menu | Purges records whose underlying files were deleted from storage. | **Visible in Menu** |
| **Remove Duplicates (`remove_duplicates`)** | Downloads Top Bar Menu | Removes repeated entries of identical URLs from history. | **Visible in Menu** |
| **Queue Clear Active (`clear_queue`)** | Queue Top Bar Menu | Cancels and terminates running downloads. | **Visible in Menu** |
| **Queue Clear All (`clear_all`)** | Queue Top Bar Menu | Clears queued, scheduled, errored, and completed task records. | **Visible in Menu** |
| **Settings Search (`search_view`)** | Settings Top Bar | Instant search across all preferences and configuration switches. | **Visible / Active** |

---

## 2. Download Customization Bottom Sheet & Sub-Dialogs

### 2.1 Sheet Header & Global Controls
Presented when tapping a video or pressing "Download" from clipboard/share sheet:

| Feature / Item Name | Location / Screen | What It Does (Summary) | Current Default State |
| :--- | :--- | :--- | :--- |
| **Media Title (`title_textinput`)** | Top of Sheet | Editable text field to customize file name prior to downloading. | Pre-filled with remote title |
| **Author / Uploader (`author_textinput`)** | Top of Sheet | Editable text field to customize artist/channel name metadata. | Pre-filled with channel name |
| **Container Selector (`downloadContainer`)** | Top of Sheet | Dropdown to choose media container (MP4, MKV, WebM, MP3, M4A, FLAC, Opus). | Matches user default |
| **Quality / Format Item (`format_item`)** | Middle of Sheet | Displays resolution/bitrate; opens full format selector sheet. | Best Available |
| **Output Directory (`outputPath`)** | Middle of Sheet | Target folder where the media file will be written. | `Download/ClipFlow` |
| **Available Storage (`freespace`)** | Middle of Sheet | Displays remaining free disk space on storage partition. | Dynamic display |
| **Schedule Download (`bottomsheet_schedule_button`)** | Top Right Header | Date & Time picker to queue download for off-peak hours. | Unscheduled (Immediate) |
| **Incognito Toggle (`bottomsheet_incognito`)** | Bottom Sheet Footer | When enabled, prevents download from logging into history database. | Disabled (Off) |
| **Source URL Link (`bottom_sheet_link`)** | Bottom Sheet Footer | Displays origin URL; clicking copies or previews the web link. | Visible |
| **Start Download Button (`bottomsheet_download_button`)** | Top Right Header | Commences processing and queues download task. | Active |

---

### 2.2 Video Tab Options & Sub-Dialogs

| Option / Chip Name | Icon | Sub-Dialog / Control Type | What It Controls | Current Default State |
| :--- | :--- | :--- | :--- | :--- |
| **Thumbnail (`thumbnail`)** | `ic_image` | Multi-toggle dialog | • Embed thumbnail: Injects high-res cover art into MP4/MKV.<br>• Save thumbnail: Saves separate image file (.jpg/.webp). | Embed: `True`<br>Save: `False` |
| **Chapters (`chapters`)** | `ic_chapters` | Multi-toggle dialog | • Add chapters: Injects chapter markers into container.<br>• Split by chapters: Splits video into individual numbered files. | Add: `True`<br>Split: `False` |
| **Subtitles (`adjust_subtitles`)** | `ic_subtitles` | Configuration dialog | • Embed subtitles into video container.<br>• Save external `.srt` / `.vtt` file.<br>• Save auto-generated speech-to-text subtitles.<br>• Burn subtitles permanently into video frames.<br>• Select subtitle language codes (`ar`, `en`, `all`). | Embed: `True`<br>Save: `False`<br>Auto: `False`<br>Burn: `False` |
| **Remove Audio (`remove_audio`)** | `baseline_delete` | Instant toggle chip | Completely strips audio tracks, downloading silent video. | `False` (Off) |
| **Audio Controls (`adjust_audio`)** | `ic_music` | Dialog | • Remove audio track.<br>• Also download audio: Extracts standalone MP3 alongside video. | Remove: `False`<br>Also Audio: `False` |
| **Recode Video (`recode_video`)** | `baseline_video_metadata` | Multi-toggle dialog | • Recode video: Forces re-encoding via FFmpeg.<br>• Compatibility mode: Re-encodes to H.264 / AAC for legacy players. | Recode: `False`<br>Compat: `False` |
| **Live Stream (`adjust_live_stream`)** | `baseline_live_tv` | Dialog | • Live from start: Downloads from beginning rather than live edge.<br>• Wait for video: Waits and polls for scheduled premiere.<br>• Retry interval frequency. | From start: `False`<br>Wait: `False` |
| **SponsorBlock (`sponsorblock_filters`)** | `ic_money` | Multi-selection list | Strips promotional segments via SponsorBlock API:<br>Sponsors, Self-promotion, Interaction reminders, Intros/Outros. | Default: `music_offtopic` |
| **Filename Template (`filename_template`)** | `ic_edit` | Template editing dialog | Custom output naming syntax using format variables. | `%(uploader).30B - %(title).170B` |
| **Extra Commands (`extra_commands`)** | `ic_terminal` | Bottom sheet | Injects custom CLI switches directly into yt-dlp execution pipeline. | **Hidden / Disabled** |
| **Cut / Trim (`cut`)** | `ic_cut` | Interactive video player sheet | Video trimming tool with timeline scrubbers, multi-segment cutting, and preview. | Whole video (No cut) |
| **Crop (`crop`)** | `ic_crop` | Interactive cropping sheet | Visual aspect ratio cropper (16:9, 4:3, 1:1, 9:16), flip, and 90° rotation. | Original aspect ratio |

---

### 2.3 Audio Tab Options & Sub-Dialogs

| Option / Chip Name | Icon | Sub-Dialog / Control Type | What It Controls | Current Default State |
| :--- | :--- | :--- | :--- | :--- |
| **Thumbnail (`thumbnail`)** | `ic_image` | Multi-toggle dialog | • Embed thumbnail into ID3/Vorbis tags.<br>• Save thumbnail as standalone image. | Embed: `True`<br>Save: `False` |
| **Lyrics (`lyrics`)** | `ic_lyrics` | Configuration dialog | • Embed synced/plain lyrics from LRCLIB into audio tags.<br>• Save standalone `.lrc` file beside audio. | Embed: `False`<br>Save `.lrc`: `False` |
| **Split by Chapters (`split_by_chapters`)** | Filter Chip | Instant toggle chip | Automatically slices full audio album/podcast into individual tracks. | `False` (Off) |
| **Bitrate (`audio_bitrate`)** | `baseline_high_quality` | Single-choice selector | Sets audio compression bitrate (320kbps, 256kbps, 192kbps, 128kbps, VBR). | Best / 320k |
| **Cut / Trim (`cut`)** | `ic_cut` | Audio timeline cutter | Trims audio duration between specified start and end timestamps. | Full track |
| **SponsorBlock (`sponsorblock_filters`)** | `ic_money` | Multi-selection list | Removes spoken ads, non-music sections, and sponsor segments from audio. | Default: `music_offtopic` |
| **Filename Template (`filename_template`)** | `ic_edit` | Template editing dialog | Custom filename syntax for music tracks (`%(artist)s - %(title)s`). | Default pattern |
| **Extra Commands (`extra_commands`)** | `ic_terminal` | Bottom sheet | Custom yt-dlp arguments specific to audio tasks. | **Hidden / Disabled** |

---

### 2.4 Command Tab Options
* **New Template (`newTemplate`):** Builds and saves a new reusable yt-dlp template. (*Hidden for normal users*)
* **Edit Selected (`editSelected`):** Edits CLI arguments of highlighted template. (*Hidden*)
* **Shortcuts (`shortcut`):** Pins command template as a home screen shortcut. (*Hidden*)

---

## 3. Settings Screens Hierarchy

### 3.1 General Settings (`general_preferences.xml`)
* **Appearance & Theming:**
  * `Language`: Opens system or in-app language picker.
  * `Theme`: Choose app theme (System, Light, Dark).
  * `App Icon`: Choose between launcher icon styles.
  * `Accent Color`: Palette accent (Blue, Green, Purple, Dynamic Monet).
  * `High Contrast`: Pure AMOLED black background mode.
  * `Hide Thumbnails`: Disables image thumbnails to save mobile data.
  * `Edit Download Card`: **Hidden** (Locked to standard layout).
  * `Display Over Other Apps`: **Hidden** (Disabled).
* **YouTube Specific:**
  * `Recommendations`: **Hidden / Disabled** (Keeps home screen clean).
  * `Custom Recommendations URL`: **Hidden**.
  * `API Key`: **Hidden**.
  * `Data Fetching Extractor`: **Hidden** (Permanently locked to `yt-dlp`).
  * `Format Source`: **Hidden** (Permanently locked to `yt-dlp`).
  * `Use Item URL over Playlist`: Set to `false` (Prompts dialog asking whether to download single item or playlist).
* **Miscellaneous & Behavior:**
  * `History Quick Action`: Tap action on history card (Share, Play, Open folder).
  * `Preferred Search Engine`: Default query engine (**YouTube**).
  * `Check Clipboard on Home`: Automatically detects copied video URLs (**Active**).
  * `Search Suggestions`: Shows real-time auto-complete suggestions while typing (**Active**).
  * `Swipe Gestures`: Swipe actions on list items (Delete, Share, Redownload).
  * `Swipe Download Card`: Dismiss download bottom sheet by swiping down (**Active**).
  * `Show Terminal in Share`: **Hidden** (`false`).
  * `Show Download Count`: Displays total downloaded items badge (**Active**).
  * `Auto Write yt-dlp in Terminal`: **Hidden** (`true`).

---

### 3.2 Directories & Storage Settings (`folders_preference.xml`)
* `Audio Folder`: `Download/ClipFlow/Audio`
* `Video Folder`: `Download/ClipFlow/Video`
* `Custom Command Folder`: `Download/ClipFlow/Command`
* `Cache Directory`: Internal app cache for chunk staging.
* `Check Available Storage`: Verifies disk space before initiating download (**Enabled**).
* `Cache First`: Downloads raw fragments to cache before final muxing (**Locked True**).
* `No Download Fragments (.part)`: Default `false` (Uses `.part` safely).
* `Keep Cache`: Purges intermediate cached fragments upon completion.
* `Save in Subdirectory`: Option to organize media into subfolders by Channel/Playlist.
* `Trim Filenames`: Truncates long titles to prevent file system errors (**Enabled**).

---

### 3.3 Downloader & Network Settings (`downloading_preferences.xml`)
* `Incognito Mode`: Disables logging downloads into local history (**Off**).
* `Show Download Card`: Shows customization sheet before downloading (**Enabled**).
* `Quick Download`: One-tap instant download using presets (**Off**).
* `Prevent Duplicate Downloads`: Prompts dialog if URL was previously downloaded.
* `Metered Networks`: Allows downloading over cellular data (**Enabled**).
* `Force IPv4`: Dual-stack default (can force IPv4 for broken IPv6 networks).
* `Use Cookies`: Attaches session cookies for member-only/age-restricted media.
* `SOCKS5 / HTTP Proxy`: Configures proxy routing for restricted countries.
* `Concurrent Fragments`: Number of fragments downloaded in parallel (Default: 1–3).
* `Concurrent Downloads`: Simultaneous active downloads queue limit (Default: 1–2).
* `aria2c Engine`: Optional multi-connection accelerator binary.
* `Scheduling`: Android WorkManager window for midnight downloading.

---

### 3.4 Processing & FFmpeg Settings (`processing_preferences.xml`)
* `Use SponsorBlock`: Enables SponsorBlock segment stripping (**Enabled**).
* `Embed Metadata`: Injects title, artist, year, and description tags (**Enabled**).
* `Crop Thumbnail`: Automatically crops 16:9 thumbnails into square album art (**Enabled**).
* `Embed Subtitles`: Automatically embeds subtitles into video container (**Enabled**).
* `Add Chapters`: Embeds chapter markers into video file (**Enabled**).
* `Video Compatibility Mode`: Re-encodes with H.264/AAC for legacy screens/editors (**Off by default**).
* `Recode Video`: Global video re-encoding switch (**Off by default**).

---

### 3.5 Backup, Updates & About Screens
* **Backup & Restore (`root_preferences.xml`):**
  * `Create Backup`: Generates zip archive containing cookies, settings, and history.
  * `Restore Backup`: Restores configurations from previously exported backup file.
  * `Auto Backup on Release`: **Hidden & Disabled** (Prevents dead-loop checks).
  * `Backup Path`: `Download/ClipFlow/Backups`.
* **About Screen (`root_preferences.xml`):**
  * `Package ID`: `com.clipflow.app`
  * `Developer Credit`: **Eng. Ezzat Emam** (English) / **م/ عزت إمام** (Arabic).
  * `Privacy Policy`: `🔐 ClipFlow - Privacy Policy`.
  * `License`: `GNU General Public License v3.0`.

---

## 4. Home Screen & Download Queue Inventory

### 4.1 Home Screen Elements
* **Search View & Engine Chips:** Instant selector between YouTube, YouTube Music, and SoundCloud (Default locked to YouTube).
* **Live Search Suggestions:** Real-time auto-complete dropdown populated while typing.
* **Clipboard Detection FAB:** Floating action button (`Link you copied`) popping up when a valid URL is copied.
* **Playlist Selection Chips:** Appears when a playlist URL is detected to filter loaded items.
* **Contextual Selection Actions:** Long-pressing any card enables batch actions: `Select Range`, `Select All`, `Invert Selection`, and `Download Selected`.

---

### 4.2 Download Queue & Task Manager
Managed through 5 dedicated status tabs:
1. **Queued / Active:** Live downloads with progress bar, download speed (MB/s), ETA, and file size.
2. **Scheduled:** Downloads scheduled for future dates or midnight scheduler.
3. **Cancelled:** Aborted downloads that can be resumed at any time.
4. **Errored:** Failed tasks with detailed error trace and one-tap retry button.
5. **Saved:** Completed downloads preserved for history and immediate playback.