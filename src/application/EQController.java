package application;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.media.EqualizerBand;
import javafx.scene.media.MediaPlayer;

public class EQController implements Initializable{

    @FXML
    private Slider subBassSlider, bassSlider, lowMidsSlider, midsSlider, upperMidsSlider;
    @FXML
    private Slider presenceSlider, claritySlider, brightnessSlider, trebleSlider, airSlider;
    @FXML 
    private HBox eqController;
    @FXML
    private Button bassBoostButton, vocalBoostButton, trebleBoostButton, resetButton;

    private MediaPlayer mediaPlayer;
    private Slider[] frequencySliders;

    // center frequencies for a 10-band EQ
    // private final double[] centerFrequencies = {31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0};
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // initialize array for sliders
        frequencySliders = new Slider[] {
            subBassSlider, bassSlider, lowMidsSlider, midsSlider, upperMidsSlider,
            presenceSlider, claritySlider, brightnessSlider, trebleSlider, airSlider
        };

        // set up sliders with default min, max and value
        for (int i = 0; i < frequencySliders.length; i++) {
            Slider slider = frequencySliders[i];

            // EQ gain typically in range of -12dB to 12 dB
            slider.setMin(-12.0);
            slider.setMax(12.0);
            slider.setValue(0);

            Tooltip tooltip = new Tooltip("0.0 dB");
            slider.setTooltip(tooltip);

            final int bandIndex = i;
            slider.valueProperty().addListener((obs, oldValue, newValue) -> {
                // update the tooltip
                double gain = newValue.doubleValue();
                String prefix = gain > 0 ? "+" : "";
                tooltip.setText(prefix + String.format("%.1f dB", gain));

                // apply the change to music player if available
                if (mediaPlayer != null && mediaPlayer.getAudioEqualizer() != null && 
                    bandIndex < mediaPlayer.getAudioEqualizer().getBands().size()) {
                        mediaPlayer.getAudioEqualizer().getBands().get(bandIndex).setGain(gain);
                    }
            });
        }
    }

    public void setMediaPlayer(MediaPlayer mediaPlayer) {
        this.mediaPlayer = mediaPlayer;

        if (mediaPlayer != null) {
            // configure the audio equalizer
            for (int i = 0; i < Math.min(frequencySliders.length, mediaPlayer.getAudioEqualizer().getBands().size()); i++) {
                EqualizerBand band = mediaPlayer.getAudioEqualizer().getBands().get(i);

                // set the current slider value to the band
                double currentGain = frequencySliders[i].getValue();
                band.setGain(currentGain);
            }
        }
    }

    @FXML
    private void resetEqualizer() {
        // reset all sliders to 0 db (update UI & set the media player equalizer)
        for (int i = 0; i < frequencySliders.length; i++) {
            frequencySliders[i].setValue(0.0);

            if (mediaPlayer != null && mediaPlayer.getAudioEqualizer() != null &&
                i < mediaPlayer.getAudioEqualizer().getBands().size()) {
                mediaPlayer.getAudioEqualizer().getBands().get(i).setGain(0.0);
            }
        }
    }

    @FXML
    private void applyBassBoost() {
        double[] gains = {10.0, 8.0, 6.0, 4.0, 2.0, 0.0, 0.0, 0.0, 0.0, 0.0};
        applyPreset(gains);
    }

    @FXML
    private void applyTrebleBoost() {
        double[] gains = {0.0, 0.0, 0.0, 0.0, 0.0, 2.0, 4.0, 6.0, 8.0, 10.0};
        applyPreset(gains);
    }

    @FXML
    private void applyVocalBoost() {
        double[] gains = {-2.0, -1.0, 0.0, 3.0, 6.0, 6.0, 3.0, 0.0, -1.0, -2.0};
        applyPreset(gains);
    }

    private void applyPreset(double[] gains) {
        for (int i = 0; i < Math.min(frequencySliders.length, gains.length); i++) {
            frequencySliders[i].setValue(gains[i]);
        }
    }
}
