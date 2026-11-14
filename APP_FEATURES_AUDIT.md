# ClipFlow — Comprehensive UI Features, Settings & Dialogs Inventory

This document provides an exhaustive, structured audit of all user interface components, settings, configuration dialogs, menus, and bottom sheets across the **ClipFlow** application.

---

## 1. Navigation Drawer & Top App Bar

### 1.1 Navigation Drawer / Sidebar & Bottom Navigation
The application features a responsive navigation system: a bottom navigation bar for standard phone screens, an extended navigation drawer for large screens/tablets, and the dedicated "More" screen menu.

| Feature / Item Name | Location / Screen | What It Does (Summary) | Current Default State |
| :--- | :--- | :--- | :--- |
| **Home (`homeFragment`)** | Bottom Nav / Nav Rail / Drawer | Navigates to the main search and content discovery feed. | **Visible / Active** |
| **Downloads (`historyFragment`)** | Bottom Nav / Nav Rail / Drawer | Opens the downloaded media library and download history. | **Visible** |
| **More (`moreFragment`)** | Bottom Nav (Phones) | Opens the primary options menu for settings, cookies, and app termination. | **Visible** |
| **Download Queue (`downloadQueueMainFragment`)** | Bottom Nav / Large Screen Drawer | Direct shortcut to the active download queue and task manager. | **Visible** |
| **Cookies Manager (`cookiesFragment`)** | Large Screen Drawer & "More" Menu | Launches the in-app WebView for logging into platforms (YouTube, etc.) to export session cookies. | **Visible** |
| **Settings (`activitySettings` / `settings`)** | Large Screen Drawer & "More" Menu | Opens the comprehensive application configuration screen (`SettingsActivity`). | **Visible** |
| **Exit App (`terminate`)** | Large Screen Drawer & "More" Menu | Pauses all active downloads, releases PoToken background server, and safely closes the application. | **Visible** |
| **Terminal (`terminalActivity`)** | Large Screen Drawer & "More" Menu | Interactive shell/CLI interface for running manual `yt-dlp` commands. | **Hidden** (Developer tool) |
| **Logs (`downloadLogListFragment`)** | Large Screen Drawer & "More" Menu | Diagnostic execution logs and crash reporting for troubleshooting downloads. | **Hidden** (Developer tool) |
| **Command Templates (`commandTemplatesFragment`)** | Large Screen Drawer & "More" Menu | Library of saved custom CLI command templates. | **Hidden** (Developer tool) |
| **Source Monitoring (`observeSourcesFragment`)** | Large Screen Drawer & "More" Menu | Periodic channel/playlist observer that automatically downloads newly released videos. | **Hidden** (Advanced tool) |

---

### 1.2 Top App Bars & Action Menus

| Feature / Item Name | Location / Screen | What It Does (Summary) | Current Default State |
| :--- | :--- | :--- | :--- |
| **App Branding Title** | Home Screen Top Bar | Displays the application name ("ClipFlow"). | **Visible** ("ClipFlow") |
| **App Updates Badge (`app_updates`)** | Home Screen Top Bar | Alerts when a newer release of ClipFlow or yt-dlp is available. | **Visible dynamically** (Hidden when up-to-date) |
| **Failed Fetches Icon (`failed_fetches`)** | Home Screen Top Bar | Indicates failed network/metadata fetch queries; opens the retry bottom sheet. | **Visible dynamically** (Hidden if none failed) |
| **Search Bar (`search_bar`)** | Home Screen Header | Primary input for YouTube/URL search; opens the expanded search overlay. | **Visible** (Hint: "Search or insert URL") |
| **Clear Results (`delete_results`)** | Home Screen Search Bar Menu | Clears all currently displayed search result cards from the feed. | **Visible in menu** |
| **Clear Search History (`delete_search`)** | Home Screen Search Bar Menu | Purges stored search queries and recent keyword history. | **Visible in menu** |
| **Copy URLs (`copy_urls`)** | Home Screen Search Bar Menu | Copies URLs of all currently loaded results to the system clipboard. | **Visible in menu** |
| **Select Range (`select_range`)** | Home Screen Search Bar Menu | Allows batch selection between two checked items. | **Hidden** (Active during selection) |
| **Search History (`search_history`)** | Downloads / History Top Bar | Search through downloaded media by title or author. | **Visible** |
| **History Filters (`filters`)** | Downloads / History Top Bar | Opens filter bottom sheet (Filter by Video, Audio, Platform, Date, Duration). | **Visible** |
| **History Queue Shortcut (`download_queue`)**| Downloads / History Top Bar | Quick switch to the active download queue. | **Visible** |
| **Remove All History (`remove_history`)** | Downloads / History Top Bar Menu | Clears the entire download history database. | **Visible in menu** |
| **Remove Deleted Files (`remove_deleted_history`)** | Downloads / History Top Bar Menu | Cleans up history entries whose underlying files no longer exist on storage. | **Visible in menu** |
| **Remove Duplicates (`remove_duplicates`)** | Downloads / History Top Bar Menu | Removes repeated entries of identical URLs from history. | **Visible in menu** |
| **Queue Clear Active (`clear_queue`)** | Download Queue Top Bar Menu | Cancels and removes all currently running downloads. | **Visible in menu** |
| **Queue Clear All (`clear_all`)** | Download Queue Top Bar Menu | Clears queued, scheduled, errored, and completed queue records. | **Visible in menu** |
| **Settings Search (`search_view`)** | Settings Screen Top Bar | Instant in-app search for finding any preference or toggle across all settings. | **Visible** |

---

## 2. Download Customization Bottom Sheet & Sub-Dialogs

When clicking a video/playlist or tapping "Download", the Download Customization Bottom Sheet is presented. It provides 3 primary tabs: **Audio**, **Video**, and **Command**.

### 2.1 Sheet Header & Global Controls
| Feature / Item Name | Location / Screen | What It Does (Summary) | Current Default State |
| :--- | :--- | :--- | :--- |
| **Media Title (`title_textinput`)** | Top of Sheet | Editable text field to modify the output file title before downloading. | Pre-filled with remote title |
| **Author / Uploader (`author_textinput`)**| Top of Sheet | Editable text field to set or override artist/channel name metadata. | Pre-filled with channel name |
| **Container Selector (`downloadContainer`)**| Top of Sheet | Dropdown to choose container format (MP4, MKV, WebM, MP3, M4A, FLAC, Opus, etc.). | Matches preferred default |
| **Quality / Format Item (`format_item`)** | Middle of Sheet | Displays selected video resolution/bitrate; opens full format selector sheet. | Best Available |
| **Output Directory (`outputPath`)** | Middle of Sheet | Specifies target folder where the file will be saved. | `Download/ClipFlow` |
| **Available Storage (`freespace`)** | Middle of Sheet | Displays current free disk space on the target partition (e.g. "45.2 GB free"). | **Visible** |
| **Schedule Download (`bottomsheet_schedule_button`)** | Top Right Header | Opens Date & Time picker to queue the download for off-peak/night hours. | Unscheduled (Immediate) |
| **Incognito Toggle (`bottomsheet_incognito`)** | Bottom Sheet Footer | When enabled, prevents this download from being logged in the history database. | **Disabled** (Off) |
| **Source URL Link (`bottom_sheet_link`)** | Bottom Sheet Footer | Displays origin domain/URL; clicking copies or previews the web link. | **Visible** |
| **Start Download Button (`bottomsheet_download_button`)** | Top Right Header | Commences processing and queues the download task. | **Active** |

---

### 2.2 Video Tab Options & Sub-Dialogs

| Option / Chip Name | Icon | Sub-Dialog / Control Type | What It Controls | Current Default State |
| :--- | :--- | :--- | :--- | :--- |
| **Thumbnail (`thumbnail`)** | `ic_image` | Multi-toggle dialog (`video_thumbnail_download_preferences_dialog.xml`) | • **Embed thumbnail**: Writes high-res cover art into MP4/MKV.<br>• **Save thumbnail**: Saves separate image file (`.jpg`/`.webp`). | • Embed: **True**<br>• Save: **False** |
| **Chapters (`chapters`)** | `ic_chapters` | Multi-toggle dialog (`video_chapter_download_preferences_dialog.xml`) | • **Add chapters**: Injects chapter markers into the video container.<br>• **Split by chapters**: Splits video into individual numbered files per chapter. | • Add: **True**<br>• Split: **False** |
| **Subtitles (`adjust_subtitles`)** | `ic_subtitles` | Configuration dialog (`subtitle_download_preferences_dialog.xml`) | • **Embed subtitles**: Muxes soft subtitles into container.<br>• **Save subtitles**: Exports external `.srt`/`.vtt` file.<br>• **Save auto subtitles**: Downloads automatic speech-to-text subs.<br>• **Burn subtitles**: Hardcodes subs permanently onto the video frames.<br>• **Subtitle languages**: Select specific language codes (e.g. `en`, `ar`, `all`). | • Embed: **True**<br>• Save: **False**<br>• Auto: **False**<br>• Burn: **False**<br>• Lang: `.*-orig` |
| **Remove Audio (`remove_audio`)** | `baseline_delete` | Instant toggle chip | Completely strips audio tracks, downloading video-only stream. | **False** (Off) |
| **Audio Controls (`adjust_audio`)** | `ic_music` | Dialog (`audio_download_preferences_dialog.xml`) | • **Remove audio**: Drops audio track.<br>• **Also download audio**: Downloads standalone audio file alongside video. | • Remove: **False**<br>• Also audio: **False** |
| **Recode Video (`recode_video`)** | `baseline_video_metadata` | Multi-toggle dialog (`video_download_preferences_dialog.xml`) | • **Recode video**: Forces video re-encoding through FFmpeg.<br>• **Compatibility mode**: Encodes to universal H.264 / AAC for maximum device compatibility. | • Recode: **False**<br>• Compat: **False** |
| **Live Stream (`adjust_live_stream`)** | `baseline_live_tv_24` | Dialog (`livestream_download_preferences_dialog.xml`) | • **Live from start**: Downloads recorded stream from beginning instead of live edge.<br>• **Wait for video**: Waits and polls for scheduled premiere/stream to go live.<br>• **Retry interval**: Poll frequency in minutes. | • From start: **False**<br>• Wait: **False**<br>• Interval: `1 min` |
| **SponsorBlock (`sponsorblock_filters`)** | `ic_money` | Multi-selection list dialog | Strips/skips segments via the SponsorBlock API:<br>• Music offtopic<br>• Sponsor segments<br>• Intro animation<br>• Outro / Endcards<br>• Self-promotion<br>• Preview / Recap<br>• Filler / Tangent<br>• Interaction reminder<br>• Hook / Greetings | Default: `music_offtopic` |
| **Filename Template (`filename_template`)** | `ic_edit` | Template editing dialog | Custom output naming syntax using yt-dlp format tags (e.g., `%(uploader)s - %(title)s.%(ext)s`). | `%(uploader).30B - %(title).170B` |
| **Extra Commands (`extra_commands`)** | `ic_terminal` | Bottom sheet (`extra_commands_bottom_sheet.xml`) | Injects custom CLI switches/arguments directly into the yt-dlp execution pipeline. | Empty (Disabled) |
| **Cut / Trim (`cut`)** | `ic_cut` | Interactive video player sheet (`cut_video_sheet.xml`) | Precision video trimming tool with timeline scrubbers, multi-segment cutting, timestamp adjusters, and preview. | Whole video (No cut) |
| **Crop (`crop`)** | `ic_crop` | Interactive cropping sheet (`crop_video_sheet.xml`) | Visual aspect ratio cropper (16:9, 4:3, 1:1, 9:16), horizontal/vertical flipping, and 90° rotation. | Original aspect ratio |

---

### 2.3 Audio Tab Options & Sub-Dialogs

| Option / Chip Name | Icon | Sub-Dialog / Control Type | What It Controls | Current Default State |
| :--- | :--- | :--- | :--- | :--- |
| **Thumbnail (`thumbnail`)** | `ic_image` | Multi-toggle dialog (`audio_thumbnail_download_preferences_dialog.xml`) | • **Embed thumbnail**: Embeds album artwork into ID3/Vorbis tags.<br>• **Save thumbnail**: Saves separate image file (`.jpg`/`.png`). | • Embed: **True**<br>• Save: **False** |
| **Lyrics (`lyrics`)** | `ic_lyrics` | Configuration dialog (`audio_lyrics_download_preferences_dialog.xml`) | • **Embed lyrics**: Fetches synced/plain lyrics from LRCLIB and embeds in file tags.<br>• **Save .lrc file**: Writes synced `.lrc` file beside the audio track. | • Embed: **False**<br>• Save .lrc: **False** |
| **Split by Chapters (`split_by_chapters`)** | Filter Chip | Instant toggle chip | Automatically slices full audio release/podcast into individual tracks based on timestamp chapters. | **False** (Off) |
| **Bitrate (`audio_bitrate`)** | `baseline_high_quality_24` | Single-choice dialog selector | Sets audio compression bitrate (320kbps, 256kbps, 192kbps, 128kbps, or VBR). | Best / 320k |
| **Cut / Trim (`cut`)** | `ic_cut` | Audio timeline cutter | Trims audio file duration between start and end timestamps. | Full track |
| **SponsorBlock (`sponsorblock_filters`)** | `ic_money` | Multi-selection list dialog | Removes spoken ads, non-music sections, and sponsor segments from audio. | Default: `music_offtopic` |
| **Filename Template (`filename_template`)** | `ic_edit` | Template editing dialog | Custom filename syntax for music tracks (e.g. `%(artist)s - %(title)s`). | `%(uploader).30B - %(title).170B` |
| **Extra Commands (`extra_commands`)** | `ic_terminal` | Bottom sheet | Custom yt-dlp arguments specific to this audio task. | Empty (Disabled) |

---

### 2.4 Command Tab Options

| Option / Chip Name | Icon | Sub-Dialog / Control Type | What It Controls | Current Default State |
| :--- | :--- | :--- | :--- | :--- |
| **New Template (`newTemplate`)** | `ic_plus` | Template creator dialog | Builds and saves a new reusable yt-dlp execution template. | N/A |
| **Edit Selected (`editSelected`)** | `ic_edit` | Template editor dialog | Edits arguments of the currently highlighted template. | N/A |
| **Shortcuts (`shortcut`)** | `ic_shortcut` | Template shortcuts manager | Pins command template as a home screen shortcut or quick launcher. | N/A |

---

## 3. Settings Screens Hierarchy

The Settings screen is accessed via "More" > "Settings". It organizes all application behavior into dedicated categories:

```
Settings
├── General (Appearance, Theming, Search, Behaviors)
├── Folders (Storage directories, Naming rules, Cache, Temporary files)
├── Downloads (Network limits, Concurrency, Throttling, Scheduler)
├── Processing (SponsorBlock, Video, Audio, Subtitles, Codecs, FFmpeg)
├── Updating (yt-dlp channels, App updates, Package managers)
├── Advanced (PO Tokens, Extraction arguments, InfoJSON, Certificates)
├── Backup & Restore (Create/Restore backup, Backup path)
└── About (Version, Package ID, Developer credits, License, Privacy Policy)
```

---

### 3.1 General Settings (`general_preferences.xml`)

#### Appearance & Theming
| Preference Item | Key | Type | Description | Default State |
| :--- | :--- | :--- | :--- | :--- |
| **Language** | `app_language` | Preference | Opens system app language selector (Android 13+) or in-app picker. | System Default |
| **Theme** | `ytdlnis_theme` | ListPreference | Choose app theme (`System`, `Light`, `Dark`). | `System` |
| **App Icon** | `ytdlnis_icon` | Preference | Choose between launcher icon styles. | `default` |
| **Accent Color** | `theme_accent` | ListPreference | Palette accent (`Blue`, `Red`, `Green`, `Purple`, `Monet/Dynamic`, etc.). | `Blue` |
| **High Contrast** | `high_contrast` | SwitchPreference | Pure AMOLED black background mode. | `false` (Off) |
| **Hide Thumbnails** | `hide_thumbnails` | MultiSelectList | Disables image thumbnails in Search, History, or Queue to save mobile data. | None (Images shown) |
| **Edit Download Card** | `modify_download_card` | MultiSelectList | Reorder or toggle card layout chips. | **Hidden** (Locked to default) |
| **Display Over Other Apps** | `display_over_apps` | SwitchPreference | Floating mini download overlay. | **Hidden** (`false`) |

#### YouTube Specific
| Preference Item | Key | Type | Description | Default State |
| :--- | :--- | :--- | :--- | :--- |
| **Recommendations** | `recommendations_home` | ListPreference | Trending / home recommendations source. | **Hidden** (`""` / Disabled) |
| **Custom Recommendations URL** | `custom_home_recommendation_url` | EditText | Custom RSS/Invidious feed URL. | **Hidden** (`""`) |
| **API Key** | `api_key` | EditText | Custom YouTube Data API v3 key. | **Hidden** (`""`) |
| **Data Fetching Extractor** | `youtube_data_fetching_extractor` | ListPreference | Engine used to fetch metadata (`yt-dlp` vs innerTube). | **Hidden** (Locked to `YT_DLP`) |
| **Format Source** | `formats_source` | ListPreference | Stream format source extractor. | **Hidden** (Locked to `yt-dlp`) |
| **Use Item URL over Playlist** | `use_itemurl_instead_playlisturl` | SwitchPreference | Downloads only single video when sharing a video link that belongs to a playlist. | `false` (Prompts dialog) |

#### Miscellaneous & Behavior
| Preference Item | Key | Type | Description | Default State |
| :--- | :--- | :--- | :--- | :--- |
| **History Quick Action** | `history_quick_action` | ListPreference | Tap action on history card (`Share`, `Play`, `Open folder`). | `share` |
| **Preferred Locale** | `locale` | ListPreference | Country locale for geographic extraction. | **Hidden** (Locked default) |
| **Preferred Home Screen** | `start_destination` | Preference | Sets initial screen on launch. | **Hidden** (Locked to `Home`) |
| **Navigation Bar Items** | `navigation_bar` | Preference | Customize bottom navigation tabs. | **Hidden** (Locked default) |
| **Label Visibility** | `label_visibility` | ListPreference | Navigation bar labels (`Always`, `Selected`, `Unlabeled`). | **Hidden** (Locked `always`) |
| **Preferred Search Engine** | `search_engine` | ListPreference | Default engine for queries (`ytsearch`, `ytmsearch`, `scsearch`). | `ytsearch` (YouTube) |
| **Check Clipboard on Home** | `check_clipboard_home` | SwitchPreference | Automatically detects video URLs in clipboard and displays the "Link you copied" FAB. | `true` (Enabled) |
| **Search Suggestions** | `search_suggestions` | SwitchPreference | Shows real-time search auto-complete suggestions while typing. | `true` (Enabled) |
| **Swipe Gestures** | `swipe_gesture` | MultiSelectList | Swipe actions on list items (Delete, Share, Redownload). | All enabled |
| **Swipe Download Card** | `swipe_gestures_download_card` | SwitchPreference | Dismiss download bottom sheet by swiping down. | `true` (Enabled) |
| **Show Terminal in Share** | `show_terminal` | SwitchPreference | Adds direct "Open in Terminal" button to share sheet. | **Hidden** (`false`) |
| **Show Download Count** | `show_count_downloads` | SwitchPreference | Displays total number of downloaded items in header badges. | `true` (Enabled) |
| **Auto Write yt-dlp in Terminal**| `write_ytdlp_terminal` | SwitchPreference | Automatically types yt-dlp command in terminal. | **Hidden** (`true`) |
| **Ignore Battery Optimization** | `ignore_battery` | Preference | Requests exemption from Android battery throttling for uninterrupted downloads. | Prompt on tap |
| **Reset Preferences** | `reset_preferences` | Preference | Resets all settings in this section to factory defaults. | N/A |

---

### 3.2 Directories & Storage Settings (`folders_preference.xml`)

| Preference Item | Key | Type | Description | Default State |
| :--- | :--- | :--- | :--- | :--- |
| **Audio Folder** | `music_path` | Preference | Target storage directory for downloaded audio files. | `Download/ClipFlow/Audio` |
| **Video Folder** | `video_path` | Preference | Target storage directory for downloaded video files. | `Download/ClipFlow/Video` |
| **Custom Command Folder** | `command_path` | Preference | Directory for files generated by custom command templates. | `Download/ClipFlow/Command` |
| **Cache Directory** | `cache_path` | Preference | Storage folder for temporary chunks, fragments, and muxing files. | App Internal Cache |
| **Access All Directories** | `access_all_files` | Preference | Requests `MANAGE_EXTERNAL_STORAGE` permission on Android 11+. | System permission check |
| **Check Available Storage** | `check_available_storage`| SwitchPreference | Verifies available free storage before starting download tasks. | `true` (Enabled) |
| **Cache First** | `cache_downloads` | SwitchPreference | Downloads complete raw fragments to cache before writing to final storage. | `true` (Locked) |
| **No Download Fragments (.part)**| `no_part` | SwitchPreference | Writes directly to target file without creating temporary `.part` files. | `false` (Uses `.part`) |
| **Keep Cache** | `keep_cache` | SwitchPreference | Retains intermediate cached files after download completion. | `false` (Purges cache) |
| **Filename Template (Video)** | `file_name_template` | Preference | Naming pattern for saved video files. | `%(uploader).30B - %(title).170B` |
| **Filename Template (Audio)** | `file_name_template_audio`| Preference | Naming pattern for saved audio files. | `%(uploader).30B - %(title).170B` |
| **Save in Subdirectory** | `save_subdirectory` | MultiSelectList | Automatically organizes media into subfolders by Channel, Playlist, or Extractor. | None (Flat structure) |
| **Trim Filenames** | `trim_filenames` | SwitchPreference | Truncates excessively long titles to avoid OS file system limit errors. | `true` (Enabled) |
| **Restrict Filenames** | `restrict_filenames` | SwitchPreference | Replaces non-ASCII characters and spaces with underscores for compatibility. | `false` (Off) |
| **Temporary Files** | `temporary_files` | Preference | Opens sub-screen to inspect and clean leftover chunk files, infojsons, and cache. | Sub-screen |
| **Reset Preferences** | `reset_preferences` | Preference | Resets folder configurations to defaults. | N/A |

---

### 3.3 Downloader & Network Settings (`downloading_preferences.xml`)

#### Downloading Rules & Network
| Preference Item | Key | Type | Description | Default State |
| :--- | :--- | :--- | :--- | :--- |
| **Incognito Mode** | `incognito` | SwitchPreference | Globally disables logging downloads into local history. | `false` (Off) |
| **Show Download Card** | `download_card` | SwitchPreference | Shows customization bottom sheet before initiating downloads. | `true` (Enabled) |
| **Quick Download** | `quick_download` | SwitchPreference | One-tap instant download using default presets without opening bottom sheet. | `false` (Off) |
| **Prevent Duplicate Downloads** | `prevent_duplicate_downloads` | ListPreference | Action when URL was previously downloaded (`Prompt`, `Skip`, `Overwrite`, `None`). | `url_type` (Prompt dialog) |
| **Download Delay** | `download_delay` | Preference | Configures delay (e.g. 5–15 seconds) between downloads to prevent platform IP rate-limiting. | `0-0` (No delay) |
| **Download Archive Folder** | `download_archive_path` | Preference | Path to `.txt` archive tracking already-downloaded video IDs (`--download-archive`). | Default App Path |
| **Metered Networks** | `metered_networks` | SwitchPreference | Allows downloads over cellular / metered Wi-Fi connections. | `true` (Enabled) |
| **Force IPv4** | `force_ipv4` | SwitchPreference | Forces connections over IPv4, bypassing slow or broken IPv6 routing. | `false` (Dual-stack) |
| **Use Cookies** | `use_cookies` | SwitchPreference | Attaches exported session cookies to yt-dlp requests for age-restricted/member videos. | `false` (Off) |
| **SOCKS5 / HTTP Proxy** | `proxy` | EditText | Proxy address (e.g., `socks5://127.0.0.1:9050` or `http://proxy:8080`). | Empty (Direct) |
| **Preferred Download Type** | `preferred_download_type` | ListPreference | Default tab opened in bottom sheet (`Auto`, `Audio`, `Video`, `Command`). | `auto` |
| **Remember Download Type** | `remember_download_type` | SwitchPreference | Remembers the last selected tab as default for future downloads. | `false` (Off) |
| **Concurrent Fragments** | `concurrent_fragments` | SeekBar | Number of video fragments downloaded in parallel per file (`-N` flag). | `1` (Range: 1–25) |
| **Concurrent Downloads** | `concurrent_downloads` | SeekBar | Maximum number of simultaneous active download tasks. | `1` (Range: 1–10) |
| **Limit Rate** | `limit_rate` | EditText | Maximum download speed bandwidth cap (e.g., `500K` or `5M`). | Empty (Unlimited) |
| **Buffer Size** | `buffer_size` | EditText | Download buffer size (e.g., `16K` or `1M`). | Empty (Default) |
| **Socket Timeout** | `socket_timeout` | EditText | Network socket connection timeout in seconds. | Empty (Default ~30s) |
| **aria2c Engine** | `aria2` | SwitchPreference | Uses high-speed multi-connection `aria2c` downloader binary instead of native HTTP client. | `false` (Off) |
| **Log Downloads** | `log_downloads` | SwitchPreference | Writes detailed execution console output to internal log files. | `false` (Off) |
| **Cleanup Leftover Downloads**| `cleanup_leftover_downloads` | ListPreference | Automatically deletes abandoned partial downloads older than 1, 3, or 7 days. | `""` (Disabled) |
| **Network Retries** | `retries` | EditText | Number of times to retry failed network requests. | Empty (Default: 10) |
| **Fragment Retries** | `fragment_retries` | EditText | Number of times to retry individual broken stream fragments. | Empty (Default: 10) |

#### Scheduling
| Preference Item | Key | Type | Description | Default State |
| :--- | :--- | :--- | :--- | :--- |
| **Use Alarm Manager** | `use_alarm_for_scheduling` | SwitchPreference | Uses Android `AlarmManager` for exact-minute wakeups instead of background `WorkManager`. | `false` (WorkManager) |
| **Use Scheduler** | `use_scheduler` | SwitchPreference | Enables automatic download window (e.g., only download during midnight hours). | `false` (Off) |
| **Schedule Start Time** | `schedule_start` | Preference | Beginning of allowed download window. | `00:00` |
| **Schedule End Time** | `schedule_end` | Preference | End of allowed download window. | `05:00` |

---

### 3.4 Processing & FFmpeg Settings (`processing_preferences.xml`)

#### General Post-Processing
| Preference Item | Key | Type | Description | Default State |
| :--- | :--- | :--- | :--- | :--- |
| **Use SponsorBlock** | `use_sponsorblock` | SwitchPreference | Enables SponsorBlock segment stripping. | `true` (Enabled) |
| **SponsorBlock Filters** | `sponsorblock_filters` | MultiSelectList | Selects default categories to cut (Sponsors, intros, etc.). | `music_offtopic` |
| **SponsorBlock API URL** | `sponsorblock_url` | EditText | Custom mirror URL for the SponsorBlock API. | Default official API |
| **File Modification Time** | `mtime` | SwitchPreference | Sets local file creation date to match remote video upload date. | `false` (Current time) |
| **Write Description** | `write_description` | SwitchPreference | Saves full video description to a `.description` text file. | `false` (Off) |
| **Use Extra Commands** | `use_extra_commands` | SwitchPreference | Enables extra CLI argument injection. | `true` (Enabled) |
| **Force Keyframes** | `force_keyframes` | SwitchPreference | Forces cutting on exact keyframes when trimming with FFmpeg. | `false` (Off) |
| **Embed Metadata** | `embed_metadata` | SwitchPreference | Embeds title, artist, year, track number, and description tags in files. | `true` (Enabled) |
| **All Artists in Album Artist**| `all_artists_in_album_artist` | SwitchPreference | Formats multiple contributing artists into the Album Artist tag. | `false` (Off) |

#### Audio Post-Processing
| Preference Item | Key | Type | Description | Default State |
| :--- | :--- | :--- | :--- | :--- |
| **Default Audio Bitrate** | `audio_bitrate` | Preference | Default audio encoding bitrate. | `""` (Best available) |
| **Embed Thumbnail (Audio)** | `embed_thumbnail` | SwitchPreference | Embeds album art into audio files. | `true` (Enabled) |
| **Crop Thumbnail** | `crop_thumbnail` | SwitchPreference | Automatically crops rectangular video thumbnails into square album art. | `true` (Enabled) |
| **Embed Lyrics** | `embed_lyrics` | SwitchPreference | Fetches lyrics from LRCLIB and embeds into audio tags. | `false` (Off) |
| **Save .lrc File** | `save_lrc` | SwitchPreference | Writes standalone `.lrc` synced lyrics file. | `false` (Off) |
| **Playlist as Album** | `playlist_as_album` | SwitchPreference | Uses playlist name as Album tag when no album metadata exists. | `true` (Enabled) |
| **Preferred Audio Language** | `audio_language` | ListPreference | Selects preferred dubbing/language stream (e.g., `en`, `es`, `ar`). | Default original |
| **Preferred Audio Codec** | `audio_codec` | ListPreference | Preferred codec (`AAC`, `Opus`, `MP3`, `FLAC`, `Vorbis`). | Best available |
| **Prefer Container over Codec**| `prefer_container_over_codec_audio` | SwitchPreference | Prioritizes target container extension over codec matching. | `false` (Off) |
| **Audio Format Container** | `audio_format` | ListPreference | Default output container format (`m4a`, `mp3`, `opus`, `flac`). | Best available |
| **Preferred Format ID** | `format_id_audio` | EditText | Hardcoded custom yt-dlp format ID for audio. | Empty |
| **Prefer DRC Audio** | `prefer_drc_audio` | SwitchPreference | Prefers Dynamic Range Compression audio streams when offered. | `false` (Off) |

#### Video Post-Processing
| Preference Item | Key | Type | Description | Default State |
| :--- | :--- | :--- | :--- | :--- |
| **Embed Subtitles** | `embed_subtitles` | SwitchPreference | Automatically embeds subtitles into video container. | `true` (Enabled) |
| **Save Subtitles** | `write_subtitles` | SwitchPreference | Saves external `.srt`/`.vtt` file beside video. | `false` (Off) |
| **Save Auto Subtitles** | `write_auto_subtitles` | SwitchPreference | Saves automatic speech-to-text subtitles. | `false` (Off) |
| **No Keep Subtitles** | `no_keep_subs` | SwitchPreference | Deletes external subtitle files once successfully embedded. | `true` (Enabled) |
| **Subtitle Languages** | `subs_lang` | Preference | Subtitle language matching regex (e.g. `.*-orig`, `en.*`, `ar.*`). | `.*-orig` |
| **Subtitle Format** | `sub_format` | ListPreference | Subtitle container format (`srt`, `vtt`, `ass`, `lrc`). | Best available |
| **Save Thumbnail (Video)** | `write_thumbnail` | SwitchPreference | Saves separate thumbnail image on disk. | `false` (Off) |
| **Embed Thumbnail (Video)** | `video_embed_thumbnail` | SwitchPreference | Embeds thumbnail into MKV/MP4 video file. | `false` (Off) |
| **Thumbnail Format** | `thumbnail_format` | ListPreference | Format for saved thumbnails (`jpg`, `png`, `webp`). | `jpg` |
| **Add Chapters** | `add_chapters` | SwitchPreference | Embeds chapter markers into video file. | `true` (Enabled) |
| **Video Format Container** | `video_format` | ListPreference | Default container format (`mp4`, `mkv`, `webm`). | Best available |
| **Recode Video** | `recode_video` | SwitchPreference | Re-encodes video stream to target format via FFmpeg. | `false` (Off) |
| **Video Compatibility Mode**| `compatible_video` | SwitchPreference | Re-encodes videos with universal codecs for legacy players. | `false` (Off) |
| **Preferred Video Codec** | `video_codec` | ListPreference | Preferred video codec (`AV1`, `VP9`, `H.265/HEVC`, `H.264/AVC`).| Best available |
| **Video Quality** | `video_quality` | ListPreference | Preferred resolution (`Best`, `4320p/8K`, `2160p/4K`, `1080p`, `720p`, `480p`, `Worst`). | `best` |
| **Preferred Format ID (Video)**| `format_id` | EditText | Hardcoded custom yt-dlp format ID for video. | Empty |
| **Internal FFmpeg Preset** | `internal_plugin_ffmpeg_preset` | ListPreference | FFmpeg encoding speed preset (`ultrafast`, `superfast`, `veryfast`, `faster`, `fast`, `medium`). | `ultrafast` |
| **Remove Audio (Video)** | `remove_audio` | SwitchPreference | Globally removes audio track from all video downloads. | `false` (Off) |
| **Also Download Audio** | `also_download_audio` | SwitchPreference | Automatically extracts standalone audio track for every video downloaded. | `false` (Off) |
| **Preferred Format Size (Video)**| `preferred_format_size` | ListPreference | Prioritizes either smallest or largest file size format. | None (Quality based) |
| **Preferred Format Size (Audio)**| `preferred_format_size_audio`| ListPreference| Prioritizes smallest or largest audio file size. | None (Quality based) |

---

### 3.5 Advanced Settings (`advanced_preferences.xml`)

| Preference Item | Key | Type | Description | Default State |
| :--- | :--- | :--- | :--- | :--- |
| **YouTube Player Client** | `yt_player_client` | Preference | Selects client emulation profile (`ANDROID`, `IOS`, `WEB`, `TV_EMBEDDED`). | Default profile |
| **Generate PO Tokens** | `generate_po_tokens` | Preference | Configures BgUtils / Node.js PoToken generator server to bypass bot verification checks. | Configured server |
| **Use App Language for Metadata**| `use_app_language_for_metadata`| SwitchPreference| Forces YouTube to return titles and descriptions translated to app language. | `false` (Off) |
| **Other YouTube Extractor Args**| `youtube_other_extractor_args` | EditText | Raw custom `--extractor-args` passed to yt-dlp. | Empty |
| **Template Data Fetching Commands**| `enable_data_fetching_extra_commands`| SwitchPreference| Executes custom CLI args during initial metadata retrieval. | `false` (Off) |
| **Use Format Sorting** | `use_format_sorting` | SwitchPreference | Uses advanced format sorting criteria instead of simple selector. | `false` (Off) |
| **Format Importance (Video)** | `format_importance_video` | Preference | Drag-and-drop hierarchy for sorting video attributes (Resolution, FPS, Codec, Size). | Standard ordering |
| **Format Importance (Audio)** | `format_importance_audio` | Preference | Drag-and-drop hierarchy for sorting audio attributes (Bitrate, Codec, Size). | Standard ordering |
| **Disable Write InfoJSON** | `disable_write_info_json` | SwitchPreference | Stops writing intermediate `.info.json` metadata files to disk. | `false` (Writes JSON) |
| **No Check Certificates** | `no_check_certificates` | SwitchPreference | Bypasses HTTPS SSL/TLS certificate verification. | `false` (Verifies SSL) |
| **Custom User-Agent Header** | `use_header` | SwitchPreference | Uses custom User-Agent string for HTTP requests. | `false` (Off) |
| **Disable Flat Playlist** | `no_flat_playlist` | SwitchPreference | Forces deep extraction of every single video when querying a playlist. | `false` (Flat fast extract) |
| **Use Original Playlist URL** | `use_original_url_playlist` | SwitchPreference | Keeps uncanonicalized original playlist link. | `false` (Off) |

---

### 3.6 Updating Settings (`updating_preferences.xml`)

| Preference Item | Key | Type | Description | Default State |
| :--- | :--- | :--- | :--- | :--- |
| **yt-dlp Updates** | `ytdlp_update_screen` | Preference | Sub-screen to update the underlying yt-dlp python/binary engine and switch channels. | Sub-screen |
| **App Updates** | `app_update_screen` | Preference | Sub-screen to check for application updates, view changelogs, and manage channels. | Sub-screen |
| **Packages Manager** | `packages` | Preference | Inspects and updates bundled helper packages (FFmpeg, BgUtils, Python modules). | Sub-screen |
| **Installation Method** | `apk_install_method` | ListPreference | APK installer method (`System`, `External Session`). | `system` |
| **External Installer App** | `apk_install_external_apk_id` | Preference | Selects third-party package installer app (Shizuku, etc.). | **Hidden** |
| **Format Ordering** | `format_order` | ListPreference | Sort criteria for formats in the selector sheet (`Filesize`, `Bitrate`, `Resolution`). | `filesize` |
| **Format Filtering** | `format_filter` | ListPreference | Filter criteria in format sheet (`All`, `Audio only`, `Video only`). | `ALL` |
| **Update Formats** | `update_formats` | SwitchPreference | Automatically re-fetches latest available formats when opening the download sheet. | `true` (Enabled) |

---

### 3.7 Backup & Restore Settings (`root_preferences.xml`)

| Preference Item | Key | Type | Description | Default State |
| :--- | :--- | :--- | :--- | :--- |
| **Create Backup** | `backup` | Preference | Generates an encrypted zip archive containing databases, settings, cookies, and templates. | Tap to export |
| **Restore Backup** | `restore` | Preference | Restores all settings, history, and configuration from a previously saved backup file. | Tap to import |
| **Auto Backup on Release** | `automatic_backup` | SwitchPreference | Automatic background backup before updating to a new version. | **Hidden & Disabled** (`false`) |
| **Backup Path** | `backup_path` | Preference | Target storage directory where backup archives are created. | `Download/ClipFlow/Backups` |

---

### 3.8 About Screen (`root_preferences.xml`)

| Preference Item | Key | Type | Description | Default State |
| :--- | :--- | :--- | :--- | :--- |
| **Package ID / App Name** | `package_name` | Preference | Shows application package identifier (`com.clipflow.app`). Tap to copy to clipboard. | `com.clipflow.app` |
| **Developer Credit** | `developer_credit` | Preference | Honors project developer: **Eng. Ezzat Emam** (م/ عزت إمام). Tap to copy name. | **Visible** |
| **Privacy Policy** | `security` | Preference | Displays "🔐 ClipFlow - Privacy Policy" and opens the security document. | **Visible** |
| **License** | `license` | Preference | Open-source license: **GNU General Public License v3.0**. | **Visible** |

---

## 4. Home Screen & Download Queue Inventory

### 4.1 Home Screen Elements

| Feature / Item Name | Location / Screen | What It Does (Summary) | Current Default State |
| :--- | :--- | :--- | :--- |
| **Search Engine Chips (`providers`)** | Search View (Expanded) | Instant selector for search target: **YouTube**, **YouTube Music**, **SoundCloud**, etc. | `YouTube` (Selected) |
| **Search History Chips (`queries`)** | Search View (Expanded) | Displays recently searched keywords as clickable chips for quick re-querying. | Populated dynamically |
| **Execute Search (`init_search_query`)** | Search View (Expanded) | Button to submit query and initiate search across selected provider. | **Visible** |
| **Live Suggestions (`search_suggestions_recycler`)** | Search View (Expanded) | Auto-completes search terms in real-time as the user types in the search bar. | **Enabled** |
| **Playlist Filter Chips (`playlist_selection_chips`)** | Below Home Top Bar | Displays "All" chip (`@id/all`) alongside individual video chips to filter loaded playlist items. | Visible only on playlists |
| **Results Feed (`recyclerViewHome`)** | Home Feed (Scrollable) | Displays fetched search results or playlist entries with titles, channels, duration, and thumbnail cards. | Dynamic list |
| **Clipboard FAB (`copied_url_fab`)** | Bottom Right (Home) | Extended FAB reading "Link you copied" when a video link is detected in system clipboard. | Visible on URL copy |
| **Download All FAB (`download_all_fab`)** | Bottom Right (Home) | One-tap button to download every video/audio item in the current search result or playlist list. | Visible when results exist |
| **Download Selected FAB (`download_selected_fab`)**| Bottom Right (Home) | Appears when one or more items are checked, downloading only the highlighted subset. | Visible during selection |

#### Home Contextual Action Mode (Selection Menu)
Activated by long-pressing any result card:
- **Select Range (`select_between`)**: Selects all items between the first and second chosen cards.
- **Clear Selected (`delete_results`)**: Removes selected cards from current screen.
- **Download Selected (`download`)**: Opens batch download bottom sheet for selected items.
- **Select All (`select_all`)**: Checks all cards in the list.
- **Invert Selection (`invert_selected`)**: Flips selection state across all visible items.

---

### 4.2 Download Queue & Task Manager

The Download Queue (`DownloadQueueMainFragment`) manages all live, scheduled, and finished download tasks across **5 dedicated tabs**:

| Tab Name | Fragment Class | What It Displays & Manages |
| :--- | :--- | :--- |
| **1. Queued / Active** | `ActiveQueuedDownloadsFragment` | Active downloads currently downloading data or waiting in execution queue. Shows speed (MB/s), ETA, progress bar, percentage, and file size. |
| **2. Scheduled** | `ScheduledDownloadsFragment` | Downloads delayed to run at a specific future date/time or within the night scheduler window. |
| **3. Cancelled** | `CancelledDownloadsFragment` | Downloads cancelled or aborted by the user before completion. Can be resumed/re-queued. |
| **4. Errored** | `ErroredDownloadsFragment` | Downloads that failed due to network loss, extractor changes, or private video blocks. Shows error trace and one-tap retry button. |
| **5. Saved** | `SavedDownloadsFragment` | Completed downloads whose records are preserved for tracking and file playback. |

#### Queue Card Actions & Contextual Controls
For each active or queued item in the list:
- **Pause / Resume**: Temporarily suspends or restarts the download stream.
- **Cancel**: Terminates execution and moves task to the Cancelled tab.
- **Retry**: Re-attempts fetching and downloading for errored tasks.
- **Move to Top (`up`)**: Re-orders item to the top of the queue for immediate priority download.
- **Move to Bottom (`down`)**: Demotes item to the end of the line.
- **Copy URLs (`copy_urls`)**: Exports URLs of queued items to clipboard.
- **Delete (`delete_results`)**: Permanently removes the task record.
