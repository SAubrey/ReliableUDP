package filetransfer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Scanner;

public class Client {

	public static void main(String[] args) {
		
		Scanner scan = new Scanner(System.in);
		System.out.println("CLIENT: Enter a port number (Leave blank for default): ");
		String p = scan.nextLine();
		System.out.println("CLIENT: Port set.");
		if (p.equals("")) {
			p = "9870";
		}
		int port = Integer.parseInt(p);
		
		System.out.println("CLIENT: Enter an IP address (Leave blank for default): ");
		String ip = scan.nextLine();
		System.out.println("CLIENT: IP address set.");
		if (ip.equals("")) {
			ip = "127.0.0.1";
		}
		
		System.out.println("CLIENT: Enter desired file name: ");
		String fileName = scan.nextLine();
		System.out.println("CLIENT: File name set.");
		scan.close();
		
		String fileExtension = extractExtension(fileName);
		
		//int[] received;
		byte[] packets[];
		int ackNum = 0;
		int packetSize = 0; // Keep 0. Packet size received from Server.
		int totalPackets = 0;
		
		try {
			DatagramChannel c = DatagramChannel.open();
			c.configureBlocking(false); // ?
			c.connect(new InetSocketAddress(ip, port));
			ByteBuffer buffer;
			boolean gotFile = false;
			DatagramSocket ds = new DatagramSocket();
			InetAddress ia = InetAddress.getByName(ip);

			// Send file name to server
			buffer = ByteBuffer.wrap(fileName.getBytes());
			byte[] b = new byte[buffer.remaining()];
			buffer.get(b);
			DatagramPacket send = new DatagramPacket(b, b.length, ia, port);
			byte[] ack = new byte[8]; // two ints
			DatagramPacket rec = new DatagramPacket(ack, ack.length);
			int timeouts = 0;
			
			while (!gotFile) {
				// send file, wait for ack, resend otherwise
				ds.send(send);
				System.out.println("CLIENT: File name sent.");
				try {
					ds.setSoTimeout(1000);
					// receives packetSize & totalPackets, which also acts as ACK
					ds.receive(rec); 
				} catch (SocketTimeoutException er) {
					ds.send(send);
					System.out.println("CLIENT: File name re-sent.");
					timeouts++;
					if (timeouts >= 15) {
						System.out.println("Server unavailable. Quitting...");
						System.exit(0);
					}
				}
				// get ack for file name delivery
				buffer = ByteBuffer.allocate(8);
				buffer.put(ack); // ack goes into buffer
				buffer.flip();
				packetSize = buffer.getInt();
				totalPackets = buffer.getInt();
				if (packetSize > 0) {
					System.out.println("CLIENT: File name ACK received.");
					System.out.println("packet size: " + packetSize);
					System.out.println("total packets: " + totalPackets);
					gotFile = true;
				}
			}
			
			packets = new byte[totalPackets][];
			while (true) {
				buffer = ByteBuffer.allocate(packetSize + 4);
				byte[] pack = new byte[packetSize + 4];
				DatagramPacket recPack = new DatagramPacket(pack, pack.length);
				try {
					ds.setSoTimeout(10000);
					ds.receive(recPack);
				} catch (SocketTimeoutException er) {
					System.out.println("Timeout");
					break;
					//printFile(packets, fileExtension);
				}
				
				if (recPack.getAddress() != null) {
					/*
					 ackNum is the largest packet number received before a
					 gap. This preserves order. 
					 * Client can still receive packets greater than ackNum, however, 
					 * so when inserting new packets, make sure not to just add them on to
					 * the end of the array so that their sequence num matches their indice.
					 * (seqNum is (packetSize - 1), indice matches sequence num.)
					 */
					buffer.put(pack);
					int seqNum = buffer.getInt(0);
					System.out.println("CLIENT: Packet# " + seqNum + " received.");
					//System.out.println("seqNum: " + seqNum);
				
					// Store rest of packet
					buffer.position(4); // skip integer
					b = new byte[buffer.remaining()];
					buffer.get(b);
					// skip insertion if it's already there.
					if (packets[seqNum] == null) { // out of bounds, seqNum needs to be + 1!
						packets[seqNum] = b;
					}
					
					// Determine ackNum
					for (int i = ackNum; i < packets.length - 1; i++) {
						if (packets[i] == null) {
							ackNum = i;
							break;
						}
					}
					
					// TO-DO: Write to file AFTER COMPLETION
					
					FileOutputStream out = new FileOutputStream("newFile" + fileExtension, true);
					String str = new String(b); 
					byte[] by = str.getBytes();
					out.write(by);
					out.close();
					
					//printFile(packets, fileExtension);
					
					//send acknowledgement
					buffer = ByteBuffer.allocate(4);
					buffer.putInt(ackNum); // ACK# as determined by sent seq num should go here
					buffer.flip();
					buffer.rewind();
					byte[] outBytes = new byte[buffer.remaining()];
					buffer.get(outBytes);
					send = new DatagramPacket(outBytes, outBytes.length, ia, port);
					ds.send(send);
					System.out.println("CLIENT: Packet# " + (ackNum) + " ACK sent.");
				}
			}
		} catch (IOException e) {
			System.out.println("CLIENT: Client IO Exception" + e);
		}
	} // end main
	
	private static String extractExtension(final String fileName) {
		String fileExtension = "";
		for (int i = fileName.length() - 1; i > 0; i--) {
			char c  = fileName.charAt(i);
			if (c == '.') {
				for (int j = i; j < fileName.length(); j++) {
					fileExtension += fileName.charAt(j);
				}
			}
		}
		return fileExtension;
	}
	
	private static void printFile(byte[][] packets, String fileExt) {
		byte[] x = new byte[packets.length];
		for (int i = 0; i < packets.length; i++) {
			ByteBuffer b = ByteBuffer.allocate(1024);
			b.put(packets[i]);
			b.flip();
			b.get(x);
		}
		try {
			FileOutputStream out = new FileOutputStream("newFile" + fileExt, true);
			out.write(x);
			out.close();
		} catch(IOException g) {
			System.out.println("Oh no!");
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
