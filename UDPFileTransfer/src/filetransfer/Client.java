package filetransfer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Scanner;
/*
 * Sean Aubrey
 * CIS 457
 */
public class Client {

	public static void main(String[] args) {

		Scanner scan = new Scanner(System.in);
		System.out.println("CLIENT: Enter a port number "
						+ "(Leave blank for default): ");
		String p = scan.nextLine();
		if (p.equals("")) {
			p = "9870";
		}
		if (!checkPortInput(Integer.parseInt(p))) {
			scan.close();
			System.exit(0);
		}
		System.out.println("CLIENT: Port set.");
		int port = Integer.parseInt(p);

		System.out.println("CLIENT: Enter an IP address "
						+ "(Leave blank for default): ");
		String ip = scan.nextLine();
		if (ip.equals("")) {
			ip = "127.0.0.1";
		}
		System.out.println("CLIENT: IP address set.");

		System.out.println("CLIENT: Enter desired file name: ");
		String fileName = scan.nextLine();
		System.out.println("CLIENT: File name set.");
		scan.close();
		String fileExtension = extractExtension(fileName);

		byte[] packets[];
		int ackNum = 0;
		int packetSize = 0; // Keep 0. Used as conditional
		int totalPackets = 0;
		int totalBytes = 0;

		try {
			ByteBuffer buffer;
			DatagramSocket ds = new DatagramSocket();
			InetAddress ia = InetAddress.getByName(ip);

			// Send file name to server
			buffer = ByteBuffer.wrap(fileName.getBytes());
			byte[] b = new byte[buffer.remaining()];
			buffer.get(b);
			DatagramPacket send = new DatagramPacket(b, b.length, ia, port);
			byte[] ack = new byte[12]; // three ints
			DatagramPacket rec = new DatagramPacket(ack, ack.length);
			int timeouts = 0;
			boolean gotFile = false;

			while (!gotFile) {
				// send file, wait for ack, resend otherwise
				ds.send(send);
				System.out.println("CLIENT: File name sent.");
				try {
					ds.setSoTimeout(500);
					/* receives packetSize, totalPackets, 
					 * and totalBytes, and is ACK. */
					ds.receive(rec); 
				} catch (SocketTimeoutException er) {
					ds.send(send);
					System.out.println("CLIENT: File name re-sent.");
					timeouts++;
					if (timeouts >= 10) {
						System.out.println("Server unavailable. Quitting...");
						ds.close();
						System.exit(0);
					}
				}
				// get ack for file name delivery
				buffer = ByteBuffer.allocate(12);
				buffer.put(ack); // ack goes into buffer
				buffer.flip();
				packetSize = buffer.getInt();
				totalPackets = buffer.getInt();
				totalBytes = buffer.getInt();
				if (packetSize > 0) {
					System.out.println("CLIENT: File name ACK received.");
					System.out.println("packet size: " + packetSize);
					System.out.println("total packets: " + totalPackets);
					System.out.println("total bytes: " + totalBytes);
					gotFile = true;
				}
			}

			/*
			 * To avoid writing the last packet as the size of packetSize, 
			 * Client can calculate: packets * packetSize to get totalBytes.
			 * totalBytes % packetSize gives remaining bytes for last packet. 
			 */
			packets = new byte[totalPackets][];
			while (true) {
				buffer = ByteBuffer.allocate(packetSize + 4);
				byte[] pack = new byte[packetSize + 4];
				DatagramPacket recPack = new DatagramPacket(pack, pack.length);
				try {
					ds.setSoTimeout(100);
					ds.receive(recPack);
				} catch (SocketTimeoutException er) {
					ackNum = determineAckNum(packets, ackNum);
					if (ackNum != totalPackets - 1) {
						System.out.println("CLIENT: TIMEOUT: Resending ACK#: "
														+ ackNum);
						sendAck(ackNum, ia, port);
					}
					else if (ackNum == totalPackets - 1) {
						System.out.println("CLIENT: Transfer complete!");
						for (int i = 0; i < 3; i++) {
							System.out.println("CLIENT: "
									+ "Resending final ack in case of loss.");
							sendAck(ackNum, ia, port);
						}
						ds.close();
						System.out.println("CLIENT: Writing data to newFile"
												+ fileExtension);
						printFile(packets, fileExtension, packetSize);
						break;
					}
				}

				if (recPack.getAddress() != null) {
					/*
					 ackNum is the largest packet number received before a
					 gap. This preserves order. 
					 * Client can still receive packets greater than ackNum
					 */
					buffer.put(pack);
					int seqNum = buffer.getInt(0);
					System.out.println("CLIENT: Packet# " + seqNum + 
												" received.");

					// If at final packet, get rid of empty bytes between 
					// remaining bytes and the generic packet size.
					if (seqNum == totalPackets - 1) {
						buffer = trimExcessBytes(buffer, totalBytes, 
													packetSize);
					}

					// Store rest of packet
					buffer.position(4); // skip integer header
					b = new byte[buffer.remaining()];
					buffer.get(b);

					// skip insertion if packet was previously added.
					if (packets[seqNum] == null) {
						packets[seqNum] = b;
					}
					ackNum = determineAckNum(packets, ackNum);
					sendAck(ackNum, ia, port);
				}
			}
		} catch (IOException e) {
			System.out.println("CLIENT: Client IO Exception" + e);
		}
	} // end main

	private static void sendAck(int ackNum, InetAddress ia, int port) {
		try {
			DatagramSocket ds = new DatagramSocket();
			ByteBuffer buffer = ByteBuffer.allocate(4);
			buffer.putInt(ackNum); // ACK# as determined by sent seqnum here
			buffer.flip();
			buffer.rewind();
			byte[] outBytes = new byte[buffer.remaining()];
			buffer.get(outBytes);
			DatagramPacket send = new DatagramPacket(
					outBytes, outBytes.length, ia, port);
			ds.send(send);
			System.out.println("CLIENT: Packet# " + (ackNum) + " ACK sent.");
			ds.close();
		} catch (IOException k) {
			System.out.println("CLIENT: Client IO Exception" + k);
		}
	}
	
	/*
	 * Scans through the array of packets starting at previous
	 * acked element to find the first empty element. The ackNum is 
	 * then the previous element unless the given ackNum is 0, 
	 * and instead remains 0.
	 */
	private static int determineAckNum(byte[][] packets, int ackNum) {
		/*
		int ack;
		// If packets is missing first element, set ackNum to 0
		// to avoid trying to access nonexistent element.
		if (ackNum < 0) {
			ack = 0;
		} else {
			ack = ackNum;
		}
		*/
		for (int i = ackNum; i < packets.length; i++) {
			// If null is found, then previous packet is to be acked.
			if (packets[i] == null) {
				if (i == 0) {
					ackNum = 0;
				} else {
					ackNum = i - 1;
				}
				break;
			} else { // if all packets have arrived, no nulls
				ackNum = packets.length - 1;
			}
		}
		return ackNum;
	}

	private static ByteBuffer trimExcessBytes(
			ByteBuffer buffer, int totalBytes, int packetSize) {
		int bytesRem = totalBytes % packetSize;
		System.out.println("bytesRem: " + bytesRem);
		ByteBuffer temp = ByteBuffer.allocate(bytesRem);
		buffer.flip();
		for (int i = 4; i < bytesRem + 4; i++) {
			temp.put(buffer.get(i));
		}
		// Add 4 initial empty bytes to fit in with other packets
		buffer = ByteBuffer.allocate(bytesRem + 4);
		temp.position(0);
		buffer.position(4);
		buffer.put(temp);
		return buffer;
	}

	private static void printFile(
			byte[][] packets, String fileExt, int packetSize) {
		FileOutputStream out = null;
		try {
			out = new FileOutputStream("newFile" + fileExt, true);
			for (int i = 0; i < packets.length; i++) {
				out.write(packets[i]);
			}
			out.close();
		} catch(IOException g) {
			System.out.println("Failure writing to file.");
		}
	}
	
	//private static void checkSum() {
	/*
	 * 
	int checkSumSize = buffer.getInt(4);
	byte[] checkSum = extractCSum(buffer, checkSumSize);
	buffer.position(24);
	byte[] dataToCheck = new byte[packetSize];
	buffer.flip();
	buffer.get(dataToCheck);
	ByteArrayInputStream ba = new ByteArrayInputStream(dataToCheck);
	MessageDigest md = null;
	try {
		md = MessageDigest.getInstance("MD5");
	} catch (NoSuchAlgorithmException a) {
		System.out.println("No such algorithm."
				+ " Unable to verify transfers correctly, quitting.");
		System.exit(0);
	}
	DigestInputStream dis = new DigestInputStream(ba, md);
	byte[] placeholder = new byte[16];
	dis.read(placeholder);
	md.update(placeholder);
	byte[] checkSumCopy = md.digest();
	for (int i = 0; i < 16; i++) {
		System.out.print(checkSumCopy[i]);
	}
	System.out.println();
	for (int i = 0; i < 16; i++) {
		System.out.print(checkSum[i]);
	}

	if (checkSumCopy == checkSum) {
	 *
	 */
	//}
	
/*
	private static byte[] extractCSum(ByteBuffer buffer, int checkSumSize){
		byte[] checkSum = new byte[checkSumSize];
		ByteBuffer temp = ByteBuffer.allocate(checkSumSize);
		buffer.position(0);
		//System.out.println("cap: " + buffer.capacity());
		for (int i = 8; i < (checkSumSize + 8); i++) {
			temp.put(buffer.get(i));
		}
		temp.flip();
		temp.position(0);
		temp.get(checkSum);
		return checkSum;
	}
	*/

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

	private static boolean checkPortInput(int p) {
		if (p < 0 || p > 65535) {
			System.out.println("Incorrect port input, try again.");
			return false;
		}
		return true;
	}

	private static boolean checkInput(String str) {
		for (int i = 0; i < str.length(); i++) {
			if (!(str.charAt(i) >= 0) || (str.charAt(i) != '.')) {
				System.out.println("Incorrect format, try again.");
				return false;
			} 
		}
		return true;
	}
}
