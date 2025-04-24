import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.scene.control.ListView;

public class Server {
	int count = 1;
	ArrayList<ClientThread> clients = new ArrayList<>();
	TheServer server;
	private Consumer<Message> callback;

	public Server(Consumer<Message> call) {
		this.callback = call;
		this.server = new TheServer();
		this.server.start();
	}

	public class TheServer extends Thread {
		public void run() {
			try (ServerSocket serverSocket = new ServerSocket(5555)) {
				System.out.println("Server is waiting for a client!");

				while (true) {
					Socket socket = serverSocket.accept();
					ClientThread client = new ClientThread(socket, count);
					callback.accept(new Message(MessageType.NEWUSER, "User " + count + " has joined!", count));
					clients.add(client);
					client.start();
					count++;
				}
			} catch (Exception e) {
				callback.accept(new Message(MessageType.ERROR, "Server did not launch"));
			}
		}
	}

	class ClientThread extends Thread {
		Socket socket;
		int clientId;
		ObjectInputStream in;
		ObjectOutputStream out;

		public ClientThread(Socket s, int id) {
			this.socket = s;
			this.clientId = id;
		}

		public void broadcast(Message message) {
			for (ClientThread client : clients) {
				try {
					if (message.getRecipient() == -1 || message.getRecipient() == client.clientId) {
						client.out.writeObject(message);
					}
				} catch (Exception e) {
					System.err.println("Error sending to client " + client.clientId);
				}
			}
		}

		public void run() {
			try {
				out = new ObjectOutputStream(socket.getOutputStream());
				in = new ObjectInputStream(socket.getInputStream());
				socket.setTcpNoDelay(true);
			} catch (Exception e) {
				System.out.println("Streams not open");
				return;
			}

			broadcast(new Message(MessageType.NEWUSER, "User " + clientId + " has joined!", clientId));

			while (true) {
				try {
					Message message = (Message) in.readObject();
					callback.accept(message);
					broadcast(message);
				} catch (Exception e) {
					Message disconnectMessage = new Message(MessageType.DISCONNECT, "User " + clientId + " has disconnected!", clientId);
					callback.accept(disconnectMessage);
					broadcast(disconnectMessage);
					clients.remove(this);
					break;
				}
			}
		}
	}
}
