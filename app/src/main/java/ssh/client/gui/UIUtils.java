package ssh.client.gui;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;

/**
 * Utility class for common UI operations and styling.
 * Provides consistent styling and common UI component creation methods.
 */
public class UIUtils {
    
    // Common styles
    public static final String DARK_BACKGROUND_STYLE = "-fx-background-color: #2c3e50; -fx-alignment: center;";
    public static final String TITLE_STYLE = "-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #ecf0f1;";
    public static final String SUBTITLE_STYLE = "-fx-font-size: 14px; -fx-text-fill: #bdc3c7; -fx-font-style: italic;";
    public static final String LABEL_STYLE = "-fx-text-fill: #ecf0f1; -fx-font-weight: bold;";
    public static final String TEXT_FIELD_STYLE = "-fx-background-color: #34495e; -fx-text-fill: #ecf0f1; -fx-border-color: #3498db; -fx-border-radius: 3px;";
    public static final String PRIMARY_BUTTON_STYLE = "-fx-background-color: linear-gradient(to right, #3498db, #2980b9); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-padding: 12 28; -fx-font-size: 16px; -fx-effect: dropshadow(gaussian, #2980b955, 4, 0.2, 0, 2);";
    public static final String SECONDARY_BUTTON_STYLE = "-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-padding: 12 28; -fx-font-size: 16px; -fx-effect: dropshadow(gaussian, #95a5a655, 4, 0.2, 0, 2);";
    public static final String RED_BUTTON_STYLE = "-fx-background-color: linear-gradient(to right, #e74c3c, #c0392b); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-padding: 12 28; -fx-font-size: 16px; -fx-effect: dropshadow(gaussian, #c0392b55, 4, 0.2, 0, 2);";
    public static final String PROGRESS_COLOR_STYLE = "-fx-progress-color: #3498db;";
    
    /**
     * Create a styled title label.
     */
    public static Label createTitleLabel(String text) {
        Label label = new Label(text);
        label.setStyle(TITLE_STYLE);
        return label;
    }
    
    /**
     * Create a styled subtitle label.
     */
    public static Label createSubtitleLabel(String text) {
        Label label = new Label(text);
        label.setStyle(SUBTITLE_STYLE);
        return label;
    }
    
    /**
     * Create a styled regular label.
     */
    public static Label createLabel(String text) {
        Label label = new Label(text);
        label.setStyle(LABEL_STYLE);
        return label;
    }
    
    /**
     * Create a styled text field.
     */
    public static TextField createTextField(String promptText) {
        TextField field = new TextField();
        field.setStyle(TEXT_FIELD_STYLE);
        field.setPromptText(promptText);
        return field;
    }
    
    /**
     * Create a styled combo box.
     */
    public static ComboBox<String> createComboBox() {
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.setStyle(TEXT_FIELD_STYLE);
        return comboBox;
    }
    
    /**
     * Create a primary action button.
     */
    public static Button createPrimaryButton(String text) {
        Button button = new Button(text);
        button.setStyle(PRIMARY_BUTTON_STYLE);
        return button;
    }
    
    /**
     * Create a secondary action button.
     */
    public static Button createSecondaryButton(String text) {
        Button button = new Button(text);
        button.setStyle(SECONDARY_BUTTON_STYLE);
        return button;
    }
    
    /**
     * Create a red button for cancel/dangerous actions.
     */
    public static Button createRedButton(String text) {
        Button button = new Button(text);
        button.setStyle(RED_BUTTON_STYLE);
        return button;
    }
    
    /**
     * Create a styled progress indicator.
     */
    public static ProgressIndicator createProgressIndicator() {
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setStyle(PROGRESS_COLOR_STYLE);
        indicator.setPrefSize(40, 40);
        return indicator;
    }
    
    /**
     * Create a styled VBox container.
     */
    public static VBox createVBox(double spacing) {
        VBox vbox = new VBox(spacing);
        vbox.setPadding(new Insets(30));
        vbox.setStyle(DARK_BACKGROUND_STYLE);
        return vbox;
    }
    
    /**
     * Create a styled HBox container.
     */
    public static HBox createHBox(double spacing) {
        HBox hbox = new HBox(spacing);
        hbox.setAlignment(javafx.geometry.Pos.CENTER);
        return hbox;
    }
    
    /**
     * Format file size in human-readable format.
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    /**
     * Format working directory for display.
     */
    public static String formatWorkingDirectory(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "~";
        }
        
        // Replace home directory with ~
        String homeDir = System.getProperty("user.home");
        if (path.startsWith(homeDir)) {
            return "~" + path.substring(homeDir.length());
        }
        
        // Limit length for display
        if (path.length() > 50) {
            return "..." + path.substring(path.length() - 47);
        }
        
        return path;
    }
    
    /**
     * Create an icon button with tooltip and modern style.
     */
    public static Button createIconButton(String emoji, String tooltip) {
        Button button = new Button(emoji);
        button.setStyle("-fx-background-color: #23272e; -fx-text-fill: #e0e6ed; -fx-font-size: 20px; -fx-background-radius: 50%; -fx-min-width: 44px; -fx-min-height: 44px; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, #00000033, 4, 0.2, 0, 2);");
        button.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        button.setFocusTraversable(false);
        button.setOnMouseEntered(e -> button.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 20px; -fx-background-radius: 50%; -fx-min-width: 44px; -fx-min-height: 44px; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, #3498db55, 6, 0.3, 0, 2);"));
        button.setOnMouseExited(e -> button.setStyle("-fx-background-color: #23272e; -fx-text-fill: #e0e6ed; -fx-font-size: 20px; -fx-background-radius: 50%; -fx-min-width: 44px; -fx-min-height: 44px; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, #00000033, 4, 0.2, 0, 2);"));
        return button;
    }

    /**
     * Create a card-like VBox with shadow and rounded corners.
     */
    public static VBox createCardVBox(double spacing) {
        VBox vbox = new VBox(spacing);
        vbox.setPadding(new Insets(18, 18, 12, 18));
        vbox.setStyle("-fx-background-color: #23272e; -fx-background-radius: 16; -fx-effect: dropshadow(gaussian, #00000033, 12, 0.2, 0, 4);");
        return vbox;
    }
} 