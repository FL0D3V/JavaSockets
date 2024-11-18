import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;
import java.nio.charset.StandardCharsets;
import java.io.DataOutputStream;
import java.io.FileWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.Date;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.Scanner;
import java.util.Arrays;


public class SocketServer {
	public static void main(String[] args) {
		Logger.StartLog("Server started!");

		SocketServer.SetPort(5733);
		SocketServer.SetMaxInactivityTime(60 * 2);	// seconds

		SocketServer.Start();
	}


	private static final int DEFAULT_PORT = 9999;

	private static SocketServer socketServer = null;

	private static ServerSocket serverSocket = null;
	private static int port;
	private static boolean isStoppingServer = false;


	private static final Command[] Commands = new Command[] {
		new Command("EXIT", "\t\tStops the server and closes all open connections!"),
		new Command("HELP", "\t\tShows all available commands!"),
		new Command("BROADCAST", "\tUsage: BROADCAST MESSAGE -> Sends a broadcast message to all clients!"),
		new Command("KICKALL", "\tKicks all connected clients!"),
		new Command("KICKID", "\t\tUsage: KICKID ID -> Kicks the client with a specific id!"),
		new Command("LIST", "\t\tLists all currently connected clients!"),
	};



	private SocketServer(int port) {
		this.port = port;
	}


	public static SocketServer Instance() {
		if (socketServer == null) {
			socketServer = new SocketServer(DEFAULT_PORT); // default port of server!
		}

		return socketServer;
	}


	private static int GetCommandCount() {
		return Commands.length;
	}

	private static String GetCommandAt(int i) {
		if (i < 0 || i >= GetCommandCount()) {
			return null;
		}

		return Commands[i].getCommand().toLowerCase();
	}

	private static Command GetCommand(int i) {
		if (i < 0 || i >= GetCommandCount()) {
			return null;
		}

		return Commands[i];
	}


	public static void Start() {
		try {
			StartBackgroundService();

			serverSocket = new ServerSocket(port);
			serverSocket.setReuseAddress(true);

			ClientService.StartService();

			while (!ClientService.GetIsError() && !isStoppingServer) {
				Socket client = null;
				
				try {
					client = serverSocket.accept();
				}
				catch (Exception e) {
					System.out.println("Socket got closed!");
					break;
				}

				// Adding client to service!
				ClientHandler model = ClientService.AddClient(client);

				// Send welcome message!
				model.SendToClient(String.format("Welcome Client! ID: %s", model.GetClient().GetClientIdentifier()), false);

				// Client connected!
				Logger.LogInfo(String.format("New client connected: %s, ID: %s", model.GetClient().GetConnectionString(), model.GetClient().GetClientIdentifier()));
			}
		} catch (IOException e) {
			ClientService.SetIsUsingList(false);
			ClientService.SetIsError(true);

			// Port in use!
			e.printStackTrace();
		}
	}


	private static void CloseConnections() {
		try {
			System.out.println("Closing connections!");

			ClientService.StopService();
			serverSocket.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}


	private static void StartBackgroundService() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				Scanner sc = new Scanner(System.in);
				
				try {
					String input = null;
					
					while (!ClientService.GetIsError() && !isStoppingServer)
					{
						try {
							String command = sc.nextLine();

							String[] subcommand = command.split(" ");
							subcommand[0] = subcommand[0].toLowerCase();
							
							if (command == null || subcommand == null) {
								throw new Exception("Not a command!");
							}


							// ************************
							// *					  *
							// *  Checking commands!  *
							// *					  *
							// ************************

							if (subcommand.length == 1 && subcommand[0].equals(GetCommandAt(0))) {			// stops the server!
								break;
							}
							else if (subcommand.length == 1 && subcommand[0].equals(GetCommandAt(1))) {		// help
								System.out.println("--- all commands ---");
								for (int i = 0; i < GetCommandCount(); i++) {
									System.out.println(GetCommand(i).toString());
								}
							}
							else if (subcommand[0].equals(GetCommandAt(2))) {								// sends a broadcast to all clients!
								if (subcommand.length > 2) {
									ClientService.Broadcast(ArrayUtils.JoinFromIndex(subcommand, " ", 1));
								}
								else {
									// wrong usage!
									System.out.println(String.format("Wrong usage! -> Usage: %s TEXT", GetCommandAt(2).toUpperCase()));
									continue;
								}
							}
							else if (subcommand[0].equals(GetCommandAt(3))) {								// kicks all clients
								if (subcommand.length == 1) {
									ClientService.KickAllClients();
								}
								else {
									ClientService.KickAllClients(ArrayUtils.JoinFromIndex(subcommand, " ", 1));
								}
							}
							else if (subcommand[0].equals(GetCommandAt(4))) {								// kicks a specific client
								if (subcommand.length == 2) {
									int id = 0;
									boolean wrongRequest = false;

									try {
										id = Integer.parseInt(subcommand[1]);
									} catch (Exception e) {
										// Error: Not a number!
										wrongRequest = true;
									}

									if (wrongRequest) {
										System.out.println(String.format("Not a number! -> Usage: %s ID", GetCommandAt(4).toUpperCase()));
										continue;
									}

									ClientService.KickClientWithId(id, ArrayUtils.JoinFromIndex(subcommand, " ", 1));
								}
								else {
									// wrong usage!
									System.out.println(String.format("Wrong usage! -> Usage: %s ID", GetCommandAt(4).toUpperCase()));
									continue;
								}
							}
							else if (subcommand.length == 1 && subcommand[0].equals(GetCommandAt(5))) {		// lists all clients
								if (ClientService.GetClientCount() > 0) {
									System.out.println("--- all clients ---");
									ClientService.PrintAllClients();
								}
								else {
									System.out.println("No clients connected!");
								}
							}
							else {
								System.out.println(String.format("Server: \"%s\" is not a command!", command));
							}


							// ****************************
							// *					      *
							// *  End checking commands!  *
							// *					      *
							// ****************************
						}
						catch (Exception e) {
							Logger.LogError(e.getMessage());
						}
					}

					isStoppingServer = true;
				}
				catch (Exception e) {
					isStoppingServer = true;

					e.printStackTrace();
				}
				finally {
					sc.close();
					CloseConnections();

					System.out.println("Stopping Server!");
				}
			}
		}).start();
	}


	public static void SetMaxInactivityTime(int seconds) {
		ClientService.SetInactivityTime(seconds);
	}

	public static void SetPort(int _port) {
		if (_port <= 0) {
			return;
		}

		port = _port;
	}

	public static int GetPort() {
		return port;
	}
}


class ClientService implements Runnable {
	private static ClientService clientService = null;
	private static Vector<ClientHandler> allAvailableClients = null;
	private static boolean isError;
	private static boolean isUsingList;
	private static boolean isRunning;
	private static boolean isStopped;
	private static int maxInactivityTime;	// in seconds... if "0", then there is no auto kicking!


	private ClientService() {
		this.allAvailableClients = new Vector<ClientHandler>();
		this.isError = false;
		this.isRunning = true;
		this.isStopped = false;
		this.isUsingList = false;
	}


	public static ClientService Instance() {
		if (clientService == null) {
			clientService = new ClientService();
		}
		return clientService;
	}

	public static void StartService(int maxInactivityTime) {
		if (!GetIsRunning()) {
			SetIsStopped(false);
			SetInactivityTime(maxInactivityTime);

			new Thread(Instance()).start();
		}
	}

	public static void StartService() {
		if (!GetIsRunning()) {
			SetIsStopped(false);

			new Thread(Instance()).start();
		}
	}

	public static void StopService() {
		SetIsStopped(true);

		SetIsUsingList(true);

		for (int i = 0; i < allAvailableClients.size(); i++) {
			allAvailableClients.get(i).StopClient();
		}

		SetIsUsingList(false);
	}

	public static ClientHandler AddClient(Socket client) {
		ClientHandler clientHandler = new ClientHandler(client);

		Instance().AddNewClient(clientHandler);

		new Thread(clientHandler).start();
		
		return clientHandler;
	}


	@Override
	public void run() {
		try {
			while(!isError && !isStopped) {
				if (!isUsingList) {
					CheckClients();
				}
				
				// Delay this thread to resource limit this thread! Better for low cpu usage!
				Thread.sleep(500);
			}
		}
		catch (Exception e) {
			isRunning = false;
			isError = true;
			isStopped = true;

			e.printStackTrace();
		}
	}


	public static void Broadcast(String message) {
		int count = GetClientCount();

		if (count == 0) {
			Logger.LogInfo("Server-Broadcast: No clients connected!");
			return;
		}

		String newMessage = String.format("Broadcast from server: %s", message);

		SetIsUsingList(true);
		
		for (int i = 0; i < count; i++) {
			allAvailableClients.get(i).SendToClient(newMessage, true);
		}

		SetIsUsingList(false);

		Logger.LogInfo(String.format("Server-Broadcast: Sending \"%s\" to %s clients!", message, count));
	}


	private void CheckClients() {
		for (int i = GetClientCount() - 1; i >= 0; i--) {
			ClientHandler ch = allAvailableClients.get(i);

			boolean isDisconnected = CheckClientsIfDisconnected(ch);
			boolean isInActive = CheckClientsForInactivity(ch, maxInactivityTime);

			if (isDisconnected || isInActive) {
				// remove this client from the list!
				allAvailableClients.remove(i);

				Logger.LogInfo(String.format("Server: Client \"%s\" with id: \"%s\" got removed from the list!", ch.GetClient().GetConnectionString(), ch.GetClient().GetClientIdentifier()));
			}
		}
	}

	private boolean CheckClientsIfDisconnected(ClientHandler ch) {
		if (!ch.GetConnectionState()) {
			return true;
		}

		return false; 	// Still connected!
	}

	private boolean CheckClientsForInactivity(ClientHandler ch, int inactivityTime) {
		if (inactivityTime <= 0) {
			return false;	// No kicking!
		}

		Calendar previous = Calendar.getInstance();
		previous.setTime(ch.GetLastActionToServerTime());
		
		Calendar now = Calendar.getInstance();
		long diff = now.getTimeInMillis() - previous.getTimeInMillis();

		if(diff >= inactivityTime * 1000)	// seconds
		{
			ch.DisconnectClient(ClientHandler.KickingReasons.INACTIVITY);
			return true;
		}

		return false; 	// Still active!
	}


	private static void AddNewClient(ClientHandler clientHandler) {
		if (clientHandler == null) {
			return;
		}

		SetIsUsingList(true);

		allAvailableClients.add(clientHandler);

		SetIsUsingList(false);
	}

	public static void KickClientWithId(int id, String message) {
		if (!CheckForKick()) return;

		SetIsUsingList(true);

		for (int i = 0; i < GetClientCount(); i++) {
			ClientHandler handler = allAvailableClients.get(i);

			if (handler.GetClient().GetId() == id) {
				// Found client!
				handler.DisconnectClient(message);

				Logger.LogInfo(String.format("Kicked client with id: %s!", id));
				return;
			}
		}

		SetIsUsingList(false);

		// Didn't found client!
		Logger.LogWarning(String.format("Couln't find a client with id: %s!", id));
	}

	public static void KickAllClients() {
		if (!CheckForKick()) return;

		int count = GetClientCount();

		SetIsUsingList(true);
		
		for (int i = 0; i < count; i++)
			allAvailableClients.get(i).DisconnectClient(ClientHandler.KickingReasons.OTHER);

		SetIsUsingList(false);

		Logger.LogInfo(String.format("Kicked %s clients!", count));
	}

	public static void KickAllClients(String message) {
		if (!CheckForKick()) return;

		int count = GetClientCount();

		SetIsUsingList(true);

		for (int i = 0; i < count; i++)
			allAvailableClients.get(i).DisconnectClient(message);

		SetIsUsingList(false);
		
		Logger.LogInfo(String.format("Kicked %s clients!", count));
	}

	private static boolean CheckForKick() {
		if (GetClientCount() == 0) {
			Logger.LogInfo("Server-KickAll: No clients there to kick!");
			return false;
		}
		return true;
	}

	public static void PrintAllClients() {
		SetIsUsingList(true);

		for (ClientHandler ch : allAvailableClients) {
			System.out.println(String.format("%s", ch.GetClient().toString()));
		}

		SetIsUsingList(false);
	}

	public static int GetClientCount() {
		return allAvailableClients.size();
	}

	public static void SetIsError(boolean _isError) {
		isError = _isError;
	}
	
	public static boolean GetIsError() {
		return isError;
	}

	public static void SetIsUsingList(boolean _isUsingList) {
		isUsingList = _isUsingList;
	}

	public static boolean GetIsUsingList() {
		return isUsingList;
	}

	public static boolean GetIsRunning() {
		return isRunning;
	}

	public static void SetIsRunning(boolean _isRunning) {
		isRunning = _isRunning;
	}

	public static boolean GetIsStopped() {
		return isStopped;
	}

	public static void SetIsStopped(boolean _isStopped) {
		isStopped = _isStopped;
	}

	public static void SetInactivityTime(int _maxInactivityTime) {
		maxInactivityTime = _maxInactivityTime;
	}

	public static int GetInactivityTime() {
		return maxInactivityTime;
	}
}


class ClientHandler implements Runnable {
	public enum KickingReasons {
		INACTIVITY,
		OTHER,
	}

	private static final String BAD_REQUEST_STRING = "Bad Request!";


	private static Vector<MenuModel> tickets;

	private ClientModel clientModel;

	private DataOutputStream out = null;
	private BufferedReader in = null;

	private boolean gotDisconnected = false;
	private boolean isConnected = true;
	private Date connectionTime;
	private Date lastActionToServer;
	private String disconnectReason = "";
	private boolean isStopped = false;


	private static final Command[] AvailableCommands = new Command[] {
		new Command("GETMENUS", "#Returns all menus!"),
		new Command("GETMENU", 	"#Usage: GETMENU \'Menu Titel\' \'Count\' -> Without the \"\'\".;##Returns the new Menu-Count of the selected Menu and the calculated price!"),
		new Command("EXIT", 	"##Closes the connection to the server!"),
		new Command("HELP", 	"##Shows this info page!"),
	};



	public ClientHandler(Socket socket) {
		if (tickets == null) {
			tickets = InitMenus();
		}

		clientModel = new ClientModel(socket);

		try {
			out = new DataOutputStream(clientModel.GetSocket().getOutputStream());
			in = new BufferedReader(new InputStreamReader(clientModel.GetSocket().getInputStream()));
		}
		catch (Exception e) {
			// error while initializing streams!
			e.printStackTrace();
		}

		connectionTime = new Date();
		lastActionToServer = new Date();
	}



	private Vector<MenuModel> InitMenus() {
		Vector<MenuModel> models = new Vector<MenuModel>();
		models.add(new MenuModel("Kaerntnernudel", 	9.00f,	23));
		models.add(new MenuModel("Wienerschnitzel",	10.00f,	25));
		models.add(new MenuModel("Fitnessteller", 	8.50f,	4));
		models.add(new MenuModel("Kaiserschmarn", 	7.00f,	6));
		return models;
	}


	private String GetCommandAt(int index) {
		if (index < 0 || index >= AvailableCommands.length) {
			return null;
		}
		return AvailableCommands[index].getCommand().toLowerCase();
	}

	private Command GetCommand(int index) {
		if (index < 0 || index >= AvailableCommands.length) {
			return null;
		}
		return AvailableCommands[index];
	}

	private int GetCommandCount() {
		return AvailableCommands.length;
	}


	public void SendToClient(String data, boolean isBroadcast) {
		if (!isBroadcast) {
			lastActionToServer = Calendar.getInstance().getTime();
		}

		try {
			byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
			int messageLength = bytes.length;

			out.writeInt(messageLength);
			out.write(bytes);
			out.flush();
		}
		catch (Exception e) {
			isConnected = false;

			Logger.LogError("Server: Client not reachable! Couln't send data!");
		}
	}

	public void DisconnectClient(KickingReasons reason) {
		String message = GetKickingReasonString(reason);

		SetDisconnectReason(message);
		SendToClient(message, false);
		
		gotDisconnected = true;
	}

	public void DisconnectClient(String message) {
		String newMessage = String.format("You got kicked for this reason: %s", message);

		SetDisconnectReason(newMessage);
		SendToClient(newMessage, false);
		
		gotDisconnected = true;
	}

	private String GetKickingReasonString(KickingReasons reason) {
		switch (reason) {
			case INACTIVITY:
				return "You got kicked for inactivity!";

			case OTHER:
				return "You got kicked for an unknown reason!";

			default:
				return null;
		}
	}

	public boolean GetConnectionState() {
		return isConnected;
	}

	public Date GetConnectionTime() {
		return connectionTime;
	}
	
	public Date GetLastActionToServerTime() {
		return lastActionToServer;
	}


	public void SetDisconnectReason(String disconnectReason) {
		this.disconnectReason = disconnectReason;
	}

	private String GetDisconnectReason() {
		return disconnectReason;
	}

	public void StopClient() {
		isStopped = true;
	}

	public ClientModel GetClient() {
		return clientModel;
	}



	@Override
	public void run()
	{
		try {
			boolean error = false;

			while(!error && !gotDisconnected && isConnected && !isStopped) {
				try {
					String output_string = "";
					String input_string = "";


					boolean stop = false;
					while (clientModel.GetSocket().getInputStream().available() <= 0) {
						if (error || gotDisconnected || !isConnected || isStopped) {
							stop = true;
							break;
						}

						Thread.sleep(20);
					}

					if (stop) {
						break;
					}

					// Read data from client!
					input_string = in.readLine().toLowerCase();
					String[] subcommand = input_string.split(" ");


					if (input_string == null || subcommand == null) {
						// ERROR!
					}


					// ************************
					// *                      *
					// *  Checking Commands!  *
					// *                      *
					// ************************

					if (subcommand.length == 1 && subcommand[0].equals(GetCommandAt(0))) {				// command: getmenus
						// Return all menus!

						output_string = "Menu name##Price##Portions;";

						for (MenuModel m : tickets) {
							output_string += m.toString() + ";";
						}
						output_string = output_string.substring(0, output_string.lastIndexOf(';'));
					}
					else if (subcommand[0].equals(GetCommandAt(1))) {									// command: getmenu NAME COUNT
						if (subcommand.length < 3) {
							// wrong usage!
							output_string = String.format("Wrong usage! -> Usage: %s NAME COUNT", GetCommandAt(1).toUpperCase());
						}
						else {
							String name = subcommand[1];
							int portions = 0;

							boolean wrongRequest = false;

							try {
								portions = Integer.parseInt(subcommand[2]);
							} catch (Exception e) {
								// Error: Not a number!
								wrongRequest = true;
							}

							if (!wrongRequest && portions > 0) {
								boolean foundEntry = false;

								for (int i = 0; i < tickets.size(); i++) {
									if (tickets.get(i).getName().toLowerCase().equals(name)) {
										MenuModel model = tickets.get(i);
										
										int newPortions = model.getAvailablePortions() - portions;
										
										if (newPortions >= 0) {
											model.setAvailablePortions(newPortions);
											tickets.set(i, model);
											
											String menuName = model.getName();
											int availablePortions = model.getAvailablePortions();
											int allPortions = availablePortions + portions;
											float totalPrice = model.getPrice() * portions;

											output_string = String.format("Selected menu:##%s;;", menuName);
											output_string += String.format("Available portions:#%s;", allPortions);
											output_string += String.format("Ordered portions:#%s;", portions);
											output_string += "_;";
											output_string += String.format("New portions:##%s;;", availablePortions);
											output_string += String.format("Total price:##%s%s", totalPrice, Constants.PriceTag);
										}
										else {
											output_string = "SOLD OUT!";
										}

										foundEntry = true;
										
										break;
									}
								}

								if (!foundEntry) {
									output_string = String.format("No menu found with name: %s", name);
								}
							} else {
								// Wrong usage!
								output_string = String.format("Not a number! -> Usage: %s NAME COUNT", GetCommandAt(1).toUpperCase());
							}
						}
					}
					else if (subcommand.length == 1 && subcommand[0].equals(GetCommandAt(2))) {				// command: exit
						// Goodbye!

						output_string = "Goodbye Client!";

						SendToClient(output_string, false);
						return;
					}
					else if (subcommand.length == 1 && subcommand[0].equals(GetCommandAt(3))) {				// command: help
						// HELP
						
						for (int i = 0; i < GetCommandCount(); i++) {
							output_string += GetCommand(i).toString() + ";";
						}
					}
					else {															// wrong command or nothing!
						output_string = BAD_REQUEST_STRING;
					}

					// Send response to client!
					SendToClient(output_string, false);

					Logger.LogInfo(String.format("Client %s: %s", clientModel.GetConnectionString(), input_string));
				}
				catch (Exception e) {
					error = true;

					// Maybe disconnected!
					Logger.LogWarning("Server: Client disconnected or error happend!");
				}
			}


			//	************************
			//	*                      *
			//	*  Checking Commands!  *
			//	*                      *
			//	************************

			if (gotDisconnected) { // Check if client got disconnected by the server!
				Logger.LogInfo(String.format("Server: Client got disconnected by the server! Reason: %s", GetDisconnectReason()));
			}
		}
		catch (Exception e) {
			// Error happend!
			Logger.LogError(e.getMessage());
		}
		finally {
			Logger.LogInfo(String.format("Server: Goodbye Client: %s", clientModel.GetConnectionString()));

			try {
				if (out != null) out.close();
				if (in != null) in.close();

				if (!clientModel.CloseConnection())
					throw new Exception("Couln't close connection!");
			}
			catch (Exception e) {
				Logger.LogError("Server: Couldn't close connection!");
			}

			isConnected = false;
		}
	}
}



class Logger {
	private static SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH-mm-ss");
	private static String startOfServer = "";


	public enum Type {
		START,
		INFO,
		WARNING,
		ERROR
	}


	private static String GetTimestamp() {
		return dateFormat.format(Calendar.getInstance().getTime());
	}

	private static String GetFilename() {
		return "Logs/" + String.format("%s-Logfile.log", startOfServer);
	}

	private static void CheckFolder(String filename) {
		try {
			Path pathToFile = Paths.get(filename);
			Files.createDirectories(pathToFile.getParent());
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static File CreateFile(String filename) {
		CheckFolder(filename);

		try {
			File file = new File(filename);

			if (file.createNewFile()) {
				// file created
			} else {
				// file allready exists
			}

			return file;
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	private static void SaveToLog(String output, boolean append) {
		try {
			File file = CreateFile(GetFilename());

			if (file == null) {
				throw new Exception("File not there!");
			}

			FileWriter myWriter = new FileWriter(file, append);
			myWriter.write(output);
			myWriter.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static String GetText(String text, Type type, boolean isStart) {
		if (isStart && startOfServer.length() == 0) {
			startOfServer = GetTimestamp();
		}

		String output = String.format("%s %s: %s\n", GetTimestamp(), StringUtils.Capitalize(type.toString()), text);
		SaveToLog(output, true);

		return output;
	}


	public static void StartLog(String text) {
		System.out.print(GetText(text, Type.START, true));
	}

	public static void LogInfo(String text) {
		System.out.print(GetText(text, Type.INFO, false));
	}

	public static void LogWarning(String text) {
		System.out.print(GetText(text, Type.WARNING, false));
	}

	public static void LogError(String text) {
		System.err.print(GetText(text, Type.ERROR, false));
	}
}



class MenuModel {
	private String name;
	private float price;
	private int availablePortions;
	
	
	public MenuModel(String name, float price, int availablePortions) {
		this.name = name;
		this.price = price;
		this.availablePortions = availablePortions;
	}


	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public float getPrice() {
		return price;
	}

	public void setPrice(float price) {
		this.price = price;
	}

	public int getAvailablePortions() {
		return availablePortions;
	}

	public void setAvailablePortions(int availablePortions) {
		this.availablePortions = availablePortions;
	}

	
	@Override
	public String toString() {
		return String.format("%s##%s%s##%s", getName(), getPrice(), Constants.PriceTag, getAvailablePortions());
	}
}



class Command {
	private String command;
	private String description;


	public Command(String command, String description) {
		this.command = command;
		this.description = description;
	}


	public String getCommand() {
		return command;
	}

	public void setCommand(String command) {
		this.command = command;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}


	@Override
	public String toString() {
		return String.format("%s %s", getCommand(), getDescription());
	}
}



class ClientModel {
	private static int counter = 0;
	
	private int id;
	private Socket clientSocket = null;


	public ClientModel(Socket socket) {
		id = counter++;
		clientSocket = socket;
	}


	public String GetConnectionString() {
		return String.format("%s at %s", clientSocket.getInetAddress().getHostAddress(), clientSocket.getPort());
	}

	public Socket GetSocket() {
		return clientSocket;
	}

	public boolean CloseConnection() {
		try {
			clientSocket.close();
			return true;
		}
		catch (Exception e) {
			return false;
		}
	}


	public int GetId() {
		return id;
	}

	public String GetClientIdentifier() {
		return String.format("Client-%s", GetId());
	}


	@Override
	public String toString() {
		return String.format("Connection: %s, ID: %s", GetConnectionString(), GetClientIdentifier());
	}
}



class Constants {
	public static final String PriceTag = ",-";
}


class ArrayUtils {
	public static String JoinFromIndex(String[] array, String spacer, int index) {
		if (index < 0 || index >= array.length) {
			// wrong index!
			return null;
		}

		String[] newArray = Arrays.copyOfRange(array, index, array.length);

		return String.join(spacer, newArray);
	}
}


class StringUtils {
	public static String Capitalize(String text) {
		if (text == null || text.length() == 0) {
			// empty or null!
			return null;
		}

		return Replace(text.toLowerCase(), 0, (text.charAt(0) + "").toUpperCase().charAt(0));
	}

	public static String Replace(String str, int index, char replace){     
	    if (str == null) {
	        return str;
	    }
	    else if (index < 0 || index >= str.length()){
	        return str;
	    }

	    char[] chars = str.toCharArray();
	    chars[index] = replace;
	    return String.valueOf(chars);       
	}
}