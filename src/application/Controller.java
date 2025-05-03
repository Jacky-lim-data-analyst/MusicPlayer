package application;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;
import java.util.prefs.Preferences;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.media.AudioSpectrumListener;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Controller implements Initializable, AudioSpectrumListener{
    @FXML
    private AnchorPane musicPlayerPane;
    @FXML
    private Label songLabel, durationLabel;
    @FXML
    private ProgressBar songProgressBar;
    @FXML
    private Button playButton, pauseButton, resetButton, backButton, forwardButton;
    @FXML
    private ComboBox<String> speedComboBox;
    @FXML
    private Slider volumeSlider, progressSlider;
    @FXML
    private ImageView speakerIconImageView, playMediaIcon, pauseMediaIcon, resetMediaIcon, backMediaIcon, forwardMediaIcon;
    @FXML
    private MenuBar mainMenuBar;
    @FXML
    private Menu fileMenuItem, audioEQMenu, historyMenu;
    @FXML
    private MenuItem selectFolderMenuItem, selectFileMenuItem, aboutMenuItem, exitMenuItem, openEQMenuItem;
    @FXML
    private ToggleButton repeatToggleButton, loopToggleButton;
    @FXML
    private Canvas spectrumCanvas;
    private GraphicsContext gc;

    private Media media;
    private MediaPlayer mediaPlayer;

    private File directory;
    private File[] files;
    private ArrayList<File> songs;

    private int currentSongIndex;
    private boolean isMuted = false;   // track mute state

    private Timer timer;
    private TimerTask timerTask;

    // for keyboard actions
    private long lastLeftKeyPressTime = 0;
    private long lastRightKeyPressTime = 0;
    private static final long DOUBLE_PRESS_THRESHOLD = 500;  // 500 miliseconds

    // speeds selection
    private int[] speeds = {25, 50, 75, 100, 125, 150, 175, 200};

    private boolean isPlaying = false;
    private boolean isRepatEnabled = false;
    private boolean isLoopEnabled = false;

    // scrolling timeline for song label
    private Timeline scrollTimeline;

    // new fields for EQ functionality
    private Stage eqStage;
    private EQController eqController;
    private boolean eqWindowOpen = false;

    // keep track of mp3 folders history
    private List<File> recentDirectories = new ArrayList<>();
    private static final int MAX_HISTORY = 3;

    // preference keys
    private static final String PREF_NODE_PATH = "application/musicplayer";
    private static final String PREF_KEY_HISTORY_COUNT = "recentDirCount";
    private static final String PREF_KEY_HISTORY_PREFIX = "recentDir";

    // define color themes (using CSS color strings for setStyle)
    private final String defaultAccent = "#84e89f";
    private final String defaultTextFill = "white";  // default text color

    private final String forestAccent = "#228B22";   // forest green accent
    private final String forestTextFill = "palegreen";

    private final String oceanAccent = "#1E90FF";   // ocean blue accent
    private final String oceanTextFill = "paleturquoise";  

    private final String sunsetAccent = "#FF8C00";   // orange accent
    private final String sunsetTextFill = "lightsalmon"; 

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // load history from preferences first **
        loadHistoryFromPreferences();

        selectFolderMenuItem.setOnAction(event -> {
            openFolderDialog();
        });

        selectFileMenuItem.setOnAction(this::handleSelectFiles);

        // theme menu before setting styles **
        setupThemeMenu();
        applyTheme(defaultAccent, defaultTextFill);

        // initialize the speeds combo box selection
        for (int i = 0; i < speeds.length; i++) {
            speedComboBox.getItems().add(speeds[i] + "%");
        }

        // add action listener to speed combo box
        speedComboBox.setOnAction(this::changeSpeed);

        // adjust volume from the slider
        volumeSlider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                if (mediaPlayer != null) {
                    double volumeValue = newValue.doubleValue();
                    mediaPlayer.setVolume(volumeValue / 100);

                    // update the speaker icon based on the volume level
                    if (volumeValue == 0) {
                        // show muted icon
                        insertIcon("mutesound.png", speakerIconImageView);
                        // also update mute state for consistency
                        isMuted = true;
                        if (mediaPlayer != null) {
                            mediaPlayer.setMute(true);
                        }
                    } else if (oldValue.doubleValue() == 0 && volumeValue > 0) {
                        // volume changed from zero to non-zero, show speaker icon
                        insertIcon("speaker.png", speakerIconImageView);
                        // update mute status
                        isMuted = false;
                        if (mediaPlayer != null) {
                            mediaPlayer.setMute(false);
                        }
                    }
                }
            }
        });

        // tooltip for volumeslider
        Tooltip volumeTooltip = new Tooltip();
        volumeSlider.setTooltip(volumeTooltip);

        // update tooltip text when the mouse hovers over the slider
        volumeSlider.setOnMouseMoved(event -> {
            int volumeValue = (int) volumeSlider.getValue();
            volumeTooltip.setText(volumeValue + "%");
        });

        // update tooltip text when the mouse hovers over the slider
        volumeSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            int volumeValue = newValue.intValue();
            volumeTooltip.setText(volumeValue + "%");
        });

        // set progress bar property
        songProgressBar.setStyle("-fx-accent: #84e89f");
        // setup pro
        setupProgressSliderClickHandler();

        // prevents the slider from updating the song position while a song is playing
        // It only updates the UI to match the current playback position
        progressSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!progressSlider.isValueChanging()) {
                // double totalDuration = mediaPlayer.getTotalDuration().toSeconds();
                double sliderValue = progressSlider.getValue() / 100.0;
                songProgressBar.setProgress(sliderValue);
            }
        });

        // add listener for proper mouse behaviors around slider
        progressSlider.setOnMousePressed(event -> {
            if (mediaPlayer != null) {
                mediaPlayer.pause();

                // calculate the position based on the click location
                double mouseX = event.getX();
                double width = progressSlider.getWidth();
                double percentage = mouseX / width;

                progressSlider.setValue(percentage * 100);

                // seek to the position in the media
                double seekTime = mediaPlayer.getTotalDuration().toSeconds() * percentage;
                mediaPlayer.seek(Duration.seconds(seekTime));
            }
        });

        progressSlider.setOnMouseReleased(event -> {
            // playMediaAtTheTime();
            if (mediaPlayer != null && isPlaying) {
                mediaPlayer.play();
            }
        });

        // set up icons
        insertIcon("play.png", playMediaIcon);
        insertIcon("pause.png", pauseMediaIcon);
        insertIcon("reset.png", resetMediaIcon);
        insertIcon("backward.png", backMediaIcon);
        insertIcon("forward.png", forwardMediaIcon);
        insertIcon("speaker.png", speakerIconImageView);
        speakerIconImageView.setOnMouseClicked(event -> {
            toggleMute();
        });

        // about menu item handling
        aboutMenuItem.setOnAction(event -> {
            handleOpenHelpWindow();
        });

        // handle app quit
        exitMenuItem.setOnAction(this::handleExit);

        // setup keyboard shortcuts
        setupKeyboardShortcuts();

        // loop and repeat behaviors
        setupRepeatAndLoopButtons();

        // setup EQ menu
        openEQMenuItem.setOnAction(event -> {
            openEQWindow();
        });

        // setup history menu
        // historyMenu = null;
        setupHistoryMenu();

        // Initialize GraphicsContext for spectrum drawing
        gc = spectrumCanvas.getGraphicsContext2D();
    }

    @Override
    public void spectrumDataUpdate(double timestamp, double duration, float[] magnitudes, float[] phases) {
        double canvasWidth = spectrumCanvas.getWidth();
        double canvasHeight = spectrumCanvas.getHeight();
        int numBands = magnitudes.length;
        double barWidth = canvasWidth / numBands;
        double threshold = mediaPlayer.getAudioSpectrumThreshold();

        // clear the canvas and set bar color
        gc.clearRect(0, 0, canvasWidth, canvasHeight);
        // gc.setFill(Color.GREEN);

        // // draw vertical bars for each frequency band
        // for (int i = 0; i < numBands; i++) {
        //     double magnitude = magnitudes[i];
        //     double height = (threshold - magnitude) * canvasHeight / threshold;
        //     double x = i * barWidth;
        //     double y = canvasHeight - height;
        //     gc.fillRect(x, y, barWidth, height);
        // }
        // create a gradient effect for bars based on frequency/magnitude
        for (int i = 0; i < numBands; i++) {
            double magnitude = magnitudes[i];

            // calculate normalized height
            double normalizedHeight = (threshold - magnitude) / threshold;
            double height = normalizedHeight * canvasHeight;

            // position calculations
            double x = i * barWidth;
            double y = canvasHeight - height;

            // create color gradient based on magnitude and position
            // higher frequency will be greenish
            // lower frequency will be blue
            double hue = 240 - (180.0 * i / numBands);
            double brightness = 0.7 + (normalizedHeight * 0.3);   // brighter for louder sound
            double saturation = 0.8 + (normalizedHeight * 0.2);   // more saturated for louder sound

            Color barColor = Color.hsb(hue, saturation, brightness);

            // apply glow effect
            if (normalizedHeight > 0.6) {
                gc.setGlobalAlpha(0.7);
                // glow effect
                gc.setFill(barColor.deriveColor(0, 1, 1, 0.5));
                gc.fillRect(x - 1, y - 2, barWidth + 2, height + 4);
                gc.setGlobalAlpha(1.0);
            }

            gc.setFill(barColor);

            // draw the bar with rounded top
            gc.fillRoundRect(x, canvasHeight - height, barWidth - 1, height, barWidth / 2, 3);
        }
    }

    private void setAudioSpectrum(MediaPlayer mediaPlayer) {
        // configure MediaPlayer for audio spectrum
        mediaPlayer.setAudioSpectrumListener(this);
        mediaPlayer.setAudioSpectrumInterval(0.1);
        mediaPlayer.setAudioSpectrumNumBands(20);
        mediaPlayer.setAudioSpectrumThreshold(-60);
    }

    private void setupThemeMenu() {
        Menu themeMenu = new Menu("Themes");   // create the main "Themes" menu

        // create MenuItem for the Default theme
        MenuItem defaultThemeItem = new MenuItem("Default");
        defaultThemeItem.setOnAction(event -> applyTheme(defaultAccent, defaultTextFill));

        // create menuItem for the forest green theme
        MenuItem forestThemeItem = new MenuItem("Forest Green");
        forestThemeItem.setOnAction(event -> applyTheme(forestAccent, forestTextFill));

        // create menuItem for the ocean blue theme
        MenuItem oceanThemeItem = new MenuItem("Ocean Blue");
        oceanThemeItem.setOnAction(event -> applyTheme(oceanAccent, oceanTextFill));

        // create menuItem for the sunset orange theme
        MenuItem sunsetThemeItem = new MenuItem("Sunset Orange");
        sunsetThemeItem.setOnAction(event -> applyTheme(sunsetAccent, sunsetTextFill));

        // add all theme menuItems to the Themes Menu
        themeMenu.getItems().addAll(defaultThemeItem, forestThemeItem, oceanThemeItem, sunsetThemeItem);

        // add the Themes menu to the main menu bar
        if (mainMenuBar != null) {
            int historyMenuIndex = -1;
            for (int i = 0; i < mainMenuBar.getMenus().size(); i++) {
                if (mainMenuBar.getMenus().get(i).getText().equals("History")) {
                    historyMenuIndex = i;
                    break;
                }
            }
            if (historyMenuIndex != -1) {
                // insert before history menu
                mainMenuBar.getMenus().add(historyMenuIndex, themeMenu);
            } else {
                mainMenuBar.getMenus().add(themeMenu);
            }
        } else {
            System.err.println("Error: main menu bar not found for theme menu");
        }
    }

    private void applyTheme(String accentColor, String textFillColor) {
        // apply text color to the song Label
        if (songLabel != null) {
            // use setStyle to the song label
            songLabel.setStyle("-fx-text-fill: " + textFillColor + ";");
        } else {
            System.err.println("Warning: songLabel is null during theme apply");
        }

        // apply accent color to progress bar
        if (songProgressBar != null) {
            songProgressBar.setStyle("-fx-accent: " + accentColor + ";");
        } else {
            System.err.println("Warning: songProgressbar is null during theme apply");
        }

        // apply accent color to progress slider
        if (progressSlider != null) {
            progressSlider.setStyle("-fx-background-color: " + accentColor + ";");
            progressSlider.setStyle("-fx-background-radius: 4px");
        } else {
            System.err.println("Warning: progressSlider is null during theme apply");
        }
    }

    // get songs and play
    private void startPlayingFirstSong() {
        // stop and dispose of media player
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }

        // set current song index
        currentSongIndex = 0;
        // reset media player
        media = new Media(songs.get(currentSongIndex).toURI().toString());
        mediaPlayer = new MediaPlayer(media);

        setAudioSpectrum(mediaPlayer);

        songLabel.setText(songs.get(currentSongIndex).getName());

        setupMediaPlayerEndOfMediaBahavior();

        mediaPlayer.setOnReady(() -> {
            // time tracking during playback
            String totalTime = formatTime(mediaPlayer.getTotalDuration().toSeconds());
            durationLabel.setText("0:00 / " + totalTime);
            // Scrolling title animation
            setupScrollingTitle();
            // set media player **
            if (eqWindowOpen && eqController != null) {
                eqController.setMediaPlayer(mediaPlayer);
            }
        });
        // play
        playMedia();
    } 

    // handle folder selection
    public void openFolderDialog() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select music folder");

        // get the stage from any node in the scene
        Stage stage = (Stage) mainMenuBar.getScene().getWindow();
        
        // show the folder selection dialog
        File selectedDirectory = directoryChooser.showDialog(stage);

        if (selectedDirectory != null) {
            // clear existing songs
            songs = new ArrayList<File>();

            // get the new directory
            directory = selectedDirectory;

            // load the files into the song playlist
            loadSongsFromDirectory();

            // if songs were found, start playing the first one
            if (!songs.isEmpty()) {
                // add the selected directory with MP3 files to history 
                addToHistory(selectedDirectory);
                startPlayingFirstSong();
            } else {
                // no songs found on the directory
                songLabel.setText("No supported audio files found");
                if (mediaPlayer != null) {
                    mediaPlayer.stop();
                    mediaPlayer.dispose();
                    mediaPlayer = null;
                }
                songProgressBar.setProgress(0.0);
                progressSlider.setValue(0.0);
                durationLabel.setText("0:00 / 0:00");
            }
        }
    }

    @FXML
    private void handleSelectFiles(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Music Files");
        fileChooser.getExtensionFilters().addAll(
            new ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.aif", "*.aiff"),
            new ExtensionFilter("MP3 Files", "*.mp3"),
            new ExtensionFilter("WAV Files", "*.wav"),
            new ExtensionFilter("AIFF Files", "*.aif", "*.aiff")
        );

        // get the stage from any node in the scene
        Stage stage = (Stage) mainMenuBar.getScene().getWindow();

        // show the file selection dialog and allow multiple selections
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(stage);

        // when user really select mp3 files
        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            // clear any songs if any
            if (songs == null) {
                songs = new ArrayList<>();
            } else {
                songs.clear();
            }

            // add selected files to the songs list
            songs.addAll(selectedFiles);

            startPlayingFirstSong();
        }
    }

    private void loadSongsFromDirectory() {
        files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && 
                    (file.getName().toLowerCase().endsWith(".mp3") || 
                    file.getName().toLowerCase().endsWith(".wav") ||
                    file.getName().toLowerCase().endsWith(".aif") ||
                    file.getName().toLowerCase().endsWith(".aiff"))) {
                    songs.add(file);
                }
            }
        }
    }

    // history menu
    private void setupHistoryMenu() {
        // clear any existing menu items
        historyMenu.getItems().clear();
        

        // add menu items for recent directories
        if (recentDirectories.isEmpty()) {
            MenuItem noHistoryItem = new MenuItem("No recent folders");
            noHistoryItem.setDisable(true);
            historyMenu.getItems().add(noHistoryItem);
        } else {
            for (File dir: recentDirectories) {
                // handle directory that is missing / modified by user
                if (dir.exists() && dir.isDirectory()) {
                    MenuItem historyItem = new MenuItem(dir.getName());
                    historyItem.setOnAction(event -> {
                        directory = dir;
                        loadSongsFromDirectory();
                        if (!songs.isEmpty()) {
                            startPlayingFirstSong();
                        } else {
                            songLabel.setText("No audio files found");
                            if (mediaPlayer != null) {
                                mediaPlayer.stop();
                                mediaPlayer.dispose();
                                mediaPlayer = null;
                            }
                            // update UI
                            songProgressBar.setProgress(0.0);
                            progressSlider.setValue(0.0);
                            durationLabel.setText("0:00 / 0:00");
                        }
                    });
                    historyMenu.getItems().add(historyItem);
                } else {
                    MenuItem invalidItem = new MenuItem(dir.getName() + " (not found)");
                    invalidItem.setDisable(true);
                    historyMenu.getItems().add(invalidItem);
                }
            }

            // add separator and clear history option
            historyMenu.getItems().add(new SeparatorMenuItem());
            MenuItem clearHistory = new MenuItem("Clear History");
            clearHistory.setOnAction(event -> {
                recentDirectories.clear();
                // -- also clear preferences **
                clearHistoryPreferences();
                setupHistoryMenu();
            });
            historyMenu.getItems().add(clearHistory);
        }
    }

    private void addToHistory(File dir) {
        // remove the directory if it already exists in history to avoid duplicates
        recentDirectories.removeIf(d -> d.getAbsolutePath().equals(dir.getAbsolutePath()));

        // add the recent directories to the beginning of the list
        recentDirectories.add(0, dir);

        // trim the list if it exceeds the max size
        if (recentDirectories.size() > MAX_HISTORY) {
            recentDirectories = recentDirectories.subList(0, MAX_HISTORY);
        }

        // ** save the updated history to preferences
        saveHistoryToPreferences();

        setupHistoryMenu();
    }

    private void loadHistoryFromPreferences() {
        Preferences prefs = Preferences.userRoot().node(PREF_NODE_PATH);
        int count = prefs.getInt(PREF_KEY_HISTORY_COUNT, 0);
        recentDirectories.clear();  // start fresh

        for (int i = 0; i < count && i < MAX_HISTORY; i++) {
            String path = prefs.get(PREF_KEY_HISTORY_PREFIX + i, null);
            if (path != null) {
                File dir = new File(path);
                recentDirectories.add(dir);
            }
        }
    }

    // save history
    private void saveHistoryToPreferences() {
        Preferences prefs = Preferences.userRoot().node(PREF_NODE_PATH);
        prefs.putInt(PREF_KEY_HISTORY_COUNT, recentDirectories.size());
        for (int i = 0; i < recentDirectories.size(); i++) {
            prefs.put(PREF_KEY_HISTORY_PREFIX + i, recentDirectories.get(i).getAbsolutePath());
        }
    }

    // clear history
    private void clearHistoryPreferences() {
        Preferences prefs = Preferences.userRoot().node(PREF_NODE_PATH);
        prefs.putInt(PREF_KEY_HISTORY_COUNT, 0);  // set count to 0
        // clear all history
        for (int i = 0; i < MAX_HISTORY; i++) {
            prefs.remove(PREF_KEY_HISTORY_PREFIX + i);
        }
    }

    // handle help / about page
    @FXML
    private void handleOpenHelpWindow() {
        try {
        // load aboutApp.fxml
            FXMLLoader loader = new FXMLLoader(getClass().getResource("aboutApp.fxml"));
            AnchorPane helpWindowRoot = loader.load();

            // create a new stage for this window
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);   // Block interaction with main window

            stage.setTitle("Help Window");
            stage.setScene(new Scene(helpWindowRoot));
            stage.show();
        } catch (IOException e) {
            System.err.println("Error loading aboutApp.fxml: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Error when opening help window: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // open EQ window
    private void openEQWindow() {
        if (eqWindowOpen) {
            eqStage.requestFocus();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("graphicEQ.fxml"));
            AnchorPane eqPane = loader.load();

            // get the controller
            eqController = loader.getController();

            // pass the media to the controller
            if (mediaPlayer != null) {
                // set media player
                eqController.setMediaPlayer(mediaPlayer);
            }

            eqStage = new Stage();
            eqStage.initModality(Modality.NONE);   // non-modal so user can interact with main window
            eqStage.setTitle("Audio Equalizer");
            eqStage.setScene(new Scene(eqPane));
            eqStage.setResizable(false);

            // set windows closed behavior
            eqStage.setOnHidden(e -> {
                eqWindowOpen = false;
            });

            eqWindowOpen = true;
            eqStage.show();
        } catch (IOException e) {
            System.err.println("Error loading graphicEQ.fxml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void insertIcon(String iconPath, ImageView appImageView) {
        try {
            // URL resourceUrl = getClass().getResource("../resources/images/");
            // System.out.println("Resource URL: " + resourceUrl);

            Image image = new Image(getClass().getResourceAsStream("/application/resources/images/" + iconPath));
            appImageView.setImage(image);

            // make the image fit into the imageView
            appImageView.setFitWidth(appImageView.getFitWidth());
            appImageView.setFitHeight(appImageView.getFitHeight());

            // preserve the aspect ratio
            appImageView.setPreserveRatio(true);

            // Image quality scaling down
            appImageView.setSmooth(true);
        }
        catch (NullPointerException e) {
            System.err.println("Error loading image: image file not found");
        }
        catch (Exception e) {
            System.err.println("Something unexpected happens while loading speaker icon: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupScrollingTitle() {
        // stop any existing animation
        if (scrollTimeline != null) {
            scrollTimeline.stop();
        }

        // reset the label position
        songLabel.setTranslateX(0);

        // wait until UI loaded and start a background thread
        Platform.runLater(() -> {
            double labelWidth = songLabel.prefWidth(-1);
            double paneWidth = musicPlayerPane.getWidth();

            // create scrolling animation
            scrollTimeline = new Timeline();

            // start from initial position
            KeyValue kv1 = new KeyValue(songLabel.translateXProperty(), 0);
            KeyFrame kf1 = new KeyFrame(Duration.ZERO, kv1);

            // move to the left so the end of the text aligns with the right edge
            // use max width to ensure scrolling even for short titles
            double distance = Math.max(labelWidth, paneWidth) - paneWidth + 30;
            KeyValue kv2 = new KeyValue(songLabel.translateXProperty(), distance);
            KeyFrame kf2 = new KeyFrame(Duration.seconds(5), kv2);

            // wait a moment
            KeyFrame kf3 = new KeyFrame(Duration.seconds(6));

            // jump back to start
            KeyValue kv4 = new KeyValue(songLabel.translateXProperty(), 0);
            KeyFrame kf4 = new KeyFrame(Duration.seconds(6.1), kv4);

            // wait at a moment at the start
            KeyFrame kf5 = new KeyFrame(Duration.seconds(7));

            scrollTimeline.getKeyFrames().addAll(kf1, kf2, kf3, kf4, kf5);
            scrollTimeline.setCycleCount(Timeline.INDEFINITE);
            scrollTimeline.play();
        });
    }

    private String formatTime(double seconds) {
        int minutes = (int) (seconds / 60);
        int remainingSeconds = (int) (seconds % 60);
        return String.format("%d:%02d", minutes, remainingSeconds);
    }

    public void playMedia() {
        beginTimer();
        changeSpeed(null);
        mediaPlayer.setVolume(volumeSlider.getValue() / 100);
        mediaPlayer.play();

        // ensure scrolling is active when playing
        if (scrollTimeline != null && scrollTimeline.getStatus() != Timeline.Status.RUNNING) {
            scrollTimeline.play();
        }
    }

    public void pauseMedia() {
        cancelTimer();
        mediaPlayer.pause();
    }

    public void resetMedia() {
        mediaPlayer.stop();
        mediaPlayer.seek(Duration.millis(0.0));
        isPlaying = false;
        songLabel.setText(songs.get(currentSongIndex).getName());
        songProgressBar.setProgress(0.0);
        progressSlider.setValue(0.0);

        // reset duration label
        String totalTime = formatTime(mediaPlayer.getTotalDuration().toSeconds());
        durationLabel.setText("0:00 / " + totalTime);

        // reset scrolling 
        setupScrollingTitle();
    }

    private void updateMediaPlayer() {
        mediaPlayer.stop();
        mediaPlayer.dispose();

        if (isPlaying) {
            cancelTimer();
        }

        media = new Media(songs.get(currentSongIndex).toURI().toString());
        mediaPlayer = new MediaPlayer(media);
        
        setAudioSpectrum(mediaPlayer);

        songLabel.setText(songs.get(currentSongIndex).getName());

        // add this line to ensure repeat and loop behavior
        setupMediaPlayerEndOfMediaBahavior();

        mediaPlayer.setOnReady(() -> {
            String totalTime = formatTime(mediaPlayer.getTotalDuration().toSeconds());
            durationLabel.setText("0:00 / " + totalTime);

            // setup current mute status
            mediaPlayer.setMute(isMuted);

            // reset scrolling 
            setupScrollingTitle();

            // update the EQ controller with the new media player
            if (eqWindowOpen && eqController != null) {
                // set media player
                eqController.setMediaPlayer(mediaPlayer);
            }
        });

        playMedia();
    }

    public void backMedia() {
        if (currentSongIndex > 0) {
            currentSongIndex--;
        } else {
            currentSongIndex = songs.size() - 1;
        }
        updateMediaPlayer();
    }

    public void forwardMedia() {
        if (currentSongIndex < songs.size() - 1) {
            currentSongIndex++;
        } else {
            currentSongIndex = 0;
        }
        updateMediaPlayer();
    }

    public void playMediaAtTheTime() {
        if (mediaPlayer != null) {
            double sliderValue = progressSlider.getValue() / 100.0;
            double seekTime = mediaPlayer.getTotalDuration().toSeconds() * sliderValue;

            // update progress bar
            songProgressBar.setProgress(sliderValue);

            // update the duration label
            String currentTimeStr = formatTime(seekTime);
            String totalTimeStr = formatTime(mediaPlayer.getTotalDuration().toSeconds());
            durationLabel.setText(currentTimeStr + " / " + totalTimeStr);

            mediaPlayer.seek(Duration.seconds(seekTime));
        }
    }

    public void toggleMute() {
        if (mediaPlayer != null) {
            isMuted = !isMuted;

            if (isMuted) {
                // store the current volume before muting
                mediaPlayer.setMute(true);
                // change icon to muted speaker
                insertIcon("mutesound.png", speakerIconImageView);
            } else {
                mediaPlayer.setMute(false);
                // change icon back to speaker icon
                insertIcon("speaker.png", speakerIconImageView);
            }
        }
    }

    public void changeSpeed(ActionEvent event) {
        // to handle potential null from speedcombo box
        if (speedComboBox.getValue() == null) {
            mediaPlayer.setRate(1);
        } else {
            double rate = Integer.parseInt(speedComboBox.getValue().replace("%", ""));
            mediaPlayer.setRate(rate / 100);
        }
    }

    public void beginTimer() {
        timer = new Timer();

        timerTask = new TimerTask() {
            @Override
            public void run() {
                isPlaying = true;
                double currentTime = mediaPlayer.getCurrentTime().toSeconds();
                double endTime = mediaPlayer.getTotalDuration().toSeconds();
                String totalTimeStr = formatTime(endTime);

                Platform.runLater(() -> {
                    // only update slider position if user isn't current dragging the slider
                    if (!progressSlider.isValueChanging()) {
                        songProgressBar.setProgress(currentTime / endTime);
                        progressSlider.setValue((currentTime / endTime) * 100);
                    }

                    // In real-time, update the duration label with current time
                    String currentTimeStr = formatTime(currentTime);
                    
                    durationLabel.setText(currentTimeStr + " / " + totalTimeStr);
                });
                // this is important for the media player to keep playing songs 
                if (currentTime >= endTime) {
                    cancelTimer();
                }
            }
        };

        timer.scheduleAtFixedRate(timerTask, 0, 1000);
    }

    public void cancelTimer() {
        isPlaying = false;
        timer.cancel();
    }

    // setup repeat and loop buttons
    private void setupRepeatAndLoopButtons() {
        // setup initial button states
        // repeatToggleButton.setStyle("-fx-background-color: #d3d3d3;");
        // loopToggleButton.setStyle("-fx-background-color: #d3d3d3;");

        // set tooltip texts
        repeatToggleButton.setTooltip(new Tooltip("Repeat current song"));
        loopToggleButton.setTooltip(new Tooltip("Loop playlist"));

        // set up action for repeat button
        repeatToggleButton.selectedProperty().addListener((observable, oldValue, newValue) -> {
            isRepatEnabled = newValue;

            if (isRepatEnabled) {
                // enable repeat mode and disable loop mode
                repeatToggleButton.setStyle("-fx-background-color: #84e89f;");
                loopToggleButton.setStyle("-fx-background-color: #d3d3d3;");
                loopToggleButton.setDisable(true);

                if (loopToggleButton.isSelected()) {
                    loopToggleButton.setSelected(false);
                }
                isLoopEnabled = false;
            } else {
                repeatToggleButton.setStyle("-fx-background-color: #d3d3d3;");
                loopToggleButton.setDisable(false);
            }
        });

        // set up action for repeat button
        loopToggleButton.selectedProperty().addListener((observable, oldValue, newValue) -> {
            isLoopEnabled = newValue;

            if (isLoopEnabled) {
                loopToggleButton.setStyle("-fx-background-color: #84e89f;");
                repeatToggleButton.setStyle("-fx-background-color: #d3d3d3;");
                repeatToggleButton.setDisable(true);
                
                if (repeatToggleButton.isSelected()) {
                    repeatToggleButton.setSelected(false);
                }
                isRepatEnabled = false;
            } else {
                loopToggleButton.setStyle("-fx-background-color: #d3d3d3;");
                repeatToggleButton.setDisable(false);
        }});
    }

    private void setupProgressSliderClickHandler() {
        songProgressBar.setOnMouseClicked(event -> {
            if (mediaPlayer != null) {
                double mouseX = event.getX();
                double width = songProgressBar.getWidth();

                double percentage = mouseX / width;

                percentage = Math.min(1, Math.max(0, percentage));

                // update UI
                songProgressBar.setProgress(percentage);
                progressSlider.setValue(percentage * 100);

                // pause, seek and resume if was playing
                boolean wasPlaying = isPlaying;
                mediaPlayer.pause();

                // seek to the position
                double seekTime = mediaPlayer.getTotalDuration().toSeconds() * percentage;
                mediaPlayer.seek(Duration.seconds(seekTime));

                // update the duration label
                String currentTimeStr = formatTime(seekTime);
                String totalTimeStr = formatTime(mediaPlayer.getTotalDuration().toSeconds());
                durationLabel.setText(currentTimeStr + " / " + totalTimeStr);

                if (wasPlaying) {
                    mediaPlayer.play();
                }
            }
        });
    }

    private void setupMediaPlayerEndOfMediaBahavior() {
        mediaPlayer.setOnEndOfMedia(() -> {
            if (isRepatEnabled) {
                // repeat the current song
                mediaPlayer.seek(Duration.ZERO);
                mediaPlayer.play();
            } else if (isLoopEnabled && currentSongIndex == songs.size() - 1) {
                // go back to the first song
                currentSongIndex = 0;
                updateMediaPlayer();
            } else {
                // normal behavior
                if (currentSongIndex < songs.size() - 1) {
                    forwardMedia();
                } else {
                    resetMedia();
                }
            }
        });
    }

    // skip forward or backward
    private void skipForwardBackward(int seconds, boolean isForward) {
        if (mediaPlayer != null) {
            Duration currentTime = mediaPlayer.getCurrentTime();
            Duration totalDuration = mediaPlayer.getTotalDuration();

            Duration newPosition;
            if (isForward) {
                newPosition = currentTime.add(Duration.seconds(seconds));
                if (newPosition.compareTo(totalDuration) > 0) {
                    newPosition = totalDuration;
                }
            } else {
                newPosition = currentTime.subtract(Duration.seconds(seconds));
                if (newPosition.toMillis() < 0) {
                    newPosition = Duration.ZERO;
                }
            }

            // seek to new position
            mediaPlayer.seek(newPosition);

            // update UI to reflect new position
            double currentSeconds = newPosition.toSeconds();
            double totalSeconds = totalDuration.toSeconds();
            double percentage = currentSeconds / totalSeconds;

            // update progress indicators
            songProgressBar.setProgress(percentage);
            progressSlider.setValue(percentage * 100);

            // update the duration label
            String currentTimeStr = formatTime(currentSeconds);
            String totalTimeStr = formatTime(totalSeconds);
            durationLabel.setText(currentTimeStr + " / " + totalTimeStr);
        }
    }

    // setup keyboard press events
    private void setupKeyboardShortcuts() {
        musicPlayerPane.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (mediaPlayer == null) {
                        return;  // exit if no media player
                    }

                    // check for key combinations
                    if (event.getCode() == KeyCode.K && !event.isShiftDown()) {
                        // 'K' Key -> play
                        if (!isPlaying) {
                            playMedia();
                            isPlaying = true;
                        }
                        event.consume();
                    } else if (event.getCode() == KeyCode.K && event.isShiftDown()) {
                        // 'Shift + K' -> pause
                        if (isPlaying) {
                            pauseMedia();
                            isPlaying = false;
                        }
                        event.consume();
                    } else if (event.getCode() == KeyCode.M) {
                        toggleMute();
                        event.consume();
                    } else if (event.getCode() == KeyCode.LEFT) {
                        // check for double press (LEFT ARROW)
                        long currentTime = System.currentTimeMillis();

                        // double press time within threshold
                        if (currentTime - lastLeftKeyPressTime <= DOUBLE_PRESS_THRESHOLD) {
                            // SKIP BACKWARD 5 s **
                            skipForwardBackward(5, false);
                            lastLeftKeyPressTime = 0;
                            event.consume();
                        } else {
                            // not a double press or first press
                            // update the last left key press time
                            lastLeftKeyPressTime = currentTime;
                            if (event.isShiftDown()) {
                                backMedia();
                                event.consume();
                            }
                        }
                        // reset other key press time
                        lastRightKeyPressTime = 0;
                    } else if (event.getCode() == KeyCode.RIGHT) {
                        // check for double press (RIGHT ARROW)
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastRightKeyPressTime <= DOUBLE_PRESS_THRESHOLD) {
                            // skip 5 s **
                            lastRightKeyPressTime = 0;
                            skipForwardBackward(5, true);
                            event.consume();
                        } else {
                            lastRightKeyPressTime = currentTime;
                            if (event.isShiftDown()) {
                                forwardMedia();
                                event.consume();
                            }
                        }
                        lastLeftKeyPressTime = 0;
                    } else if (event.getCode() == KeyCode.R) {
                        resetMedia();
                        playMedia();
                        event.consume();
                    }
                });
            }
        });
    }

    @FXML
    private void handleExit(ActionEvent event) {
        Platform.exit();
        System.exit(0);
    }
}
