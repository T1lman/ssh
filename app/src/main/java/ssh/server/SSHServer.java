package ssh.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;


public class SSHServer {


    public static void main(String[] args){

        System.out.println("SSH Server is running...");

        int port = 2222;

        try (ServerSocket serverSocket= new ServerSocket(port)){
            System.out.println("Waiting for client connection on port " + port + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress().getHostAddress());
                
                // Here you would typically start a new thread to handle the client connection
                // For simplicity, we are just closing the socket immediately
                clientSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());

        }
        
    }
}