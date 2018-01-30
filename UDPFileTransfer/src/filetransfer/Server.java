package filetransfer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
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
		System.out.println("SERVER: Enter a port number: ");
		String p = scan.nextLine();
		if (p.equals("")) {
			p = "9870";
		}	
		int port = Integer.parseInt(p);
		scan.close();
		
		try {
			DatagramChannel c = DatagramChannel.open();
			Selector s = Selector.open(); 
			c.configureBlocking(false); //If data not available immediately, move on
			c.register(s, SelectionKey.OP_READ);
			c.bind(new InetSocketAddress(port));
			SocketAddress clientAddr = null;
			String fileName = "";
			boolean hasFile = false;
			ByteBuffer buffer;
			//DatagramSocket ds = new DatagramSocket();
			//ds.

			ArrayList<byte[]> packets = new ArrayList<byte[]>();
			File file = new File(fileName);
			String str = "";
			int floor = 0;
			int winSize = 5;
			int high = winSize;
			int low = 0;
			int packetSize = 14;

			// Waits until a non-empty file name is received, then exits.
			// TO-DO: Send ACK that filename has been received
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
					if (!fileName.isEmpty()) {
						hasFile = true;					
					}
				}
			}
			
			/*
			 * 1/28
			 * variables: Floor, winLow, winHigh, winSize = 5, ackNumber = last packet acked
			 * Window size shortens and grows back up to max
			 * Sequence number is 1 greater than window size. Seq = 6;
			 * Numbers are in three possible states: in window and ready to be sent, sent and not acked, or sent and acked.
			 * 
			 * When transmitted, low++. When acked, high++. 
			 * use %winSize to implement sequence numbers.  
			 * Could use an arrayList of byte arrays and empty previous byte arrays, but keep them in memory. 
			 */
			
			
			if (file.exists()) {
				System.out.println("file exists");
				FileInputStream in = new FileInputStream(file); // reads bytes
				System.out.println(file.length());
				int bytesRead = 0;
				// send file size, packet size
				buffer = ByteBuffer.allocate(4);
				buffer.putInt(packetSize);
				buffer.flip();
				c.send(buffer, clientAddr);
				
				while (bytesRead != -1) {
					while (high > low && ((bytesRead >= packetSize) || (packets.size() == 0))) { // what if you run out of data in the file?
						byte[] b = new byte[packetSize];
						// Allows the last packet to be sized flexibly
						if (in.available() < packetSize) {
							System.out.println("last packet");
							b = new byte[in.available()];
						}
						bytesRead = in.read(b);

						System.out.println("Bytes read: " + bytesRead);
						packets.add(b);
						buffer = ByteBuffer.allocate(packetSize + 4);
						buffer.putInt(packets.size() - 1);
						buffer.put(b); // write
						buffer.flip(); // to read mode
						int bytesSent = c.send(buffer, clientAddr);
						System.out.println("Bytes sent: " + bytesSent);
						System.out.println("SERVER: Packet# " + packets.size() + " sent.");
						low++;
					} 
					
					/*
					 * Keep track of elements which have been acknowledged.
					 * Raise floor to last acknowledged packet number (not sequence number)
					 * Raise high to difference between ack and previous floor.
					 */
					buffer = ByteBuffer.allocate(4);
					SocketAddress sa = c.receive(buffer);
					int ack = buffer.getInt(0);
					if (sa != null) {
						System.out.println("SERVER: Packet# " + ack + " ACK received.");

						System.out.println("ack: " + ack);
						high += (ack - floor);
						System.out.println("high: " + high);
						floor = ack;
						
						// empty packets between 0 and floor
						// can be improved
						byte[] emptyByteArray = new byte[0];
						if (ack != 0) {
							for (int i = 0; i < floor; i++) {
								packets.set(i, emptyByteArray);
							}
						}
					}
				}
				in.close();
			} else {
				System.out.println("File not found");
				//str = "file not found";
				//sc.write(buffer);
			}
			
			// send number of packets to client, shouldn't even need this
			/*
			if (packets.size() > 0) {
				buffer = ByteBuffer.allocate(100);
				buffer.putInt(packets.size());
				buffer.flip();
				c.send(buffer, clientAddr);
			}
			*/
		} catch (IOException e) {
			System.out.println("Server IO Exception");
		}
	}
}