import java.net.*;
import java.io.*;
import java.util.*;

class connection extends Thread
{
	protected Socket client;
	protected BufferedReader in;
	protected PrintStream out;
	protected server server;
	protected String nickname;
	private volatile Thread connect;

	public connection(server server, Socket client)
	{
		this.server=server;
		this.client=client;

		try
		{
			in = new BufferedReader(new InputStreamReader( client.getInputStream() ));
			out = new PrintStream(client.getOutputStream());
		} catch (IOException e)
		{
			try { client.close(); } catch (IOException e2) {} ;
			System.err.println("Fehler beim Erzeugen der Streams: " + e);
			return;
		}
		
		// zufälligen Benutzernamen erstellen und testen ob schon vorhanden
		String newnick;
		do {
			newnick = "USER" + (int) (Math.random() * 1000);
		} while( server.userExists(newnick) || newnick.equals("Server") );
		nickname = newnick;

		connect = new Thread(this);
		connect.start();
	}
	
	public void sendServerMsg(String line)
	{
		out.println("Server: "+ line);
	}
	
	public String getNickname()
	{
		return nickname;
	}
	
	public void done()
	{
		// Zeugs um Thread zu stoppen
		System.out.println("Stop the connection Thread of " + nickname);

		try {
			client.close();
		} catch (IOException e) {
			System.err.println("Fehler beim Schließen der Verbindung: " + e);
		}
		
		server.removeConnection(this);
		
		Thread moribund = connect;
		connect = null;
		moribund.interrupt();
	}
	
	/**
	 * 
	 * @param line String mit zu überprüfender Optionen - Slash am Anfang entfernen
	 */
	private void filterOptions( String line)
	{
		if(line.startsWith("name ")) {
			String newnick = line.substring(5);
			if(newnick.contains(" ")) {
				sendServerMsg("Name Darf keine Leerzeichen enthalten");
			} else if (server.userExists( newnick ) || newnick.equals("Server") ) {
				sendServerMsg("Name schon vergeben");
			} else {
				nickname = newnick;
				sendServerMsg("Ihr Benutzername lautet " + nickname);
				System.out.println("Nickname: " + nickname);
			}
			// TODO hier könnten alle anderen Serverbefehle hin
		} else if(line.startsWith("who")) {
			Vector<String> namelist = server.getUserNames();
			if(namelist.isEmpty()) {
				sendServerMsg("Keine Benutzer online");
				return;
			}
			sendServerMsg(namelist.size() + " Personen im Chat:");
			String names = new String();
			for( int i = 0; i < namelist.size(); i++ ) {
				names += namelist.elementAt(i);
				if ( i < namelist.size() - 1 ) {
					names += ", ";
					if ( names.length() > 40 ) {
						// neue Zeile anfangen
						sendServerMsg(names);
						names = "";
					}
				}
			}
			sendServerMsg(names);
		} else if(line.startsWith("quit")) {
			this.done();
		} else if(line.startsWith("help")) {
			sendServerMsg("Serverbefehle:");
			sendServerMsg("/name benutzername");
			sendServerMsg("   neuen Benutzernamen setzen");
			sendServerMsg("/quit");
			sendServerMsg("   Server verlassen und Client beenden");
			sendServerMsg("/help");
			sendServerMsg("   zeigt diese Hilfe an");
		} else {
			sendServerMsg("Unbekannter Befehl");
		}
	}

	public void run()
	{
		String line;
		Thread thisThread = Thread.currentThread();
		
		sendServerMsg("Willkommen auf dem Server");

		while(connect == thisThread)
		{
			try {
				line=in.readLine();
			} catch (IOException e) {
				System.out.println("Fehler: " + e);
				this.done(); // funktioniert das hier?
				break; // sicher ist sicher?
			}
			
			if(line == null) {
				System.out.println("Verbindungsfehler - beende Verbindung");
				this.done();
			} else if( line.startsWith("/") ) {
				filterOptions( line.substring(1) );
			} else {
				System.out.println(nickname + ": " + line); //könnte gelöscht werden
				server.broadcast(nickname + ": " + line);
			}
		}
	}
}