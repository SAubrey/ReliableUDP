package filetransfer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Scanner;

public class Server {
	public static void main(String[] args) {
		Scanner scan = new Scanner(System.in);
		System.out.println("SERVER: Enter a port number (Leave blank for default): ");
		String p = scan.nextLine();
		if (p.equals("")) {
			p = "9870";
		}	
		if (!(checkPortInput(Integer.parseInt(p)))) {
			scan.close();
			System.exit(0);
		}
		
		System.out.println("SERVER: Port set.");
		
		int port = Integer.parseInt(p);
		scan.close();
		
		try {
			DatagramChannel c = DatagramChannel.open();
			Selector s = Selector.open(); 
			c.configureBlocking(false);
			c.register(s, SelectionKey.OP_READ);
			c.bind(new InetSocketAddress(port));
			SocketAddress clientAddr = null;
			String fileName = "";
			boolean hasFile = false;
			ByteBuffer buffer;

			ArrayList<byte[]> packets = new ArrayList<byte[]>();
			int floor = 0;
			int winSize = 5;
			int high = winSize; // account for zero
			int low = 0;
			int packetSize = 1024; // success dependent upon packetSize???
			int totalPackets = 0;
			
			// Waits until a non-empty file name is received, then exits.
			File file = null;
			while (!hasFile) {
				int t = s.select(50000);
				if (t == 0) {
					System.out.println("Timeout - No data received");
				} else {
					buffer = ByteBuffer.allocate(100);
					clientAddr = c.receive(buffer);
					buffer.flip();
					byte[] a = new byte[buffer.remaining()];
					buffer.get(a);
					fileName = new String(a);
					System.out.println("SERVER: File name from client: " + fileName);
					// send ack
					if (!fileName.isEmpty()) {
						// Calculate total number of packets, which will be sent to client.
						file = new File(fileName);
						int totalBytes = (int)file.length(); // long is not decimal
						if (totalBytes % packetSize != 0) { // if not a whole number
							totalPackets = (int)
									Math.round(((double) totalBytes/packetSize) + 0.5);
						} else {
							totalPackets = totalBytes/packetSize;
						}
						System.out.println("total packets: " + totalPackets);
						System.out.println("SERVER: File name received.");
						buffer = ByteBuffer.allocate(12);
						buffer.putInt(packetSize);
						buffer.putInt(totalPackets);
						buffer.putInt(totalBytes);
						buffer.flip();
						c.send(buffer, clientAddr);
						System.out.println("SERVER: File name received ACK sent.");
						hasFile = true;					
					}
				}
			}
			/*
			 * When transmitted, low++. When acked, high++. 
			 * use %winSize to implement sequence numbers.  
			 * Could use an arrayList of byte arrays and empty previous byte arrays, but keep them in memory. 
			 */
			
			// used to to avoid traversing entire array each cycle when cleaning
			int prevFloor = 0; 
			if (file.exists()) {
				System.out.println("file exists");
				FileInputStream in = new FileInputStream(file); // reads bytes
				System.out.println("File length: " + file.length());
				
				int bytesRead = 0;
				while (bytesRead != -1) {
					while (high > low && ((bytesRead >= packetSize) || 
							(packets.size() == 0))) {
					//while (high > low) {
						byte[] b = new byte[packetSize];
						// Allows the last packet to be sized flexibly
						System.out.println(in.available());
						if (in.available() < packetSize) {
							System.out.println("last packet");
							b = new byte[in.available()];
						}
						bytesRead = in.read(b); // -1 if end of file

						//System.out.println("Bytes read: " + bytesRead);
						packets.add(b);
						buffer = ByteBuffer.allocate(packetSize + 4);
						buffer.putInt(packets.size() - 1);
						buffer.put(b); // write
						buffer.flip(); // to read mode
						int bytesSent = c.send(buffer, clientAddr);
						System.out.println("Bytes sent: " + bytesSent);
						System.out.println("SERVER: Packet# " + (packets.size() - 1) + " sent.");
						low++;
					} 
					
					/*
					 * Keep track of elements which have been acknowledged.
					 * Raise floor to last acknowledged packet number (not sequence number)
					 * Raise high to difference between ack and previous floor.
					 */
					buffer = ByteBuffer.allocate(4);
					SocketAddress sa = c.receive(buffer); // receive ack
					int ack = buffer.getInt(0);
					if (sa != null) {
						System.out.println("SERVER: Packet# " + ack + " ACK received.");

						System.out.println("ack: " + ack);
						if (high != totalPackets) {
							high += (ack - floor);
						}
						System.out.println("high: " + high);
						System.out.println("");
						prevFloor = floor;
						floor = ack;
						
						// empty packets between 0 and floor
						// can be improved
						/*
						byte[] emptyByteArray = new byte[0];
						if (ack != 0) {
							for (int i = prevFloor; i < floor; i++) {
								packets.set(i, emptyByteArray);
							}
						}
						*/
					}
				}
				in.close();
			} else {
				System.out.println("File not found!");
				//str = "file not found";
				//sc.write(buffer);
			}
		} catch (IOException e) {
			System.out.println("Server IO Exception");
		}
	}
	
	private static boolean checkPortInput(int p) {
		if (p < 0 || p > 65535) {
			System.out.println("Incorrect port input, try again.");
			return false;
		}
		return true;
	}
}
