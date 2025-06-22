package ssh.shell;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ShellExecutorTest {

    private ShellExecutor shellExecutor;

    @BeforeEach
    public void setUp() {
        shellExecutor = new ShellExecutor();
    }

    @Test
    public void testInitialDirectoryIsUserHome() {
        assertEquals(System.getProperty("user.home"), shellExecutor.getCurrentWorkingDirectory());
    }

    @Test
    public void testCdToSubdirectory(@TempDir Path tempDir) throws IOException {
        Path subDir = tempDir.resolve("test_subdir");
        Files.createDirectory(subDir);

        shellExecutor.execute("cd " + subDir.toString());

        assertEquals(subDir.toString(), shellExecutor.getCurrentWorkingDirectory());
    }

    @Test
    public void testCdToNonexistentDirectory() {
        String nonExistentDir = "non_existent_dir_12345";
        CommandResult result = shellExecutor.execute("cd " + nonExistentDir);

        assertNotEquals(0, result.getExitCode());
        assertTrue(result.getStderr().contains("no such file or directory"));
    }

    @Test
    public void testCdToHomeWithTilde() {
        shellExecutor.execute("cd ~");
        assertEquals(System.getProperty("user.home"), shellExecutor.getCurrentWorkingDirectory());
    }

    @Test
    public void testExecuteSimpleCommand() {
        // This test is OS-dependent. 'echo' works on both Windows and Unix-like systems.
        CommandResult result = shellExecutor.execute("echo hello");
        assertEquals(0, result.getExitCode());
        assertTrue(result.getStdout().contains("hello"));
    }
} 