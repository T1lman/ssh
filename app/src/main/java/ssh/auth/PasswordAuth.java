package ssh.auth;

import ssh.utils.Logger;

/**
 * Handles password authentication.
 */
public class PasswordAuth {
    private UserStore userStore;

    public PasswordAuth(UserStore userStore) {
        this.userStore = userStore;
    }

    /**
     * Authenticate a user using password.
     */
    public boolean authenticate(String username, String password) {
        Logger.info("PasswordAuth: Checking if user exists: " + username);
        // Check if user exists
        if (!userStore.userExists(username)) {
            Logger.error("PasswordAuth: User does not exist: " + username);
            return false;
        }

        Logger.info("PasswordAuth: User exists, verifying password for: " + username);
        // Verify password
        boolean result = userStore.verifyPassword(username, password);
        Logger.info("PasswordAuth: Password verification result for " + username + ": " + result);
        return result;
    }

    /**
     * Add a new user with password.
     */
    public void addUser(String username, String password) {
        userStore.addUser(username, password);
    }

    /**
     * Change a user's password.
     */
    public void changePassword(String username, String newPassword) {
        userStore.changePassword(username, newPassword);
    }

    /**
     * Check if a user exists.
     */
    public boolean userExists(String username) {
        return userStore.userExists(username);
    }

    /**
     * Remove a user.
     */
    public void removeUser(String username) {
        userStore.removeUser(username);
    }
} 