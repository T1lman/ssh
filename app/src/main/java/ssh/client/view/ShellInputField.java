package ssh.client.view;

import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import java.util.function.Consumer;

/**
 * Custom input field that behaves like a shell prompt.
 * Provides shell-like behavior with a non-editable prompt prefix.
 */
public class ShellInputField extends TextField {
    private String prompt;
    private int promptLength;
    private Consumer<String> onCommandEntered;
    
    public ShellInputField() {
        super();
        setupShellBehavior();
    }
    
    private void setupShellBehavior() {
        // Handle key events to maintain shell-like behavior
        this.setOnKeyPressed(this::handleKeyPress);
        this.setOnKeyTyped(this::handleKeyTyped);
    }
    
    private void handleKeyPress(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            // Don't handle ENTER here, let the parent handle it
            return;
        }
        
        if (event.getCode() == KeyCode.BACK_SPACE) {
            // Prevent backspace from deleting the prompt
            if (getCaretPosition() <= promptLength) {
                event.consume();
            }
        }
        
        if (event.getCode() == KeyCode.DELETE) {
            // Prevent delete from affecting the prompt
            if (getCaretPosition() < promptLength) {
                event.consume();
            }
        }
        
        if (event.getCode() == KeyCode.LEFT) {
            // Prevent moving cursor into the prompt area
            if (getCaretPosition() <= promptLength) {
                event.consume();
            }
        }
        
        if (event.getCode() == KeyCode.HOME) {
            // Move to start of command (after prompt)
            event.consume();
            positionCaret(promptLength);
        }
        
        // Handle Ctrl+A to select all text (but not the prompt)
        if (event.isControlDown() && event.getCode() == KeyCode.A) {
            event.consume();
            selectRange(promptLength, getText().length());
        }
    }
    
    private void handleKeyTyped(KeyEvent event) {
        // Prevent typing into the prompt area
        if (getCaretPosition() < promptLength) {
            event.consume();
        }
    }
    
    public void setPrompt(String prompt) {
        this.prompt = prompt;
        this.promptLength = prompt.length();
        updateDisplay();
    }
    
    public String getCommand() {
        String fullText = getText();
        if (fullText.length() > promptLength) {
            return fullText.substring(promptLength);
        }
        return "";
    }
    
    public void clearCommand() {
        setText(prompt);
        positionCaret(promptLength);
    }
    
    private void updateDisplay() {
        String currentCommand = getCommand();
        setText(prompt + currentCommand);
        positionCaret(promptLength + currentCommand.length());
    }
    
    @Override
    public void replaceText(int start, int end, String text) {
        // Prevent replacing text in the prompt area
        if (start < promptLength) {
            start = promptLength;
        }
        if (end < promptLength) {
            end = promptLength;
        }
        super.replaceText(start, end, text);
    }
    
    @Override
    public void replaceSelection(String replacement) {
        // Prevent replacing selection if it includes the prompt
        if (getCaretPosition() < promptLength) {
            return;
        }
        super.replaceSelection(replacement);
    }

    public void setOnCommandEntered(Consumer<String> onCommandEntered) {
        this.onCommandEntered = onCommandEntered;
    }

    public String getPrompt() {
        return prompt;
    }
} 