package filetransfer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Scanner;
/*
 * Sean Aubrey
 * CIS 457
 */
public class Server {
	public static void main(String[] args) {
		Scanner scan = new Scanner(System.in);
		System.out.println("SERVER: Enter a port number "
				+ "(Leave blank for default): ");
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
			DatagramSocket ds = new DatagramSocket();
			String fileName = "";
			boolean hasFile = false;
			ByteBuffer buffer;

			byte[] packets[];
			//byte[] checkPackets[];
			int floor = 0;
			int winSize = 5;
			int high = winSize;
			int low = 0;
			int packetSize = 14; 
			int totalPackets = 0;
			
			// Waits until a non-empty file name is received
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
					System.out.println("SERVER: File name from client: " + 
													fileName);
					
					if (!fileName.isEmpty()) {
						/* Calculate total number of packets, send it to client.*/
						file = new File(fileName);
						int totalBytes = (int)file.length();
						/* If not a whole number. */
						if (totalBytes % packetSize != 0) { 
							totalPackets = (int)
							Math.round(((double) totalBytes/packetSize) + 0.5);
						} else {
							totalPackets = totalBytes/packetSize;
						}
						System.out.println("SERVER: total packets: " + 
											totalPackets);
						System.out.println("SERVER: File name received.");
						buffer = ByteBuffer.allocate(12);
						buffer.putInt(packetSize);
						buffer.putInt(totalPackets);
						buffer.putInt(totalBytes);
						buffer.flip();
						c.send(buffer, clientAddr);
						System.out.println("SERVER: File name received ACK sent.");
						
						byte[] ack = new byte[4];
						DatagramPacket rec = new DatagramPacket(ack, ack.length);
						boolean clientReady = false;
						int timeouts = 0;
						// Waits for packet# 0 ack return from client.
						while (!clientReady) {
							try {
								ds.setSoTimeout(100);
								ds.receive(rec);
							} catch (SocketTimeoutException er) {
								timeouts++;
								System.out.println("to: " + timeouts);
							}
							ByteBuffer buffer2 = ByteBuffer.allocate(4);
							buffer2.put(ack);
							buffer2.flip();
							buffer2.position(0);
							int q = buffer2.getInt();
							if (q != 0) {
								buffer.position(0);
								c.send(buffer, clientAddr);
								System.out.println("SERVER: "
										+ "File name received ACK sent.");
							} else if (q == 0){
								clientReady = true;
							}
						}
						hasFile = true;					
					}
				}
			}

			/*
			checkPackets = new byte[totalPackets][];
			MessageDigest md = null;
			try {
				md = MessageDigest.getInstance("MD5");
				
			} catch (NoSuchAlgorithmException a) {
				System.out.println("No such algorithm."
				+ " Unable to verify transfers correctly, quitting.");
				System.exit(0);
			}
			*/
			/* prevFloor used to to avoid traversing entire 
			 * array each cycle when cleaning */
			int prevFloor = -1; 
			int lastAck = -1;
			int sameAckCount = 0;
			packets = new byte[totalPackets][];
			if (file.exists()) {
				System.out.println("file exists");
				FileInputStream in = new FileInputStream(file);
				System.out.println("File length: " + file.length());
				int bytesRead = 0;
				while ((bytesRead != -1) || (high > low)) {
					// Send packet(s) in sliding window.
					while (high > low) {
						byte[] b = new byte[packetSize];
						System.out.println("SERVER: Bytes to read in: " + 
													in.available());
						// Allows the last packet to be sized flexibly
						if (in.available() < packetSize 
								&& in.available() != 0) {
							b = new byte[in.available()];
						}
						
						/*  Only read from file if first time sending.
						 *  Input is filtered and (not) checksum'd and 
						 *  (not) added to the packet's corresponding 
						 *  index in checkPackets. */
						//if (low == totalPackets) {
						//	low = totalPackets - 1;
						//}
						if (packets[low] == null) {
							//DigestInputStream dis; 
							//dis = new DigestInputStream(in, md);
							//bytesRead = dis.read(b); 
							//byte[] checkSum = md.digest();
							bytesRead = in.read(b); // -1 if end of file
							packets[low] = b;
							//checkPackets[low] = checkSum;
						}
						buffer = ByteBuffer.allocate(packetSize + 4);
						buffer.putInt(low); // put packetNum
						//buffer.put(checkPackets[low]);
						buffer.put(packets[low]);
						buffer.flip();
						c.send(buffer, clientAddr);
						System.out.println("SERVER: Packet# " + 
											low + " sent.");
						low++;
					} // No more packets to be sent yet.
					
					/* -Keep track of elements which have been acknowledged
					 * first. -Raise floor to last acknowledged packet number. 
					 * -Raise high to difference between ack and previous floor.
					 * -Free obsolete memory. */
					buffer = ByteBuffer.allocate(4);
					SocketAddress sa = c.receive(buffer); // receive ack
					int ack = buffer.getInt(0);
					if (sa != null) {
						System.out.println("SERVER: Packet# " + 
										ack + " ACK received.");
						if (ack == totalPackets - 1) {
							System.out.println("SERVER: "
								+ "Transfer complete! Closing server...");
							in.close();
							ds.close();
							System.exit(0);
						}
					   /* Unless we've reached the end of the file,
						* increase what we can send by the amount of 
						* acks received since we last raised the floor. */
						if (high < totalPackets) {
							high += (ack - floor);
						}
						System.out.println("");
						/* prevFloor used to remove acked packets. */
						prevFloor = floor; 
						floor = ack;
						
					   /* If not a new ack, we need to resend, 
						* so decrease 'low' to ack + 1.
						* But, repeat acks are common, let's be sure 
						* that a packet is missing. */
						if (ack == lastAck) { 	 
							sameAckCount++;
							if (sameAckCount >= 3) {
								low = ack + 1;
								if (ack == 0) {
									low = 0;
								}
								sameAckCount = 0;
							}
						}
						lastAck = ack;
						
						// empty packets between 0 and floor
						if (prevFloor > 0) {
							for (int i = prevFloor; i < floor; i++) {
								packets[i] = null;
							}
						}
					}
				}
				in.close();
			} else {
				System.out.println("SERVER: File not found!");
			}
		} catch (IOException e) {
			System.out.println("SERVER: Server IO Exception");
		}
	} // end Main
	
	private static boolean checkPortInput(int p) {
		if (p < 0 || p > 65535) {
			System.out.println("SERVER: Incorrect port input, try again.");
			return false;
		}
		return true;
	}
}
