package ssh.auth;

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
        // Check if user exists
        if (!userStore.userExists(username)) {
            return false;
        }

        // Verify password
        return userStore.verifyPassword(username, password);
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