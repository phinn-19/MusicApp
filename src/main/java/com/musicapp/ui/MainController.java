package com.musicapp.ui;

import com.musicapp.dao.PlaylistDao;
import com.musicapp.dao.SongDao;
import com.musicapp.model.Playlist;
import com.musicapp.model.Song;

import javafx.animation.KeyFrame;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainController {

    // ── Now Playing ──────────────────────────────────────────────────
    @FXML private StackPane albumArtPane;
    @FXML private ImageView albumArtImage;
    @FXML private Label albumArtPlaceholder;
    @FXML private Label albumArtLabel;
    @FXML private Label nowPlayingTitle;
    @FXML private Label nowPlayingArtist;
    @FXML private Label nowPlayingAlbum;
    @FXML private ProgressBar progressBar;
    @FXML private Label currentTimeLabel;
    @FXML private Label totalTimeLabel;
    @FXML private Button playPauseBtn;
    @FXML private Button shuffleBtn;
    @FXML private Button repeatBtn;
    @FXML private Slider volumeSlider;
    @FXML private TextField streamUrlField;

    // ── Sidebar / Views ──────────────────────────────────────────────
    @FXML private VBox sidebar;
    @FXML private VBox panelSongs;
    @FXML private VBox panelPlaylists;
    @FXML private Button tabSongsBtn;
    @FXML private Button tabPlaylistsBtn;
    @FXML private Button themeToggleBtn;
    @FXML private VBox playerView;
    @FXML private VBox searchView;

    // ── Song list ────────────────────────────────────────────────────
    @FXML private ListView<Song> songListView;
    @FXML private ListView<Playlist> playlistListView;
    @FXML private ListView<Song> playlistSongsView;
    @FXML private ComboBox<Playlist> playlistComboBox;

    // ── Search ───────────────────────────────────────────────────────
    @FXML private TextField searchField;
    @FXML private TilePane searchResultTile;
    @FXML private Label searchResultTitle;
    @FXML private Label searchResultCount;
    @FXML private ComboBox<Playlist> searchPlaylistCombo;

    // ── Lyrics sidebar ───────────────────────────────────────────────
    @FXML private VBox lyricsSidebar;
    @FXML private Label lyricsSongTitle;
    @FXML private Label lyricsArtistName;
    @FXML private TextArea lyricsArea;

    // ── Status ───────────────────────────────────────────────────────
    @FXML private Label statusLabel;

    // ── DAOs ─────────────────────────────────────────────────────────
    private final SongDao songDao = new SongDao();
    private final PlaylistDao playlistDao = new PlaylistDao();

    // ── Observable lists ─────────────────────────────────────────────
    private final ObservableList<Song> songList = FXCollections.observableArrayList();
    private final ObservableList<Playlist> playlistList = FXCollections.observableArrayList();
    private final ObservableList<Song> playlistSongList = FXCollections.observableArrayList();
    private final ObservableList<Song> searchResultList = FXCollections.observableArrayList();
    private final Map<Integer, String> lyricsMap = new HashMap<>();

    // ── Playback state ───────────────────────────────────────────────
    private Timeline progressTimeline;
    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;
    private boolean shuffleOn = false;
    private boolean repeatOn = false;
    private boolean sidebarVisible = true;
    private boolean lyricsVisible = true;
    private boolean darkMode = false;
    private Song currentSong = null;
    private double progressSeconds = 0;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "yt-dlp-thread");
        t.setDaemon(true);
        return t;
    });

    private static final String CSS_LIGHT = "/com/musicapp/ui/musicapp.css";
    private static final String CSS_DARK   = "/com/musicapp/ui/musicapp-dark.css";

    // ─────────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        songListView.setItems(songList);
        songListView.setCellFactory(lv -> new javafx.scene.control.ListCell<Song>() {
            private final ImageView imageView = new ImageView();
            private final HBox hbox = new HBox(12);
            private final VBox vbox = new VBox(4);
            private final Label titleLabel = new Label();
            private final Label artistLabel = new Label();

            {
                imageView.setFitWidth(60);
                imageView.setFitHeight(60);
                imageView.setPreserveRatio(true);
                imageView.setSmooth(true);
                imageView.setStyle("-fx-background-radius: 8; -fx-background-color: #f0f0f0;");

                titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #333;");
                artistLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

                vbox.getChildren().addAll(titleLabel, artistLabel);
                hbox.getChildren().addAll(imageView, vbox);
                hbox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                hbox.setStyle("-fx-padding: 8 12 8 12;");
            }

            @Override
            protected void updateItem(Song song, boolean empty) {
                super.updateItem(song, empty);
                if (empty || song == null) {
                    setGraphic(null);
                    imageView.setImage(null);
                } else {
                    titleLabel.setText(song.getTitle());
                    artistLabel.setText(song.getArtist());

                    // Reset image to default before fetching
                    imageView.setImage(null);

                    // Fetch album art in background
                    executor.submit(() -> {
                        try {
                            String query = java.net.URLEncoder.encode(song.getTitle() + " " + song.getArtist(), StandardCharsets.UTF_8);
                            String apiUrl = "https://itunes.apple.com/search?term=" + query + "&media=music&limit=1";
                            java.net.URL url = new java.net.URL(apiUrl);
                            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                            conn.setConnectTimeout(3000);
                            conn.setReadTimeout(3000);
                            conn.setRequestProperty("User-Agent", "MusicApp/1.0");

                            if (conn.getResponseCode() == 200) {
                                String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                                int idx = body.indexOf("\"artworkUrl100\":");
                                if (idx >= 0) {
                                    int start = body.indexOf("\"", idx + 16) + 1;
                                    int end = body.indexOf("\"", start);
                                    String artUrl = body.substring(start, end);
                                    artUrl = artUrl.replace("100x100bb", "60x60bb");
                                    final String finalUrl = artUrl;
                                    Image img = new Image(finalUrl, true);
                                    img.progressProperty().addListener((obs, o, n) -> {
                                        if (n.doubleValue() >= 1.0 && !img.isError()) {
                                            Platform.runLater(() -> {
                                                if (!isEmpty() && getItem() == song) imageView.setImage(img);
                                            });
                                        }
                                    });
                                }
                            }
                            conn.disconnect();
                        } catch (Exception ignored) {
                            // Keep default image on error
                        }
                    });

                    setGraphic(hbox);
                }
            }
        });

        playlistListView.setItems(playlistList);
        playlistSongsView.setItems(playlistSongList);
        playlistSongsView.setCellFactory(lv -> new javafx.scene.control.ListCell<Song>() {
            private final ImageView imageView = new ImageView();
            private final HBox hbox = new HBox(12);
            private final VBox vbox = new VBox(4);
            private final Label titleLabel = new Label();
            private final Label artistLabel = new Label();

            {
                imageView.setFitWidth(60);
                imageView.setFitHeight(60);
                imageView.setPreserveRatio(true);
                imageView.setSmooth(true);
                imageView.setStyle("-fx-background-radius: 8; -fx-background-color: #f0f0f0;");

                titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #333;");
                artistLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

                vbox.getChildren().addAll(titleLabel, artistLabel);
                hbox.getChildren().addAll(imageView, vbox);
                hbox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                hbox.setStyle("-fx-padding: 8 12 8 12;");
            }

            @Override
            protected void updateItem(Song song, boolean empty) {
                super.updateItem(song, empty);
                if (empty || song == null) {
                    setGraphic(null);
                    imageView.setImage(null);
                } else {
                    titleLabel.setText(song.getTitle());
                    artistLabel.setText(song.getArtist());

                    // Reset image to default before fetching
                    imageView.setImage(null);

                    // Fetch album art in background
                    executor.submit(() -> {
                        try {
                            String query = java.net.URLEncoder.encode(song.getTitle() + " " + song.getArtist(), StandardCharsets.UTF_8);
                            String apiUrl = "https://itunes.apple.com/search?term=" + query + "&media=music&limit=1";
                            java.net.URL url = new java.net.URL(apiUrl);
                            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                            conn.setConnectTimeout(3000);
                            conn.setReadTimeout(3000);
                            conn.setRequestProperty("User-Agent", "MusicApp/1.0");

                            if (conn.getResponseCode() == 200) {
                                String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                                int idx = body.indexOf("\"artworkUrl100\":");
                                if (idx >= 0) {
                                    int start = body.indexOf("\"", idx + 16) + 1;
                                    int end = body.indexOf("\"", start);
                                    String artUrl = body.substring(start, end);
                                    artUrl = artUrl.replace("100x100bb", "60x60bb");
                                    final String finalUrl = artUrl;
                                    Image img = new Image(finalUrl, true);
                                    img.progressProperty().addListener((obs, o, n) -> {
                                        if (n.doubleValue() >= 1.0 && !img.isError()) {
                                            Platform.runLater(() -> {
                                                if (!isEmpty() && getItem() == song) imageView.setImage(img);
                                            });
                                        }
                                    });
                                }
                            }
                            conn.disconnect();
                        } catch (Exception ignored) {
                            // Keep default image on error
                        }
                    });

                    setGraphic(hbox);
                }
            }
        });

        playlistComboBox.setItems(playlistList);

        progressTimeline = new Timeline(new KeyFrame(Duration.millis(200), e -> tickProgress()));
        progressTimeline.setCycleCount(Timeline.INDEFINITE);

        if (volumeSlider != null) {
            volumeSlider.valueProperty().addListener((obs, o, n) -> {
                if (mediaPlayer != null) mediaPlayer.setVolume(n.doubleValue() / 100.0);
            });
        }

        // Double-click to play in list
        songListView.setOnMouseClicked(e -> {
            if (e.getClickCount() >= 1) {
                Song s = songListView.getSelectionModel().getSelectedItem();
                if (s != null) playSong(s);
            }
        });
        songListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, o, n) -> { if (n != null) playSong(n); });
        playlistListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, o, n) -> { if (n != null) loadPlaylistSongs(n); });
        searchResultView.setOnMouseClicked(e -> {
            if (e.getClickCount() >= 1) {
                Song s = searchResultView.getSelectionModel().getSelectedItem();
                if (s != null) playSong(s);
            }
        });

        // Also support pressing Enter in stream field
        if (streamUrlField != null) {
            streamUrlField.setOnAction(e -> onStreamUrl());
        }

        // Remove duplicate songs on startup
        int duplicatesRemoved = songDao.removeDuplicates();
        if (duplicatesRemoved > 0) {
            System.out.println("Đã xóa " + duplicatesRemoved + " bài hát trùng khỏi database");
        }
        
        loadSongs();
        loadPlaylists();
        showSongsPanel();
    }

    // ════════════════════════════════════════════════════════════════
    //  PLAYBACK
    // ════════════════════════════════════════════════════════════════

    private void playSong(Song song) {
        // Stop old song before playing new one
        stopMedia();
        
        // Update UI immediately for instant feedback
        currentSong = song;
        progressSeconds = 0;
        progressBar.setProgress(0);
        currentTimeLabel.setText("0:00");
        totalTimeLabel.setText(song.getDurationFormatted());
        nowPlayingTitle.setText(song.getTitle());
        nowPlayingArtist.setText(song.getArtist());
        nowPlayingAlbum.setText(song.getAlbum() != null ? song.getAlbum() : "");
        setStatus("Đang phát: " + song.getTitle() + " — " + song.getArtist());

        // Reset album art to default green immediately
        setAlbumArtDefault();

        // Update lyrics panel immediately
        updateLyricsPanel(song);

        // Fetch album art in background (non-blocking)
        executor.submit(() -> fetchAlbumArt(song.getTitle(), song.getArtist()));

        String path = song.getFilePath();
        if (path != null && !path.isBlank()) {
            if (path.startsWith("http")) {
                playYouTubeWithFallback(path);
            } else {
                playMedia(path.startsWith("http") ? path : new File(path).toURI().toString(), song.getDuration());
            }
        } else {
            stopMedia();
            startProgressTimer();
        }
    }

    private void setAlbumArtDefault() {
        if (albumArtImage != null) {
            albumArtImage.setImage(null);
            albumArtPlaceholder.setVisible(true);
        }
        if (albumArtLabel != null) albumArtLabel.setText("♪");
    }

    private void fetchAlbumArt(String title, String artist) {
        if (title == null || title.isBlank() || artist == null || artist.isBlank()) return;
        if (title.startsWith("Stream:") || artist.isEmpty()) return;

        executor.submit(() -> {
            try {
                // Use iTunes Search API for album art
                String query = java.net.URLEncoder.encode(title + " " + artist, StandardCharsets.UTF_8);
                String apiUrl = "https://itunes.apple.com/search?term=" + query + "&media=music&limit=1";
                java.net.URL url = new java.net.URL(apiUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestProperty("User-Agent", "MusicApp/1.0");

                if (conn.getResponseCode() == 200) {
                    String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    // Simple JSON parsing to find artworkUrl100
                    int idx = body.indexOf("\"artworkUrl100\":");
                    if (idx >= 0) {
                        int start = body.indexOf("\"", idx + 16) + 1;
                        int end = body.indexOf("\"", start);
                        String artUrl = body.substring(start, end);
                        // Use 300x300 version
                        artUrl = artUrl.replace("100x100bb", "300x300bb");
                        final String finalUrl = artUrl;
                        Image img = new Image(finalUrl, true);
                        img.progressProperty().addListener((obs, o, n) -> {
                            if (n.doubleValue() >= 1.0 && !img.isError()) {
                                Platform.runLater(() -> {
                                    if (albumArtImage != null) {
                                        albumArtImage.setImage(img);
                                        albumArtPlaceholder.setVisible(false);
                                        if (albumArtLabel != null) albumArtLabel.setText("");
                                    }
                                });
                            }
                        });
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {}
        });
    }


    private String findYtDlp() {
        String[] candidates = {"yt-dlp", "yt-dlp.exe",
            System.getProperty("user.home") + "/AppData/Local/Programs/Python/Python312/Scripts/yt-dlp.exe",
            System.getProperty("user.home") + "/.local/bin/yt-dlp",
            "/usr/local/bin/yt-dlp", "/usr/bin/yt-dlp"
        };
        for (String c : candidates) {
            try {
                Process p = new ProcessBuilder(c, "--version").start();
                p.waitFor();
                if (p.exitValue() == 0) return c;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private YouTubeMetadata fetchYouTubeMetadata(String youtubeUrl) {
        String ytDlp = findYtDlp();
        if (ytDlp == null) return null;
        
        try {
            ProcessBuilder pb = new ProcessBuilder(
                ytDlp, "--no-playlist", "--dump-json", "--encoding", "utf-8", youtubeUrl
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            
            // Read output with proper UTF-8 handling
            StringBuilder output = new StringBuilder();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            int exit = proc.waitFor();
            String jsonOutput = output.toString().trim();
            
            if (exit == 0 && !jsonOutput.isBlank()) {
                // Parse JSON to extract title and description (potential lyrics)
                String title = extractJsonValue(jsonOutput, "title");
                String artist = extractJsonValue(jsonOutput, "uploader");
                String duration = extractJsonValue(jsonOutput, "duration");
                String description = extractJsonValue(jsonOutput, "description");
                
                if (title != null) {
                    YouTubeMetadata metadata = new YouTubeMetadata();
                    metadata.title = title;
                    metadata.artist = artist != null ? artist : "YouTube";
                    metadata.duration = duration != null ? parseDuration(duration) : 0;
                    metadata.description = description != null ? description : "";
                    return metadata;
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching YouTube metadata: " + e.getMessage());
        }
        return null;
    }
    
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int startIdx = json.indexOf(searchKey);
        if (startIdx == -1) return null;
        
        startIdx += searchKey.length();
        if (startIdx >= json.length()) return null;
        
        // Skip whitespace
        while (startIdx < json.length() && Character.isWhitespace(json.charAt(startIdx))) {
            startIdx++;
        }
        
        if (startIdx >= json.length()) return null;
        
        char firstChar = json.charAt(startIdx);
        if (firstChar == '"') {
            // String value - handle escaped quotes
            startIdx++;
            StringBuilder result = new StringBuilder();
            boolean escaped = false;
            
            for (int i = startIdx; i < json.length(); i++) {
                char c = json.charAt(i);
                if (escaped) {
                    if (c == '"' || c == '\\') {
                        result.append(c);
                    } else if (c == 'n') {
                        result.append('\n');
                    } else if (c == 't') {
                        result.append('\t');
                    } else if (c == 'u' && i + 4 < json.length()) {
                        // Handle Unicode escape sequences
                        try {
                            String hex = json.substring(i + 1, i + 5);
                            result.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        } catch (Exception e) {
                            result.append(c);
                        }
                    } else {
                        result.append(c);
                    }
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    break;
                } else {
                    result.append(c);
                }
            }
            return result.toString();
        } else {
            // Numeric value
            int endIdx = json.indexOf(",", startIdx);
            if (endIdx == -1) endIdx = json.indexOf("}", startIdx);
            if (endIdx == -1) return null;
            return json.substring(startIdx, endIdx).trim();
        }
    }
    
    private int parseDuration(String durationStr) {
        try {
            return Integer.parseInt(durationStr);
        } catch (Exception e) {
            return 0;
        }
    }
    
    private static class YouTubeMetadata {
        String title;
        String artist;
        int duration;
        String description;
    }

    private void playMedia(String uri, int durationHint) {
        System.out.println("=== playMedia called ===");
        System.out.println("URI: " + uri);
        System.out.println("Duration hint: " + durationHint);
        
        // Check if URI is a file and if file exists
        if (uri.startsWith("file:/")) {
            String filePath = uri.substring(5); // Remove "file:" prefix
            java.io.File file = new java.io.File(filePath);
            System.out.println("File path: " + filePath);
            System.out.println("File exists: " + file.exists());
            System.out.println("File size: " + (file.exists() ? file.length() + " bytes" : "N/A"));
            if (!file.exists()) {
                System.err.println("ERROR: File does not exist!");
                setStatus("Loi: File khong ton tai!");
                return;
            }
        }
        
        stopMedia();
        try {
            System.out.println("Creating Media object...");
            Media media = new Media(uri);
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setVolume(volumeSlider != null ? volumeSlider.getValue() / 100.0 : 0.7);
            
            // Set up error handling
            mediaPlayer.setOnError(() -> {
                System.err.println("MediaPlayer error: " + mediaPlayer.getError());
                System.err.println("Error type: " + mediaPlayer.getError().getType());
                Platform.runLater(() -> {
                    setStatus("Loi MediaPlayer: " + mediaPlayer.getError().getMessage());
                    // Fallback to manual progress timer
                    startProgressTimer();
                });
            });
            
            mediaPlayer.setOnReady(() -> {
                System.out.println("MediaPlayer ready");
                double total = mediaPlayer.getTotalDuration().toSeconds();
                System.out.println("Total duration: " + total + " seconds");
                if (total > 0) {
                    Platform.runLater(() -> totalTimeLabel.setText(formatTime((int) total)));
                } else if (durationHint > 0) {
                    // Use duration hint if available
                    Platform.runLater(() -> totalTimeLabel.setText(formatTime(durationHint)));
                }
            });
            mediaPlayer.currentTimeProperty().addListener((obs, o, n) -> {
                if (mediaPlayer == null) return;
                double total = mediaPlayer.getTotalDuration().toSeconds();
                double cur = n.toSeconds();
                progressSeconds = cur;
                Platform.runLater(() -> {
                    if (total > 0) progressBar.setProgress(cur / total);
                    currentTimeLabel.setText(formatTime((int) cur));
                });
            });
            mediaPlayer.setOnEndOfMedia(() -> {
                stopPlayback();
                if (repeatOn) playSong(currentSong);
                else onNext();
            });
            mediaPlayer.play();
            isPlaying = true;
            playPauseBtn.setText("⏸");
        } catch (Exception e) {
            setStatus("⚠ Không phát được: " + e.getMessage());
            startProgressTimer();
        }
    }

    private void stopMedia() {
        if (mediaPlayer != null) { mediaPlayer.stop(); mediaPlayer.dispose(); mediaPlayer = null; }
        progressTimeline.stop();
    }

    private void startProgressTimer() {
        isPlaying = true;
        playPauseBtn.setText("⏸");
        progressTimeline.play();
    }

    private void startPlayback() {
        isPlaying = true;
        playPauseBtn.setText("⏸");
        if (mediaPlayer != null) mediaPlayer.play();
        else progressTimeline.play();
    }

    private void stopPlayback() {
        isPlaying = false;
        playPauseBtn.setText("▶");
        if (mediaPlayer != null) mediaPlayer.pause();
        else progressTimeline.pause();
    }

    private void tickProgress() {
        if (currentSong == null) return;
        progressSeconds += 0.2;
        double total = currentSong.getDuration();
        
        // If duration is 0 (common for YouTube streams), use a reasonable default
        if (total <= 0) {
            total = 180; // 3 minutes default for unknown duration
            // Just update time display without progress bar
            currentTimeLabel.setText(formatTime((int) progressSeconds));
            return;
        }
        
        double p = progressSeconds / total;
        if (p >= 1.0) {
            progressBar.setProgress(1.0);
            stopPlayback();
            if (repeatOn) { progressSeconds = 0; startPlayback(); }
            else onNext();
            return;
        }
        progressBar.setProgress(p);
        currentTimeLabel.setText(formatTime((int) progressSeconds));
    }

    private String formatTime(int secs) {
        return secs / 60 + ":" + String.format("%02d", secs % 60);
    }

    @FXML private void onPlayPause() {
        if (currentSong == null) {
            if (!songList.isEmpty()) playSong(songList.get(0));
            return;
        }
        if (isPlaying) stopPlayback(); else startPlayback();
    }

    @FXML private void onPrev() {
        if (songList.isEmpty()) return;
        int idx = songList.indexOf(currentSong);
        int prev = (idx <= 0) ? songList.size() - 1 : idx - 1;
        playSong(songList.get(prev));
        songListView.getSelectionModel().select(prev);
    }

    @FXML private void onNext() {
        if (songList.isEmpty()) return;
        int idx = songList.indexOf(currentSong);
        int next = shuffleOn
                ? (int) (Math.random() * songList.size())
                : (idx >= songList.size() - 1 ? 0 : idx + 1);
        playSong(songList.get(next));
        songListView.getSelectionModel().select(next);
    }

    @FXML private void onToggleShuffle() { shuffleOn = !shuffleOn; applyGhostToggle(shuffleBtn, shuffleOn); }
    @FXML private void onToggleRepeat() { repeatOn = !repeatOn; applyGhostToggle(repeatBtn, repeatOn); }

    private void applyGhostToggle(Button btn, boolean on) {
        if (btn == null) return;
        btn.getStyleClass().clear();
        btn.getStyleClass().add(on ? "btn-ghost-on" : "btn-ghost");
    }

    // ════════════════════════════════════════════════════════════════
    //  STREAM URL — supports YouTube, SoundCloud placeholder, mp3
    // ════════════════════════════════════════════════════════════════

    @FXML
    private void onStreamUrl() {
        if (streamUrlField == null) return;
        String url = streamUrlField.getText().trim();
        if (url.isEmpty()) { showError("Vui lòng dán link nhạc vào!"); return; }

        boolean isYouTube = url.contains("youtube.com") || url.contains("youtu.be");
        boolean isSoundCloud = url.contains("soundcloud.com");

        setStatus("Đang tải thông tin bài hát...");

        // Fetch metadata BEFORE saving to database
        executor.submit(() -> {
            try {
                String title = url;
                String artist = isYouTube ? "YouTube" : (isSoundCloud ? "SoundCloud" : "Online");
                int duration = 0;

                if (isYouTube) {
                    YouTubeMetadata metadata = fetchYouTubeMetadata(url);
                    if (metadata != null) {
                        title = metadata.title;
                        artist = metadata.artist;
                        duration = metadata.duration;

                        // Try to extract lyrics from description
                        String lyrics = extractLyricsFromDescription(metadata.description);
                        if (lyrics != null && !lyrics.trim().isEmpty()) {
                            lyricsMap.put(-1, lyrics); // Temp storage, will update after song is saved
                        }
                    } else {
                        // Fallback if metadata fetch fails
                        title = "YouTube: " + url.replaceAll(".*[?&]v=([^&]+).*", "$1");
                        try { title = URLDecoder.decode(title, StandardCharsets.UTF_8); } catch (Exception ignored) {}
                    }
                } else {
                    // For non-YouTube URLs, use filename as title
                    title = url.substring(url.lastIndexOf('/') + 1);
                    try { title = URLDecoder.decode(title, StandardCharsets.UTF_8); } catch (Exception ignored) {}
                }

                // Create song with fetched metadata
                final String finalTitle = title;
                final String finalArtist = artist;
                final int finalDuration = duration;
                Song streamSong = new Song(finalTitle, finalArtist, "", finalDuration, url);

                // Add to database with correct metadata
                Platform.runLater(() -> {
                    if (songDao.addSong(streamSong)) {
                        // Update lyrics map with correct song ID
                        if (lyricsMap.containsKey(-1)) {
                            lyricsMap.put(streamSong.getId(), lyricsMap.remove(-1));
                        }

                        // Add to list
                        addSongToList(streamSong);

                        // Play immediately
                        playSong(streamSong);

                        // Clear field
                        streamUrlField.clear();
                        setStatus("Đã thêm: " + finalTitle + " - " + finalArtist);
                    } else {
                        showError("Không thêm được bài hát!");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setStatus("Lỗi khi tải thông tin: " + e.getMessage());
                });
            }
        });
    }

    // ════════════════════════════════════════════════════════════════
    //  DARK MODE TOGGLE
    // ════════════════════════════════════════════════════════════════

    @FXML
    private void onToggleTheme() {
        darkMode = !darkMode;
        Scene scene = sidebar.getScene();
        if (scene == null) return;
        scene.getStylesheets().clear();
        String cssPath = getClass().getResource(darkMode ? CSS_DARK : CSS_LIGHT).toExternalForm();
        scene.getStylesheets().add(cssPath);
        if (themeToggleBtn != null) themeToggleBtn.setText(darkMode ? "☀" : "🌙");

        // Update lyrics sidebar bg for dark mode
        if (lyricsSidebar != null) {
            if (darkMode) {
                lyricsSidebar.setStyle("-fx-background-color: #000000; -fx-border-color: transparent transparent transparent #282828; -fx-border-width: 0 0 0 1;");
            } else {
                lyricsSidebar.setStyle("-fx-background-color: #F5F5F5; -fx-border-color: transparent transparent transparent #E0E0E0; -fx-border-width: 0 0 0 1;");
            }
        }

        setStatus(darkMode ? "Dark mode bật" : "Light mode bật");
    }

    // ════════════════════════════════════════════════════════════════
    //  SIDEBAR
    // ════════════════════════════════════════════════════════════════

    @FXML private void onToggleSidebar() {
        sidebarVisible = !sidebarVisible;
        if (sidebar != null) { sidebar.setVisible(sidebarVisible); sidebar.setManaged(sidebarVisible); }
    }

    @FXML private void onTabSongs() { showSongsPanel(); }
    @FXML private void onTabPlaylists() { showPlaylistsPanel(); }

    private void showSongsPanel() {
        setVisible(panelSongs, true); setVisible(panelPlaylists, false);
        setTabStyle(tabSongsBtn, true); setTabStyle(tabPlaylistsBtn, false);
    }

    private void showPlaylistsPanel() {
        setVisible(panelSongs, false); setVisible(panelPlaylists, true);
        setTabStyle(tabSongsBtn, false); setTabStyle(tabPlaylistsBtn, true);
    }

    private void setTabStyle(Button btn, boolean active) {
        if (btn == null) return;
        btn.getStyleClass().clear();
        btn.getStyleClass().add(active ? "btn-tab-active" : "btn-tab-inactive");
    }

    // ════════════════════════════════════════════════════════════════
    //  LYRICS
    // ════════════════════════════════════════════════════════════════

    @FXML private void onToggleLyrics() {
        lyricsVisible = !lyricsVisible;
        if (lyricsSidebar != null) { lyricsSidebar.setVisible(lyricsVisible); lyricsSidebar.setManaged(lyricsVisible); }
    }

    private void updateLyricsPanel(Song song) {
        if (lyricsSongTitle != null) lyricsSongTitle.setText(song.getTitle());
        if (lyricsArtistName != null) lyricsArtistName.setText(song.getArtist());
        if (lyricsArea != null) lyricsArea.setText(lyricsMap.getOrDefault(song.getId(), ""));
    }

    @FXML private void onEditLyrics() {
        if (currentSong == null) { showError("Vui lòng chọn bài hát trước!"); return; }
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Nhập lời bài hát");
        dialog.setHeaderText(currentSong.getTitle() + " — " + currentSong.getArtist());
        ButtonType saveBtn = new ButtonType("Lưu", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
        TextArea ta = new TextArea();
        ta.setWrapText(true); ta.setPrefRowCount(18); ta.setPrefColumnCount(40);
        ta.setPromptText("Dán lời bài hát vào đây...");
        ta.setText(lyricsMap.getOrDefault(currentSong.getId(), ""));
        dialog.getDialogPane().setContent(ta);
        dialog.setResultConverter(btn -> btn == saveBtn ? ta.getText() : null);
        dialog.showAndWait().ifPresent(lyrics -> {
            lyricsMap.put(currentSong.getId(), lyrics);
            lyricsArea.setText(lyrics);
            setStatus("Đã lưu lời: " + currentSong.getTitle());
        });
    }

    @FXML private void onClearLyrics() {
        if (currentSong == null) return;
        lyricsMap.remove(currentSong.getId());
        lyricsArea.setText("");
    }

    // ════════════════════════════════════════════════════════════════
    //  SEARCH
    // ════════════════════════════════════════════════════════════════

    @FXML private void onSearch() {
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) { onBackToPlayer(); return; }
        List<Song> results = songDao.searchSongs(keyword);
        searchResultList.setAll(results);
        searchResultTitle.setText("Kết quả: \"" + keyword + "\"");
        searchResultCount.setText("Tìm thấy " + results.size() + " bài hát");
        searchPlaylistCombo.setItems(playlistList);
        populateSearchTile(results);
        setVisible(playerView, false); setVisible(searchView, true);
        setStatus("Tìm thấy " + results.size() + " bài cho \"" + keyword + "\"");
    }

    private void populateSearchTile(List<Song> results) {
        searchResultTile.getChildren().clear();
        for (Song song : results) {
            VBox card = new VBox(8);
            card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0, 0, 2);");

            ImageView imageView = new ImageView();
            imageView.setFitWidth(120);
            imageView.setFitHeight(120);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            imageView.setStyle("-fx-background-radius: 8; -fx-background-color: #f0f0f0;");

            Label titleLabel = new Label(song.getTitle());
            titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #333; -fx-wrap-text: true;");
            titleLabel.setMaxWidth(120);

            Label artistLabel = new Label(song.getArtist());
            artistLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666; -fx-wrap-text: true;");
            artistLabel.setMaxWidth(120);

            card.getChildren().addAll(imageView, titleLabel, artistLabel);
            card.setAlignment(javafx.geometry.Pos.CENTER);

            // Fetch album art in background
            executor.submit(() -> {
                try {
                    String query = java.net.URLEncoder.encode(song.getTitle() + " " + song.getArtist(), StandardCharsets.UTF_8);
                    String apiUrl = "https://itunes.apple.com/search?term=" + query + "&media=music&limit=1";
                    java.net.URL url = new java.net.URL(apiUrl);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    conn.setRequestProperty("User-Agent", "MusicApp/1.0");

                    if (conn.getResponseCode() == 200) {
                        String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                        int idx = body.indexOf("\"artworkUrl100\":");
                        if (idx >= 0) {
                            int start = body.indexOf("\"", idx + 16) + 1;
                            int end = body.indexOf("\"", start);
                            String artUrl = body.substring(start, end);
                            artUrl = artUrl.replace("100x100bb", "120x120bb");
                            final String finalUrl = artUrl;
                            Image img = new Image(finalUrl, true);
                            img.progressProperty().addListener((obs, o, n) -> {
                                if (n.doubleValue() >= 1.0 && !img.isError()) {
                                    Platform.runLater(() -> imageView.setImage(img));
                                }
                            });
                        }
                    }
                    conn.disconnect();
                } catch (Exception ignored) {
                    // Keep default image on error
                }
            });

            // Click handler
            card.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    playSong(song);
                }
            });

            searchResultTile.getChildren().add(card);
        }
    }

    @FXML private void onBackToPlayer() {
        setVisible(searchView, false); setVisible(playerView, true);
        searchField.clear();
    }

    @FXML private void onAddSearchResultToPlaylist() {
        // Not needed anymore - double click to play
        showError("Click đúp vào bài hát để phát!");
    }

    // ════════════════════════════════════════════════════════════════
    //  SONGS CRUD
    // ════════════════════════════════════════════════════════════════

    private void loadSongs() {
        songList.setAll(songDao.getAllSongs());
        setStatus("Đã tải " + songList.size() + " bài hát");
    }
    
    private void addSongToList(Song song) {
        songList.add(song);
        setStatus("Ðâº£ thÃªm: " + song.getTitle());
    }

    @FXML private void onAddSong() {
        String title  = prompt("Thêm bài hát", "Tên bài hát:", "Tên:", "");          if (title == null) return;
        String artist = prompt("Thêm bài hát", "Ca sĩ:",        "Ca sĩ:", "");       if (artist == null) return;
        String album  = prompt("Thêm bài hát", "Album:",         "Album:", "");
        String durStr = prompt("Thêm bài hát", "Thời lượng (giây):", "Giây:", "0");
        int dur = 0; try { dur = Integer.parseInt(durStr == null ? "0" : durStr.trim()); } catch (Exception ignored) {}
        Song s = new Song(title.trim(), artist.trim(), album == null ? "" : album.trim(), dur, "");

        // Add to database in background
        executor.submit(() -> {
            if (songDao.addSong(s)) {
                // Add to list immediately
                Platform.runLater(() -> addSongToList(s));
            } else {
                Platform.runLater(() -> showError("Không thêm được bài hát!"));
            }
        });
    }

    @FXML private void onImportSong() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn file nhạc");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("File nhạc", "*.mp3", "*.wav", "*.flac", "*.m4a", "*.ogg"),
                new FileChooser.ExtensionFilter("Tất cả file", "*.*"));
        File file = chooser.showOpenDialog(sidebar.getScene().getWindow());
        if (file == null) return;
        String nameOnly = file.getName().contains(".")
                ? file.getName().substring(0, file.getName().lastIndexOf('.'))
                : file.getName();
        String title = prompt("Import bài hát", "Tên bài:", "Tên:", nameOnly); if (title == null) return;
        String artist = prompt("Import bài hát", "Ca sĩ:", "Ca sĩ:", ""); if (artist == null) artist = "";
        Song s = new Song(title.trim(), artist.trim(), "", 0, file.getAbsolutePath());
        if (songDao.addSong(s)) { loadSongs(); setStatus("Đã import: " + s.getTitle()); }
        else showError("Không import được!");
    }

    @FXML private void onDeleteSong() {
        Song s = songListView.getSelectionModel().getSelectedItem();
        if (s == null) { showError("Vui lòng chọn bài hát!"); return; }
        if (confirm("Xóa \"" + s.getTitle() + "\"?")) {
            if (songDao.deleteSong(s.getId())) {
                if (currentSong != null && currentSong.getId() == s.getId()) { stopMedia(); stopPlayback(); }
                lyricsMap.remove(s.getId()); loadSongs();
                setStatus("Đã xóa: " + s.getTitle());
            } else showError("Không xóa được!");
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  PLAYLIST CRUD
    // ════════════════════════════════════════════════════════════════

    private void loadPlaylists() {
        playlistList.setAll(playlistDao.getAllPlaylists());
        playlistComboBox.setItems(playlistList);
    }

    private void loadPlaylistSongs(Playlist pl) {
        playlistSongList.setAll(playlistDao.getSongsInPlaylist(pl.getId()));
        setStatus("Playlist \"" + pl.getName() + "\": " + playlistSongList.size() + " bài");
    }

    @FXML private void onAddPlaylist() {
        String name = prompt("Tạo playlist", "Tên playlist:", "Tên:", ""); if (name == null || name.isBlank()) return;
        Playlist pl = new Playlist(name.trim());
        if (playlistDao.addPlaylist(pl)) { loadPlaylists(); setStatus("Đã tạo: " + pl.getName()); }
        else showError("Không tạo được playlist!");
    }

    @FXML private void onDeletePlaylist() {
        Playlist pl = playlistListView.getSelectionModel().getSelectedItem();
        if (pl == null) { showError("Vui lòng chọn playlist!"); return; }
        if (confirm("Xóa \"" + pl.getName() + "\"?")) {
            if (playlistDao.deletePlaylist(pl.getId())) { playlistSongList.clear(); loadPlaylists(); setStatus("Đã xóa: " + pl.getName()); }
            else showError("Không xóa được!");
        }
    }

    @FXML private void onAddSongToPlaylist() {
        Song s = songListView.getSelectionModel().getSelectedItem();
        Playlist pl = playlistComboBox.getSelectionModel().getSelectedItem();
        if (s == null) { showError("Vui lòng chọn bài hát!"); return; }
        if (pl == null) { showError("Vui lòng chọn playlist!"); return; }
        if (playlistDao.addSongToPlaylist(pl.getId(), s.getId()))
            setStatus("Đã thêm \"" + s.getTitle() + "\" vào \"" + pl.getName() + "\"");
        else showError("Không thêm được!");
    }

    @FXML private void onRemoveSongFromPlaylist() {
        Playlist pl = playlistListView.getSelectionModel().getSelectedItem();
        Song s = playlistSongsView.getSelectionModel().getSelectedItem();
        if (pl == null) { showError("Vui lòng chọn playlist!"); return; }
        if (s == null) { showError("Vui lòng chọn bài hát!"); return; }
        if (playlistDao.removeSongFromPlaylist(pl.getId(), s.getId())) { loadPlaylistSongs(pl); setStatus("Đã xóa khỏi playlist"); }
        else showError("Không xóa được!");
    }

    // ════════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════════

    private void setVisible(VBox node, boolean v) {
        if (node != null) { node.setVisible(v); node.setManaged(v); }
    }

    private void setStatus(String msg) {
        if (statusLabel != null) Platform.runLater(() -> statusLabel.setText(msg));
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }

    private boolean confirm(String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.YES, ButtonType.NO);
        a.setHeaderText(null);
        return a.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private String prompt(String title, String header, String content, String defaultVal) {
        TextInputDialog d = new TextInputDialog(defaultVal);
        d.setTitle(title); d.setHeaderText(header); d.setContentText(content);
        return d.showAndWait().orElse(null);
    }
    
    private void handlePlaybackError(String errorMessage) {
        Platform.runLater(() -> {
            showError(errorMessage);
            setStatus("Lá»'i: " + errorMessage);
            // Start manual progress timer as fallback
            if (currentSong != null) startProgressTimer();
        });
    }
    
    private String getInvidiousAudioUrl(String videoId) {
        try {
            // List of Invidious mirrors to try
            String[] invidiousServers = {
                "https://vid.puffyan.us/api/v1/videos/",
                "https://invidious.snopyta.org/api/v1/videos/",
                "https://yewtu.be/api/v1/videos/",
                "https://invidious.kavin.rocks/api/v1/videos/"
            };

            for (String server : invidiousServers) {
                try {
                    String apiUrl = server + videoId;
                    System.out.println("Trying Invidious API: " + apiUrl);

                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(apiUrl).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);

                    int responseCode = conn.getResponseCode();
                    System.out.println("Invidious response code: " + responseCode);

                    if (responseCode == 200) {
                        StringBuilder response = new StringBuilder();
                        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                response.append(line);
                            }
                        }

                        // Parse JSON to find audio URL
                        String json = response.toString();
                        System.out.println("JSON response length: " + json.length());

                        // Try to find adaptiveFormats
                        int adaptiveFormatsIndex = json.indexOf("\"adaptiveFormats\"");

                        if (adaptiveFormatsIndex != -1) {
                            System.out.println("Found adaptiveFormats at index: " + adaptiveFormatsIndex);

                            // Look for audio-only formats - improved pattern
                            String jsonSubstring = json.substring(adaptiveFormatsIndex);
                            String audioPattern = "\"url\":\"([^\"]+)\"[^}]*\"mimeType\":\"audio/";
                            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(audioPattern);
                            java.util.regex.Matcher matcher = pattern.matcher(jsonSubstring);

                            if (matcher.find()) {
                                String audioUrl = matcher.group(1).replace("\\u0026", "&").replace("\\/", "/");
                                System.out.println("Found audio URL from Invidious: " + audioUrl);
                                return audioUrl;
                            } else {
                                System.out.println("Pattern match failed, trying alternative pattern");
                                // Try alternative pattern for different JSON structure
                                String altPattern = "\"url\":\"([^\"]+)\"[^\"]*\"itag\":";
                                java.util.regex.Pattern altPatternObj = java.util.regex.Pattern.compile(altPattern);
                                java.util.regex.Matcher altMatcher = altPatternObj.matcher(jsonSubstring);

                                if (altMatcher.find()) {
                                    String audioUrl = altMatcher.group(1).replace("\\u0026", "&").replace("\\/", "/");
                                    System.out.println("Found audio URL with alternative pattern: " + audioUrl);
                                    return audioUrl;
                                }
                            }
                        } else {
                            System.out.println("No adaptiveFormats found, trying regular formats");
                            // Try regular formats if adaptiveFormats not found
                            int formatsIndex = json.indexOf("\"format\":");
                            if (formatsIndex != -1) {
                                String jsonSubstring = json.substring(formatsIndex);
                                String audioPattern = "\"url\":\"([^\"]+)\"[^\"]*\"itag\":";
                                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(audioPattern);
                                java.util.regex.Matcher matcher = pattern.matcher(jsonSubstring);

                                if (matcher.find()) {
                                    String audioUrl = matcher.group(1).replace("\\u0026", "&").replace("\\/", "/");
                                    System.out.println("Found audio URL from regular formats: " + audioUrl);
                                    return audioUrl;
                                }
                            }
                            continue;
                        }
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    System.out.println("Failed with server " + server + ": " + e.getMessage());
                    // Continue to next server
                }
            }
        } catch (Exception e) {
            System.out.println("Invidious API error: " + e.getMessage());
        }
        return null;
    }

    private void playYouTubeWithFallback(String youtubeUrl) {
        setStatus("Ðang táº£i link YouTube...");
        executor.submit(() -> {
            try {
                // Extract video ID from YouTube URL
                String videoId = extractYouTubeVideoIdFromUrl(youtubeUrl);
                if (videoId == null) {
                    handlePlaybackError("Khong the lay video ID tu URL");
                    return;
                }

                // Try Invidious API first
                String audioUrl = getInvidiousAudioUrl(videoId);

                if (audioUrl != null && !audioUrl.isEmpty()) {
                    System.out.println("Using Invidious audio URL: " + audioUrl);
                    final Song current = currentSong;
                    final int duration = current != null ? current.getDuration() : 0;
                    final String originalUrl = current != null ? current.getFilePath() : youtubeUrl;

                    Platform.runLater(() -> {
                        setStatus("Ðang kiem tra cache...");
                        downloadAndPlayQuickAudioWithKey(originalUrl, audioUrl, duration);
                    });
                    return;
                }

                // Fallback to yt-dlp
                System.out.println("Invidious failed, falling back to yt-dlp");
                String ytDlp = findYtDlp();
                if (ytDlp == null) {
                    handlePlaybackError("Cáº§n cÃ i yt-dlp Ä'á»' phÃ¡t YouTube.\n\nCháº¡y lá»'nh: pip install yt-dlp");
                    return;
                }

                ProcessBuilder pb = new ProcessBuilder(
                    ytDlp, "-g", "-f", "bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio/best",
                    "--no-playlist", "--no-warnings", "--encoding", "utf-8", youtubeUrl
                );
                pb.redirectErrorStream(true);
                Process proc = pb.start();

                // Read output with proper UTF-8 handling
                StringBuilder output = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }

                int exit = proc.waitFor();
                String result = output.toString().trim();

                // Debug output
                System.out.println("yt-dlp exit code: " + exit);
                System.out.println("yt-dlp output: " + result);

                if (exit == 0 && !result.isBlank()) {
                    // Filter out warning messages and extract clean stream URL
                    String[] lines = result.split("\n");
                    String streamUrl = null;

                    for (String line : lines) {
                        line = line.trim();
                        // Skip warning messages
                        if (line.contains("RequestsDependencyWarning") ||
                            line.contains("warnings.warn") ||
                            line.isEmpty()) {
                            continue;
                        }
                        // Look for HTTP URLs
                        if (line.startsWith("http://") || line.startsWith("https://")) {
                            streamUrl = line;
                            break;
                        }
                    }

                    if (streamUrl != null && !streamUrl.isEmpty()) {
                        System.out.println("Clean Stream URL from yt-dlp: " + streamUrl);

                        // Extract duration from URL if available
                        int duration = extractDurationFromUrl(streamUrl);
                        final Song current = currentSong; // Create final copy for lambda
                        final String finalStreamUrl = streamUrl; // Create final copy for lambda
                        if (duration > 0 && current != null) {
                            current.setDuration(duration);
                        }

                        final Song finalCurrent = current; // Create final copy for inner lambda
                        final String finalStreamUrlCopy = finalStreamUrl; // Create final copy for inner lambda
                        final int finalDuration = finalCurrent != null ? finalCurrent.getDuration() : 0; // Create final copy for inner lambda

                        Platform.runLater(() -> {
                            setStatus("Ðang kiem tra cache...");
                            // Pass original YouTube URL for cache key instead of stream URL
                            String originalUrl = finalCurrent != null ? finalCurrent.getFilePath() : finalStreamUrlCopy;
                            downloadAndPlayQuickAudioWithKey(originalUrl, finalStreamUrlCopy, finalDuration);
                        });
                    } else {
                        handlePlaybackError("Khong tim thay stream URL hop le trong output.");
                    }
                } else {
                    String errorMsg = "Khong lay duoc link stream tu YouTube (cÃ Invidious lÃ yt-dlp).\nExit code: " + exit + "\nOutput: " + result;
                    handlePlaybackError(errorMsg);
                }
            } catch (Exception e) {
                handlePlaybackError("Loi khi xu ly YouTube: " + e.getMessage());
            }
        });
    }
    
    private void playYouTubeStreamWithHeaders(String streamUrl, int durationHint) {
        try {
            System.out.println("Playing YouTube stream with headers: " + streamUrl);
            
            // Create media with custom headers for YouTube streams
            Media media = new Media(streamUrl);
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setVolume(volumeSlider != null ? volumeSlider.getValue() / 100.0 : 0.7);
            
            // Set up error handling
            mediaPlayer.setOnError(() -> {
                System.err.println("MediaPlayer error: " + mediaPlayer.getError());
                Platform.runLater(() -> {
                    setStatus("Loi MediaPlayer: " + mediaPlayer.getError().getMessage());
                    // Fallback to download method if streaming fails
                    setStatus("Chuyen sang phat file local...");
                    downloadAndPlayQuickAudio(streamUrl, durationHint);
                });
            });
            
            mediaPlayer.setOnReady(() -> {
                System.out.println("YouTube stream ready with headers");
                double total = mediaPlayer.getTotalDuration().toSeconds();
                System.out.println("Total duration: " + total + " seconds");
                if (total > 0) {
                    Platform.runLater(() -> totalTimeLabel.setText(formatTime((int) total)));
                } else if (durationHint > 0) {
                    // Use duration hint if available
                    Platform.runLater(() -> totalTimeLabel.setText(formatTime(durationHint)));
                }
            });
            
            mediaPlayer.currentTimeProperty().addListener((obs, o, n) -> {
                if (mediaPlayer == null) return;
                double total = mediaPlayer.getTotalDuration().toSeconds();
                double cur = n.toSeconds();
                progressSeconds = cur;
                Platform.runLater(() -> {
                    if (total > 0) progressBar.setProgress(cur / total);
                    currentTimeLabel.setText(formatTime((int) cur));
                });
            });
            
            mediaPlayer.setOnEndOfMedia(() -> {
                System.out.println("YouTube stream ended");
                stopPlayback();
                if (repeatOn) playSong(currentSong);
                else onNext();
            });
            
            mediaPlayer.play();
            isPlaying = true;
            playPauseBtn.setText("â¶");
            System.out.println("YouTube stream play started with headers");
            
        } catch (Exception e) {
            System.err.println("Exception creating YouTube stream player: " + e.getMessage());
            e.printStackTrace();
            setStatus("Khong the phat stream truc tiep, chuyen sang tai file...");
            // Fallback to download method
            downloadAndPlayQuickAudio(streamUrl, durationHint);
        }
    }
    
    private void playYouTubeVideo(String streamUrl, int durationHint) {
        try {
            System.out.println("Opening YouTube video in browser...");
            
            // Extract video ID from stream URL to construct YouTube video URL
            String videoId = extractYouTubeVideoId(streamUrl);
            String youtubeUrl;
            
            if (videoId != null) {
                youtubeUrl = "https://www.youtube.com/watch?v=" + videoId;
            } else {
                // Fallback: try to extract from original URL if available
                youtubeUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"; // Default fallback
            }
            
            System.out.println("Opening YouTube URL: " + youtubeUrl);
            
            // Open YouTube video in browser
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            
            if (os.contains("win")) {
                // Windows
                pb = new ProcessBuilder("cmd", "/c", "start", youtubeUrl);
            } else if (os.contains("mac")) {
                // macOS
                pb = new ProcessBuilder("open", youtubeUrl);
            } else {
                // Linux
                pb = new ProcessBuilder("xdg-open", youtubeUrl);
            }
            
            pb.start();
            Platform.runLater(() -> {
                setStatus("Ðang mo YouTube video trong browser...");
                // Start manual progress timer since we can't track external player
                startProgressTimer();
            });
            
        } catch (Exception e) {
            System.err.println("Failed to open YouTube video: " + e.getMessage());
            Platform.runLater(() -> {
                setStatus("Khong the mo browser, chuyen sang cache...");
                // Fallback to cache/download method
                downloadAndPlayQuickAudio(streamUrl, durationHint);
            });
        }
    }
    
    private void playYouTubeWithVLC(String streamUrl, int durationHint) {
        try {
            System.out.println("Playing YouTube with system media player: " + streamUrl);
            
            // Try to open with browser (works on most systems)
            try {
                String os = System.getProperty("os.name").toLowerCase();
                ProcessBuilder pb;
                
                if (os.contains("win")) {
                    // Windows
                    pb = new ProcessBuilder("cmd", "/c", "start", streamUrl);
                } else if (os.contains("mac")) {
                    // macOS
                    pb = new ProcessBuilder("open", streamUrl);
                } else {
                    // Linux
                    pb = new ProcessBuilder("xdg-open", streamUrl);
                }
                
                pb.start();
                Platform.runLater(() -> {
                    setStatus("Ðang mo voi browser...");
                    // Start manual progress timer since we can't track external player
                    startProgressTimer();
                });
                return;
            } catch (Exception e) {
                System.err.println("Failed to open browser: " + e.getMessage());
            }
            
            // Fallback: try to open with VLC directly if available
            try {
                String vlcPath = findVLCPath();
                if (vlcPath != null) {
                    ProcessBuilder pb = new ProcessBuilder(vlcPath, streamUrl);
                    pb.start();
                    Platform.runLater(() -> {
                        setStatus("Ðang phat voi VLC...");
                        startProgressTimer();
                    });
                    return;
                }
            } catch (Exception e) {
                System.err.println("VLC not found: " + e.getMessage());
            }
            
            // Final fallback to cache/download
            Platform.runLater(() -> {
                setStatus("VLC khong san sang, chuyen sang cache...");
                downloadAndPlayQuickAudio(streamUrl, durationHint);
            });
            
        } catch (Exception e) {
            System.err.println("Error in VLC playback: " + e.getMessage());
            Platform.runLater(() -> {
                setStatus("Khong the phat voi VLC, chuyen sang cache...");
                downloadAndPlayQuickAudio(streamUrl, durationHint);
            });
        }
    }
    
    private String findVLCPath() {
        // Try common VLC installation paths
        String[] possiblePaths = {
            "C:\\Program Files\\VideoLAN\\VLC\\vlc.exe",
            "C:\\Program Files (x86)\\VideoLAN\\VLC\\vlc.exe",
            "D:\\Program Files\\VideoLAN\\VLC\\vlc.exe",
            "vlc"  // If VLC is in PATH
        };
        
        for (String path : possiblePaths) {
            java.io.File vlcFile = new java.io.File(path);
            if (vlcFile.exists()) {
                return path;
            }
        }
        return null;
    }
    
    private void playYouTubeDirectStream(String streamUrl, int durationHint) {
        try {
            System.out.println("Playing YouTube direct stream: " + streamUrl);
            
            // Create media with proper headers for YouTube streams
            Media media = new Media(streamUrl);
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setVolume(volumeSlider != null ? volumeSlider.getValue() / 100.0 : 0.7);
            
            // Set up error handling with fallback
            mediaPlayer.setOnError(() -> {
                System.err.println("Direct stream error: " + mediaPlayer.getError());
                Platform.runLater(() -> {
                    setStatus("Stream truc tiep that bai, chuyen sang cache...");
                    // Fallback to cache/download method
                    downloadAndPlayQuickAudio(streamUrl, durationHint);
                });
            });
            
            mediaPlayer.setOnReady(() -> {
                System.out.println("Direct stream ready!");
                double total = mediaPlayer.getTotalDuration().toSeconds();
                System.out.println("Total duration: " + total + " seconds");
                if (total > 0) {
                    Platform.runLater(() -> totalTimeLabel.setText(formatTime((int) total)));
                } else if (durationHint > 0) {
                    Platform.runLater(() -> totalTimeLabel.setText(formatTime(durationHint)));
                }
            });
            
            mediaPlayer.currentTimeProperty().addListener((obs, o, n) -> {
                if (mediaPlayer == null) return;
                double total = mediaPlayer.getTotalDuration().toSeconds();
                double cur = n.toSeconds();
                progressSeconds = cur;
                Platform.runLater(() -> {
                    if (total > 0) progressBar.setProgress(cur / total);
                    currentTimeLabel.setText(formatTime((int) cur));
                });
            });
            
            mediaPlayer.setOnEndOfMedia(() -> {
                System.out.println("Direct stream ended");
                stopPlayback();
                if (repeatOn) playSong(currentSong);
                else onNext();
            });
            
            mediaPlayer.play();
            isPlaying = true;
            playPauseBtn.setText("â¶");
            System.out.println("Direct stream play started - NO DOWNLOAD NEEDED!");
            
        } catch (Exception e) {
            System.err.println("Exception in direct stream: " + e.getMessage());
            e.printStackTrace();
            setStatus("Khong the phat truc tiep, chuyen sang cache...");
            // Fallback to cache/download method
            downloadAndPlayQuickAudio(streamUrl, durationHint);
        }
    }
    
    private String extractYouTubeVideoId(String streamUrl) {
        // Extract video ID from YouTube stream URL
        if (streamUrl.contains("id=")) {
            String[] parts = streamUrl.split("id=");
            if (parts.length > 1) {
                String idPart = parts[1].split("&")[0];
                System.out.println("Extracted video ID from stream: " + idPart);
                return idPart;
            }
        }
        // Fallback: use hash of stream URL if no ID found
        String fallbackId = "cache_" + Math.abs(streamUrl.hashCode());
        System.out.println("Using fallback ID: " + fallbackId);
        return fallbackId;
    }
    
    private java.io.File findCachedAudioFile(java.io.File cacheDir, String videoId) {
        // Look for cached file that starts with videoId (handles .m4a.m4a, .webm, etc.)
        java.io.File[] allFiles = cacheDir.listFiles();
        if (allFiles != null) {
            for (java.io.File f : allFiles) {
                String fileName = f.getName();
                if (fileName.startsWith(videoId) && !fileName.endsWith(".part")) {
                    System.out.println("Found cached file: " + f.getAbsolutePath() + " for video ID: " + videoId);
                    return f;
                }
            }
        }
        
        // Fallback: try standard extensions
        String[] extensions = {".m4a", ".webm", ".mp4", ".mp3"};
        for (String ext : extensions) {
            java.io.File testFile = new java.io.File(cacheDir, videoId + ext);
            if (testFile.exists()) {
                System.out.println("Found cached file with standard extension: " + testFile.getAbsolutePath());
                return testFile;
            }
        }
        
        System.out.println("No cached file found for video ID: " + videoId);
        return null;
    }
    
    private void downloadAndPlayQuickAudioWithKey(String originalUrl, String streamUrl, int durationHint) {
        executor.submit(() -> {
            try {
                // Use original YouTube URL for cache key (more stable)
                String cacheKey = extractYouTubeVideoIdFromUrl(originalUrl);
                if (cacheKey == null) {
                    cacheKey = "cache_" + Math.abs(originalUrl.hashCode());
                }
                
                System.out.println("Using cache key: " + cacheKey + " for URL: " + originalUrl);
                
                // Check cache first
                java.io.File cacheDir = new java.io.File("D:/MusicApp/cache");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                
                java.io.File cachedFile = findCachedAudioFile(cacheDir, cacheKey);
                if (cachedFile != null && cachedFile.exists()) {
                    System.out.println("Found cached file: " + cachedFile.getAbsolutePath());
                    System.out.println("File size: " + cachedFile.length() + " bytes");
                    
                    Platform.runLater(() -> {
                        setStatus("Ðang phat tu cache...");
                        playMedia(cachedFile.toURI().toString(), durationHint);
                    });
                    return;
                } else {
                    System.out.println("No cached file found for key: " + cacheKey);
                }
                
                // Download and cache the file
                System.out.println("Downloading and caching new file...");
                downloadAndCacheAudio(originalUrl, streamUrl, cacheKey, cacheDir, durationHint);
                
            } catch (Exception e) {
                System.err.println("Error in cache system: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    handlePlaybackError("Loi cache system: " + e.getMessage());
                });
            }
        });
    }
    
    private String extractYouTubeVideoIdFromUrl(String url) {
        System.out.println("Extracting video ID from URL: " + url);
        
        if (url.contains("youtube.com/watch?v=")) {
            String[] parts = url.split("v=");
            if (parts.length > 1) {
                String videoId = parts[1].split("&")[0];
                System.out.println("Extracted video ID from standard URL: " + videoId);
                return videoId;
            }
        } else if (url.contains("youtu.be/")) {
            String[] parts = url.split("youtu.be/");
            if (parts.length > 1) {
                String videoId = parts[1].split("\\?")[0];
                System.out.println("Extracted video ID from short URL: " + videoId);
                return videoId;
            }
        } else if (url.contains("id=")) {
            // Try to extract from stream URL as fallback
            String[] parts = url.split("id=");
            if (parts.length > 1) {
                String videoId = parts[1].split("&")[0];
                System.out.println("Extracted video ID from stream URL: " + videoId);
                return videoId;
            }
        }
        
        // Last resort: use hash
        String fallbackId = "cache_" + Math.abs(url.hashCode());
        System.out.println("Using fallback hash ID: " + fallbackId);
        return fallbackId;
    }
    
    private java.io.File findFileByPrefix(java.io.File[] files, String prefix) {
        if (files == null) return null;
        
        for (java.io.File f : files) {
            String fileName = f.getName();
            if (fileName.startsWith(prefix) && !fileName.endsWith(".part")) {
                System.out.println("Found matching file: " + f.getAbsolutePath() + " size: " + f.length() + " bytes");
                return f;
            }
        }
        return null;
    }
    
    private void downloadAndCacheAudio(String originalUrl, String streamUrl, String cacheKey, java.io.File cacheDir, int durationHint) {
        try {
            // Use yt-dlp to download and cache
            String ytDlp = findYtDlp();
            if (ytDlp == null) {
                Platform.runLater(() -> handlePlaybackError("Can khong tim thay yt-dlp"));
                return;
            }
            
            // Create cache file with video ID
            String fileName = cacheKey + ".%(ext)s";
            java.io.File tempFile = new java.io.File(cacheDir, fileName);
            
            System.out.println("=== Download and Cache Audio ===");
            System.out.println("Cache key: " + cacheKey);
            System.out.println("Cache directory: " + cacheDir.getAbsolutePath());
            System.out.println("File name pattern: " + fileName);
            System.out.println("Downloading to: " + tempFile.getAbsolutePath());
            
            // Use yt-dlp to download original audio file
            String outputPath = tempFile.getAbsolutePath().replace(".mp3", "").replace(".%(ext)s", "");
            ProcessBuilder pb = new ProcessBuilder(
                ytDlp, "-f", "bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio/best",
                "-o", outputPath + ".%(ext)s",
                "--no-playlist", "--newline", "--no-post-overwrites", streamUrl
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            
            // Read output
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    System.out.println("yt-dlp: " + line);
                }
            }
            
            int exit = proc.waitFor();
            System.out.println("yt-dlp exit code: " + exit);
            
            if (exit == 0) {
                System.out.println("=== Finding Downloaded File ===");
                final String finalBasePath = outputPath; // Create final copy for loops
                final String finalCacheKey = cacheKey; // Create final copy for loops
                System.out.println("Base path: " + finalBasePath);
                
                // List all files in cache directory
                java.io.File[] allFiles = cacheDir.listFiles();
                if (allFiles != null) {
                    System.out.println("All files in cache after download:");
                    for (java.io.File f : allFiles) {
                        System.out.println("  - " + f.getName() + " (" + f.length() + " bytes)");
                    }
                }
                
                // Find and play the downloaded file
                // yt-dlp creates files like: cacheKey.m4a.m4a, cacheKey.webm, etc.
                java.io.File actualFile = null;
                
                // First try: look for files that start with cacheKey (reuse allFiles)
                actualFile = findFileByPrefix(allFiles, finalCacheKey);
                
                // Fallback: try standard extensions
                if (actualFile == null) {
                    String[] extensions = {".m4a", ".webm", ".mp4", ".mp3"};
                    for (String ext : extensions) {
                        java.io.File testFile = new java.io.File(finalBasePath + ext);
                        System.out.println("Testing file: " + testFile.getAbsolutePath() + " exists: " + testFile.exists());
                        if (testFile.exists()) {
                            actualFile = testFile;
                            System.out.println("Found downloaded file: " + actualFile.getAbsolutePath());
                            break;
                        }
                    }
                }
                
                if (actualFile != null && actualFile.exists()) {
                    final java.io.File finalFile = actualFile;
                    final int finalDuration = durationHint; // Create final copy for lambda
                    Platform.runLater(() -> {
                        setStatus("Ðang phat audio tu file local...");
                        playMedia(finalFile.toURI().toString(), finalDuration);
                    });
                } else {
                    Platform.runLater(() -> handlePlaybackError("Khong tim thay file sau khi download"));
                }
            } else {
                Platform.runLater(() -> handlePlaybackError("Download that bai: " + output.toString()));
            }
            
        } catch (Exception e) {
            System.err.println("Error downloading: " + e.getMessage());
            Platform.runLater(() -> handlePlaybackError("Loi khi tai: " + e.getMessage()));
        }
    }
    
    private void downloadAndPlayQuickAudio(String streamUrl, int durationHint) {
        executor.submit(() -> {
            try {
                // Extract YouTube video ID for cache key
                String videoId = extractYouTubeVideoId(streamUrl);
                if (videoId == null) {
                    videoId = "cache_" + Math.abs(streamUrl.hashCode());
                }
                
                // Check cache first
                java.io.File cacheDir = new java.io.File("D:/MusicApp/cache");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                
                java.io.File cachedFile = findCachedAudioFile(cacheDir, videoId);
                if (cachedFile != null && cachedFile.exists()) {
                    System.out.println("Found cached file: " + cachedFile.getAbsolutePath());
                    System.out.println("File size: " + cachedFile.length() + " bytes");
                    System.out.println("File URI: " + cachedFile.toURI().toString());
                    
                    Platform.runLater(() -> {
                        setStatus("Ðang phat tu cache...");
                        System.out.println("About to play cached file...");
                        playMedia(cachedFile.toURI().toString(), durationHint);
                    });
                    return;
                } else {
                    System.out.println("No cached file found for video ID: " + videoId);
                    System.out.println("Cache directory: " + cacheDir.getAbsolutePath());
                    java.io.File[] files = cacheDir.listFiles();
                    if (files != null) {
                        System.out.println("Files in cache directory:");
                        for (java.io.File f : files) {
                            System.out.println("  - " + f.getName() + " (" + f.length() + " bytes)");
                        }
                    }
                }
                
                // Use yt-dlp to extract and save audio directly
                String ytDlp = findYtDlp();
                if (ytDlp == null) {
                    Platform.runLater(() -> handlePlaybackError("Can khong tim thay yt-dlp"));
                    return;
                }
                
                // Create cache file with video ID
                String fileName = videoId + ".%(ext)s";
                java.io.File tempFile = new java.io.File(cacheDir, fileName);
                
                System.out.println("Extracting audio to: " + tempFile.getAbsolutePath());
                
                // Use yt-dlp to download original audio file (no conversion)
                String outputPath = tempFile.getAbsolutePath().replace(".mp3", "");
                ProcessBuilder pb = new ProcessBuilder(
                    ytDlp, "-f", "bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio/best",
                    "-o", outputPath + ".%(ext)s",
                    "--no-playlist", "--newline", "--no-post-overwrites", streamUrl
                );
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                
                // Read output with detailed logging
                StringBuilder output = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        System.out.println("yt-dlp: " + line); // Debug each line
                        
                        if (line.contains("100%")) {
                            Platform.runLater(() -> setStatus("Da tai xong audio..."));
                        }
                        if (line.contains("ERROR") || line.contains("ERROR:")) {
                            final String errorLine = line; // Create final copy for lambda
                            Platform.runLater(() -> setStatus("Loi yt-dlp: " + errorLine));
                        }
                        if (line.contains("Destination:")) {
                            System.out.println("File destination: " + line);
                        }
                    }
                }
                
                int exit = proc.waitFor();
                System.out.println("yt-dlp extract exit code: " + exit);
                System.out.println("yt-dlp full output: " + output.toString());
                
                if (exit == 0) {
                    // Find the actual file (yt-dlp might add different extension)
                    String basePath = tempFile.getAbsolutePath().replace(".mp3", "");
                    java.io.File actualFile = null;
                    
                    System.out.println("Looking for files with base path: " + basePath);
                    System.out.println("Cache directory: " + cacheDir.getAbsolutePath());
                    
                    // List all files in cache directory
                    java.io.File[] allFiles = cacheDir.listFiles();
                    if (allFiles != null) {
                        System.out.println("All files in cache directory:");
                        for (java.io.File f : allFiles) {
                            System.out.println("  - " + f.getName() + " (" + f.length() + " bytes)");
                        }
                    }
                    
                    // Try different possible extensions
                    String[] extensions = {".m4a", ".webm", ".mp4", ".mp3"};
                    for (String ext : extensions) {
                        java.io.File testFile = new java.io.File(basePath + ext);
                        System.out.println("Testing file: " + testFile.getAbsolutePath() + " exists: " + testFile.exists());
                        if (testFile.exists()) {
                            actualFile = testFile;
                            System.out.println("Found file: " + actualFile.getAbsolutePath());
                            break;
                        }
                    }
                    
                    if (actualFile == null) {
                        System.out.println("No file found for any extension!");
                        Platform.runLater(() -> handlePlaybackError("Khong tim thay file audio sau khi download"));
                        return;
                    }
                    
                    if (actualFile.exists()) {
                        System.out.println("Playing extracted file: " + actualFile.getAbsolutePath());
                        
                        final java.io.File finalFile = actualFile; // Create final copy for lambda
                        final int finalDuration = durationHint; // Create final copy for lambda
                        
                        Platform.runLater(() -> {
                            setStatus("Ðang phat audio tu file local...");
                            playMedia(finalFile.toURI().toString(), finalDuration);
                            
                            // Schedule immediate cleanup after playback
                            java.util.concurrent.ScheduledExecutorService cleanup = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
                            cleanup.schedule(() -> {
                                try {
                                    if (finalFile.exists()) {
                                        finalFile.delete();
                                        System.out.println("Cleaned up audio file: " + finalFile.getName());
                                    }
                                } catch (Exception e) {
                                    System.err.println("Failed to cleanup: " + e.getMessage());
                                }
                                cleanup.shutdown();
                            }, 10, java.util.concurrent.TimeUnit.MINUTES); // Cleanup after 10 minutes
                        });
                    } else {
                        Platform.runLater(() -> handlePlaybackError("Khong tim thay file audio sau khi extract"));
                    }
                } else {
                    Platform.runLater(() -> handlePlaybackError("Extract audio that bai: " + output.toString()));
                }
                
            } catch (Exception e) {
                System.err.println("Error in quick audio download: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> handlePlaybackError("Loi khi tai audio: " + e.getMessage()));
            }
        });
    }
    
    private void playYouTubeStream(String streamUrl, int durationHint) {
        try {
            System.out.println("Playing YouTube stream: " + streamUrl);
            
            // Create custom media with headers for YouTube streams
            Media media = new Media(streamUrl);
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setVolume(volumeSlider != null ? volumeSlider.getValue() / 100.0 : 0.7);
            
            // Set up error handling
            mediaPlayer.setOnError(() -> {
                System.err.println("MediaPlayer error: " + mediaPlayer.getError());
                Platform.runLater(() -> {
                    setStatus("Loi MediaPlayer: " + mediaPlayer.getError().getMessage());
                    // Fallback to manual progress timer
                    startProgressTimer();
                });
            });
            
            mediaPlayer.setOnReady(() -> {
                System.out.println("YouTube stream ready");
                double total = mediaPlayer.getTotalDuration().toSeconds();
                System.out.println("Total duration: " + total + " seconds");
                if (total > 0) {
                    Platform.runLater(() -> totalTimeLabel.setText(formatTime((int) total)));
                } else if (durationHint > 0) {
                    // Use duration hint if available
                    Platform.runLater(() -> totalTimeLabel.setText(formatTime(durationHint)));
                }
            });
            
            mediaPlayer.currentTimeProperty().addListener((obs, o, n) -> {
                if (mediaPlayer == null) return;
                double total = mediaPlayer.getTotalDuration().toSeconds();
                double cur = n.toSeconds();
                progressSeconds = cur;
                Platform.runLater(() -> {
                    if (total > 0) progressBar.setProgress(cur / total);
                    currentTimeLabel.setText(formatTime((int) cur));
                });
            });
            
            mediaPlayer.setOnEndOfMedia(() -> {
                System.out.println("YouTube stream ended");
                stopPlayback();
                if (repeatOn) playSong(currentSong);
                else onNext();
            });
            
            mediaPlayer.play();
            isPlaying = true;
            playPauseBtn.setText("â¶");
            System.out.println("YouTube stream play started");
            
        } catch (Exception e) {
            System.err.println("Exception creating YouTube stream player: " + e.getMessage());
            e.printStackTrace();
            setStatus("Khong the phat stream YouTube: " + e.getMessage());
            startProgressTimer();
        }
    }
    
    private void downloadAndPlayYouTubeAudio(String streamUrl, int durationHint) {
        executor.submit(() -> {
            try {
                // Create temp file on D drive
                java.io.File tempDir = new java.io.File("D:/MusicApp/temp");
                if (!tempDir.exists()) tempDir.mkdirs();
                
                String fileName = "youtube_audio_" + System.currentTimeMillis() + ".m4a";
                java.io.File tempFile = new java.io.File(tempDir, fileName);
                
                System.out.println("Downloading audio to: " + tempFile.getAbsolutePath());
                
                // Download file using HTTP connection
                java.net.URL url = new java.net.URL(streamUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "MusicApp/1.0");
                conn.setRequestProperty("Accept", "*/*");
                conn.connect();
                
                try (java.io.InputStream in = conn.getInputStream();
                     java.io.FileOutputStream out = new java.io.FileOutputStream(tempFile)) {
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalBytes = 0;
                    
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
                        
                        // Update progress
                        if (totalBytes % (1024 * 100) == 0) { // Update every 100KB
                            final long currentBytes = totalBytes; // Create final copy for lambda
                            Platform.runLater(() -> {
                                setStatus("Ðang tai: " + (currentBytes / 1024) + " KB");
                            });
                        }
                    }
                }
                
                conn.disconnect();
                
                System.out.println("Download completed: " + tempFile.length() + " bytes");
                
                // Play the downloaded file
                Platform.runLater(() -> {
                    setStatus("Ðang phat audio tu file local...");
                    System.out.println("Playing local file: " + tempFile.toURI().toString());
                    System.out.println("File exists: " + tempFile.exists());
                    System.out.println("File size: " + tempFile.length() + " bytes");
                    System.out.println("Duration hint: " + durationHint + " seconds");
                    
                    playMedia(tempFile.toURI().toString(), durationHint);
                    
                    // Schedule cleanup of temp file after playback
                    java.util.concurrent.ScheduledExecutorService cleanup = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
                    cleanup.schedule(() -> {
                        try {
                            if (tempFile.exists()) {
                                tempFile.delete();
                                System.out.println("Cleaned up temp file: " + tempFile.getName());
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to cleanup temp file: " + e.getMessage());
                        }
                        cleanup.shutdown();
                    }, 5, java.util.concurrent.TimeUnit.MINUTES); // Cleanup after 5 minutes
                });
                
            } catch (Exception e) {
                System.err.println("Error downloading YouTube audio: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    handlePlaybackError("Khong the tai audio tu YouTube: " + e.getMessage());
                });
            }
        });
    }
    
    private int extractDurationFromUrl(String url) {
        if (url == null || url.isEmpty()) return 0;
        
        // Look for duration parameter in URL
        String[] params = url.split("&");
        for (String param : params) {
            if (param.startsWith("dur=")) {
                try {
                    String durStr = param.substring(4); // Skip "dur="
                    double duration = Double.parseDouble(durStr);
                    return (int) Math.round(duration);
                } catch (Exception e) {
                    // Ignore parsing errors
                }
            }
        }
        return 0;
    }
    
    private String extractLyricsFromDescription(String description) {
        if (description == null || description.trim().isEmpty()) return null;
        
        // Look for common lyrics patterns in description
        String[] lyricsKeywords = {
            "Lyrics:", "Lyrics", "L:", "l:", "Paroles:", "Paroles",
            "-----", "---", "====", "***"
        };
        
        String[] lines = description.split("\n");
        StringBuilder lyrics = new StringBuilder();
        boolean foundLyrics = false;
        
        for (String line : lines) {
            line = line.trim();
            
            // Check if this line starts a lyrics section
            for (String keyword : lyricsKeywords) {
                if (line.startsWith(keyword)) {
                    foundLyrics = true;
                    if (line.length() > keyword.length()) {
                        lyrics.append(line.substring(keyword.length()).trim()).append("\n");
                    }
                    break;
                }
            }
            
            // If we found lyrics section, collect subsequent lines
            if (foundLyrics) {
                // Skip lines that look like metadata
                if (!line.contains("http") && !line.contains("www.") && 
                    !line.contains("Subscribe") && !line.contains("Follow") &&
                    !line.contains("Facebook") && !line.contains("Instagram") &&
                    !line.contains("Twitter") && !line.contains("Official") &&
                    line.length() > 0 && line.length() < 200) {
                    lyrics.append(line).append("\n");
                }
            }
        }
        
        String result = lyrics.toString().trim();
        return result.length() > 50 ? result : null; // Only return if substantial content
    }
}
