import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.nio.charset.StandardCharsets;
import java.io.DataInputStream;


public class Client {
	private Socket socket;
	private PrintWriter out = null;
	private DataInputStream in = null;

	private String ip;
	private int port;
	
	
	private Client(String ip, int port) {
		this.ip = ip;
		this.port = port;
	}
	
	
	public static void main(String[] args) {
		System.out.println("Client started!");
		
		Client client = new Client("127.0.0.1", 5733);
		client.ConnectToServer();
		client.Communicate();
	}


	private String GetFormattedString(String input, String splitter, String space) {
		String output = "";

		try {
			String[] buff = input.split(splitter);

			for (int i = 0; i < buff.length; i++) {
				if (i < buff.length - 1) {
					output += buff[i] + space;
				}
				else {
					output += buff[i];
				}
			}
		}
		catch (Exception e) {
			output = input;

			System.err.println("Error: Couln't format the data right!");
		}

		return output;
	}
	

	private String FormatString(String input) {
		// Set splitter
		if (input.contains("_")) {
			input = GetFormattedString(input, "_", "--------------------------------------");
		}
		
		// Set new lines
		if (input.contains(";")) {
			input = GetFormattedString(input, ";", "\n");
		}

		// Set spacing
		if (input.contains("#")) {
			input = GetFormattedString(input, "#", "\t");
		}

		return input;
	}


	private String GetMessageFromServer() throws IOException {
		int length = in.readInt();
		byte[] message = new byte[length];

		if (length <= 0) {
			System.err.println("Error: Wrong format!");

			return null;
		} else {
			in.readFully(message, 0, message.length);

			return FormatString(new String(message, StandardCharsets.UTF_8));
		}
	}

	
	private void Communicate() {
		Scanner scanner = new Scanner(System.in);
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while(true) {
						if (socket.getInputStream().available() > 0) {
							String input = GetMessageFromServer();
							if (input != null) {
								System.out.println();
								System.out.println(input);
								System.out.println("\n");

								System.out.print("-> Please enter request: ");
							}
						}

						Thread.sleep(20);
					}
				}
				catch (Exception e) {
					// disconnected or error happend
					System.out.println("Socket closed!");
				}
			}
		}).start();

		try {
			while (true) {
				System.out.print("-> Please enter request: ");
				String scan = scanner.nextLine();

				out.println(scan);
				out.flush();

				String input = GetMessageFromServer();

				if (input != null) {
					System.out.println();
					System.out.println(input);
					System.out.println("\n");
				}
			}
		} catch (Exception e) {
			System.err.println("\nWarning: Server disconnected or error happend!");
		}
		finally {
			scanner.close();
			CloseConnection();
		}
	}
	
	
	private void CloseConnection() {
		System.out.println("Closing open connection!");

		try {
			in.close();
			out.close();
			socket.close();
		}
		catch (Exception e) {
			System.err.println("Error: Couldn't close the connection!");
		}
	}
	
	
	private void ConnectToServer() {
		boolean connected = false;

		while (!connected) {
			try {
				socket = new Socket(ip, port);

				out = new PrintWriter(socket.getOutputStream(), true);
				in = new DataInputStream(socket.getInputStream());

				connected = true;

				System.out.println("Connected to Server!\n");
			}
			catch (Exception e) {
				System.err.println("\nError: Couln't connect to Server!");

				try {
					System.out.println("-> Trying to reconnect in 3s!");
					Thread.sleep(1000);
					System.out.println("-> Trying to reconnect in 2s!");
					Thread.sleep(1000);
					System.out.println("-> Trying to reconnect in 1s!");
					Thread.sleep(1000);
				} catch (Exception exc) {
					exc.printStackTrace();
				}
			}
		}
	}
}
