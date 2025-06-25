import socket
import time

HOST = '127.0.0.1'  # Connect to localhost
PORT = 6000         # Change to the port you want to send to

with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
    s.connect((HOST, PORT))
    print(f"Connected to {HOST}:{PORT}")
    counter = 0
    while True:
        message = f"Hello {counter} at {time.time()}"
        s.sendall(message.encode())
        print("Sent:", message)
        counter += 1
        time.sleep(1)