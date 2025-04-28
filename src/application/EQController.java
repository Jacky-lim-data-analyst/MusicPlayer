package application;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Slider;

public class EQController implements Initializable{

    @FXML
    private Slider subBassSlider, bassSlider, lowMidsSlider, midsSlider, upperMidsSlider;
    @FXML
    private Slider presenceSlider, claritySlider, brightnessSlider, trebleSlider, airSlider;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        
    }
}
