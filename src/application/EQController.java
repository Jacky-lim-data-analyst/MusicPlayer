package application;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.media.AudioEqualizer;
import javafx.scene.media.EqualizerBand;
import javafx.scene.media.MediaPlayer;

public class EQController implements Initializable {

    @FXML
    private Slider subBassSlider, bassSlider, lowMidsSlider, midsSlider, upperMidsSlider;
    @FXML
    private Slider presenceSlider, claritySlider, brightnessSlider, trebleSlider, airSlider;
    @FXML 
    private HBox eqController;
    @FXML
    private Button resetButton;
    @FXML
    private RadioButton bassBoostRadioButton, vocalBoostRadioButton, trebleBoostRadioButton;
    @FXML 
    private ToggleGroup presetToggleGroup;

    private MediaPlayer mediaPlayer;
    private Slider[] frequencySliders;

    // center frequencies for a 10-band EQ
    // private final double[] centerFrequencies = {31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0};
    private final double[] BASS_BOOST_GAINS = {10.0, 8.0, 6.0, 4.0, 2.0, 0.0, 0.0, 0.0, 0.0, 0.0};
    private final double[] VOCAL_BOOST_GAINS = {-2.0, -1.0, 0.0, 3.0, 6.0, 6.0, 3.0, 0.0, -1.0, -2.0};
    private final double[] TREBLE_BOOST_GAINS = {0.0, 0.0, 0.0, 0.0, 0.0, 2.0, 4.0, 6.0, 8.0, 10.0};
    
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
                if (mediaPlayer != null) {
                    AudioEqualizer audioEQ = mediaPlayer.getAudioEqualizer();
                    if (audioEQ != null && audioEQ.isEnabled() && bandIndex < audioEQ.getBands().size()) {
                        audioEQ.getBands().get(bandIndex).setGain(gain);
                    }
                }
                // -- deselect radio button if user manually adjusts a slider
                if (presetToggleGroup.getSelectedToggle() != null) {
                    presetToggleGroup.getSelectedToggle().setSelected(false);
                }
            });
        }

        // setup listener for the ToggleGroup
        presetToggleGroup.selectedToggleProperty().addListener(new ChangeListener<Toggle>() {
            @Override
            public void changed(ObservableValue<? extends Toggle> observable, Toggle oldValue, Toggle newValue) {
                if (newValue == bassBoostRadioButton) {
                    applyPreset(BASS_BOOST_GAINS);
                } else if (newValue == vocalBoostRadioButton) {
                    applyPreset(VOCAL_BOOST_GAINS);
                } else if (newValue == trebleBoostRadioButton) {
                    applyPreset(TREBLE_BOOST_GAINS);
                }
            }
        });

        // setup button action
        resetButton.setOnAction(event -> resetEqualizer());
    }

    public void setMediaPlayer(MediaPlayer mediaPlayer) {
        // pass the media player instance to this controller
        this.mediaPlayer = mediaPlayer;

        if (this.mediaPlayer != null) {
            AudioEqualizer audioEQ = this.mediaPlayer.getAudioEqualizer();

            if (audioEQ != null) {
                audioEQ.setEnabled(true);

                // ensure the number of sliders matches the number of bands available
                int numBands = Math.min(frequencySliders.length, audioEQ.getBands().size());

                // apply the current sllider values to the equalizer bands
                if (presetToggleGroup.getSelectedToggle() != null) {
                    // reapply selected preset if EQ was just enabled
                    if (presetToggleGroup.getSelectedToggle() == bassBoostRadioButton) applyPreset(BASS_BOOST_GAINS);
                    else if (presetToggleGroup.getSelectedToggle() == vocalBoostRadioButton) applyPreset(VOCAL_BOOST_GAINS);
                    else if (presetToggleGroup.getSelectedToggle() == trebleBoostRadioButton) applyPreset(TREBLE_BOOST_GAINS);
                } else {
                    for (int i = 0; i < numBands; i++) {
                        EqualizerBand band = audioEQ.getBands().get(i);

                        // set the current slider value to the band
                        double currentGain = frequencySliders[i].getValue();
                        band.setGain(currentGain);
                    }
                }
            } else {
                System.err.println("Audio equalizer is not available");
            }
        } else {
            System.err.println("Media player is not available in EQController");
        }
    }

    @FXML
    private void resetEqualizer() {
        // -- deselect any active radio button
        if (presetToggleGroup.getSelectedToggle() != null) {
            presetToggleGroup.getSelectedToggle().setSelected(false);
        }

        // reset all sliders to 0 db (update UI & set the media player equalizer)
        for (Slider slider: frequencySliders) {
            slider.setValue(0.0);
        }

        // Note: The slider listeners will automatically update the MediaPlayer's bands
        // if the equalizer is enabled and available. Explicitly setting gain here
        // would be redundant if listeners are working properly. we keep the check just in case
        // if (mediaPlayer != null) {
        //     AudioEqualizer audioEQ = mediaPlayer.getAudioEqualizer();
        //     if (audioEQ != null && audioEQ.isEnabled()) {
        //         int numBands = Math.min(frequencySliders.length, audioEQ.getBands().size());
        //         for (int i = 0; i < numBands; i++) {
        //             audioEQ.getBands().get(i).setGain(0.0);
        //         }
        //     }
        // }
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
        // determine the number of sliders to update
        int count = frequencySliders.length;
        AudioEqualizer audioEQ = null;

        if (mediaPlayer != null) {
            audioEQ = mediaPlayer.getAudioEqualizer();
            if (audioEQ != null && audioEQ.isEnabled()) {
                count = Math.min(count, audioEQ.getBands().size());
            } else {
                audioEQ = null;
            }
        }
        count = Math.min(count, gains.length);

        // set the predefined gains value for each slider
        for (int i = 0; i < count; i++) {
            frequencySliders[i].setValue(gains[i]);
        }
    }
}
