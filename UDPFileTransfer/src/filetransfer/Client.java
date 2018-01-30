package filetransfer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.Scanner;

public class Client {

	public static void main(String[] args) {
		Scanner scan = new Scanner(System.in);
		System.out.println("CLIENT: Enter an IP address (Leave blank for default): ");
		String ip = scan.nextLine();
		if (ip.equals("")) {
			ip = "127.0.0.1";
		}
		System.out.println("CLIENT: Enter a port number (Leave blank for default): ");
		String p = scan.nextLine();
		if (p.equals("")) {
			p = "9870";
		}	
		int port = Integer.parseInt(p);
			/*
		}
		if (!checkInput(ip) || !checkInput(port)) {
			System.out.println("Incorrect port or IP format.");
			scan.close();
			System.exit(0);
		}
		*/
		System.out.println("CLIENT: Enter desired file name: ");
		String fileName = scan.nextLine();
		scan.close();
		String fileExtension = "";
		String front = "";
		// Extracts file extension for writing
		for (int i = fileName.length() - 1; i > 0; i--) {
			char c  = fileName.charAt(i);
			if (c == '.') {
				for (int j = i; j < fileName.length(); j++) {
					fileExtension += fileName.charAt(j);
				}
			}
		}
		ArrayList<byte[]> packets = new ArrayList<byte[]>();
		ArrayList<Integer> received = new ArrayList<Integer>();
		SocketAddress serverAddr = null;
		int ackNum = 0;
		int packetSize = 14;
		// TO-DO: SEND PACKET SIZE, SEND FILE SIZE
		
		try {
			DatagramChannel c = DatagramChannel.open();
			c.configureBlocking(false); // ?
			c.connect(new InetSocketAddress(ip, port));
			// send server file name
			ByteBuffer buffer = ByteBuffer.wrap(fileName.getBytes());
			c.send(buffer, new InetSocketAddress(ip, port));
			// get ack for file name delivery
			// receive packet size
			// send ack for packet size
			
			while (true) {
				buffer = ByteBuffer.allocate(packetSize + 4);
				buffer.clear();
				serverAddr = c.receive(buffer);
				//System.out.println("CLIENT: Packet# " + (packets.size() + 1) + " received.");
				if (serverAddr != null) {
					// manage which ack num to send back
					/*
					 ackNum is that number in the received array for which all packets
					 less than ackNum are accounted for. This preserves order. 
					 * Client can still receive packets greater than ackNum, however, 
					 * so when inserting new packets, make sure not to just add them on to
					 * the end of the array so that their sequence num matches their indice.
					 * (seqNum is (packetSize - 1), indice matches sequence num.)
					 */
					buffer.flip();
					int seqNum = buffer.getInt(0);
					received.add(seqNum, seqNum);
					// change if using a set array size
					for (int i = ackNum; i < received.size(); i++) {
						if ((received.get(i) > i) || (i == (received.size() - 1))) {
							ackNum = i;
						}
					}
					
					buffer.position(4); // skip integer
					byte[] b = new byte[buffer.remaining()];
					buffer.get(b);
					packets.add(b);
					
					// TO-DO: Write to file AFTER COMPLETION
					FileOutputStream out = new FileOutputStream("newFile" + fileExtension,  true);
					String s = new String(b); 
					byte[] by = s.getBytes();
					out.write(by);
					out.close();
					
					//send acknowledgement
					buffer = ByteBuffer.allocate(4);
					buffer.putInt(ackNum); // ACK# as determined by sent seq num should go here
					buffer.flip();
					System.out.println("Packet size: " + packets.size());
					c.send(buffer, serverAddr);
					System.out.println("CLIENT: Packet# " + (ackNum) + " ACK sent.");
					serverAddr = null;
				}
			}
			
		} catch (IOException e) {
			System.out.println("CLIENT: Client IO Exception");
		}
	}
	/*
	private static boolean checkInput(String str) {
		for (int i = 0; i < str.length(); i++) {
			if (!(str.charAt(i) >= 0) || str.charAt(i) != '.') {
				return false;
			} 
		}
		return true;
	}
	*/
}
