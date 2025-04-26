package application;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ResourceBundle;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ListView;

public class AboutController implements Initializable{

    @FXML
    private ListView<String> shortcutsListView;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadShortcuts();
    }

    private void loadShortcuts() {
        ObservableList<String> shortcutItems = FXCollections.observableArrayList();

        String resourcePath = "/application/resources/text/shortcuts.txt";

        // input stream from the text file
        InputStream inputStream = getClass().getResourceAsStream(resourcePath);

        if (inputStream == null) {
            System.err.println("Error: cannot find the help file at " + resourcePath);
            shortcutItems.add("Error loading shortcuts.");   // provide feedback at UI
        } else {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {   // avoid adding empty lines
                        shortcutItems.add(line.trim());
                    }
                }
            } catch (IOException e) {
                System.err.println("Error reading help file: " + e.getMessage());
                e.printStackTrace();
                shortcutItems.add("Error reading help file");
            }
        }

        // populate the list view
        if (shortcutsListView != null) {
            shortcutsListView.setItems(shortcutItems);
        } else {
            System.err.println("Error: ListView is null");
        }
    }
}
