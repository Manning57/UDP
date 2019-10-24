import java.net.DatagramPacket;
import java.net.Inet4Address;
import edu.utulsa.unet.*;
import com.braju.format.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Properties;

public class RSendUDP implements RSendUDPI {

	private static String PROP = "unet.properties";
	private static String SERVER = "10.20.28.155";
	private static int RECPORT = 32456;
	private static int SENDPORT = 23456;
	private int ack = 0;
	private static int MTU;
	private static int HL;
	private static int PAYLEN;

	private static int Mode = 0;
	public static long WindowSize = 256;
	private static String FileName;
	public static long TimeOut = 1000;
	static UDPSocket socket;
	private static InetAddress LocalHost = getLocalHost();
	InetSocketAddress ReceiverAddress = new InetSocketAddress(LocalHost, RECPORT);
	InetSocketAddress SenderAddress = new InetSocketAddress(LocalHost, SENDPORT);

	// sender header
	private static byte[] ackBuffer = new byte[4];
	private static int ackNumber = 0;
	private static byte[] seqBuffer = new byte[4];
	private static int seqNumber = 1;
	private static byte[] windowBuffer = new byte[4];

	// receiver header
	private static byte[] recBuffer = new byte[4];

	private static byte[] constructPacket(byte[] seq, byte[] ack, byte[] window, byte[] pay) {

		byte[] preHeader = new byte[HL - 4];
		System.arraycopy(seq, 0, preHeader, 0, seq.length);
		System.arraycopy(ack, 0, preHeader, seq.length, ack.length);
		byte[] header = new byte[HL];
		System.arraycopy(preHeader, 0, header, 0, preHeader.length);
		System.arraycopy(window, 0, header, preHeader.length, window.length);
		byte[] payload = pay;
		byte[] sendPacket = new byte[MTU];
		System.arraycopy(header, 0, sendPacket, 0, header.length);
		System.arraycopy(payload, 0, sendPacket, header.length, payload.length);

		return sendPacket;
	}

	private static byte[] constructTrimmedPacket(byte[] seq, byte[] ack, byte[] window, byte[] pay, int trim) {

		byte[] preHeader = new byte[HL - 4];
		System.arraycopy(seq, 0, preHeader, 0, seq.length);
		System.arraycopy(ack, 0, preHeader, seq.length, ack.length);
		byte[] header = new byte[HL];
		System.arraycopy(preHeader, 0, header, 0, preHeader.length);
		System.arraycopy(window, 0, header, preHeader.length, window.length);
		byte[] payload = pay;
		byte[] sendPacket = new byte[MTU - trim];
		System.arraycopy(header, 0, sendPacket, 0, header.length);
		System.arraycopy(payload, 0, sendPacket, header.length, payload.length);
		return sendPacket;
	}

	private static void printPacket(byte[] packet) {
		System.out.println("---->");
		byte[] testSeq = Arrays.copyOfRange(packet, 0, 4);
		int seq = ByteBuffer.wrap(testSeq).getInt();
		System.out.println("Sequence#: " + seq);

		byte[] testAck = Arrays.copyOfRange(packet, 4, 8);
		int ack = ByteBuffer.wrap(testAck).getInt();
		System.out.println("Ack#: " + ack);

		byte[] testWindow = Arrays.copyOfRange(packet, 8, 12);
		int window = ByteBuffer.wrap(testWindow).getInt();
		System.out.println("Window size: " + window);

		byte[] testPayload = Arrays.copyOfRange(packet, 12, MTU);
		String payload = new String(testPayload);
		System.out.println("Data: '" + payload + "'");
	}

	private static void printInfo(byte[] packet) {

		byte[] testSeq = Arrays.copyOfRange(packet, 0, 4);
		int seq = ByteBuffer.wrap(testSeq).getInt();

		byte[] testAck = Arrays.copyOfRange(packet, 4, 8);
		int Ack = ByteBuffer.wrap(testAck).getInt();

		byte[] testPayload = Arrays.copyOfRange(packet, 12, packet.length);
		int payload = testPayload.length;
		System.out.println("Message " + seq + " sent with " + payload + " actual bytes of data");
	}

	private static void loadProps() {
		Properties properties = new Properties();
		try {
			properties.load(new FileInputStream(PROP));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// fetching mtu
		MTU = Integer.parseInt(properties.getProperty("packet.mtu"));
		HL = 12;
		PAYLEN = MTU - HL;
		int outstandingFrames = (int) Math.ceil((double) WindowSize / (double) MTU);

		System.out.println("~~~~~PROPERTY INFO~~~~~~");
		System.out.println("MTU: " + MTU);
		System.out.println("HL: " + HL);
		System.out.println("PAYLEN: " + PAYLEN);
		System.out.println("Timeout: " + TimeOut);
		if (Mode == 1) {
			System.out.println("Window Size: " + WindowSize);
			System.out.println("Outstanding Frames: " + outstandingFrames);
		}
		System.out.println("~~~~~PROPERTY INFO~~~~~~");
		System.out.println();

		if (MTU != (-1) && MTU < 13) {
			System.out.println("unet.properties MTU must be > 12.");
			System.out.println("This is because the header size is 10 bytes.");
			return;
		}
	}

	private static void initializeTransfer(int iterations) {
		try {
			socket.setSoTimeout((int) TimeOut);
			int x = 0;
			// loop until initial EOT info is sent and received
			while (x != 1) {
				// constructing initial packet with EOT info
				seqBuffer = ByteBuffer.allocate(4).putInt(iterations).array();
				ackBuffer = ByteBuffer.allocate(4).putInt(0).array();
				windowBuffer = ByteBuffer.allocate(4).putInt((int) WindowSize).array();
				byte[] payload = new byte[PAYLEN];
				byte[] sendPacket = constructPacket(seqBuffer, ackBuffer, windowBuffer, payload);

				// sending inital packet with EOT info
				System.out.println("Sending EOT info");
				socket.send(new DatagramPacket(sendPacket, sendPacket.length, InetAddress.getByName(SERVER), RECPORT));

				DatagramPacket recPacket = new DatagramPacket(recBuffer, recBuffer.length);
				// exit if successful interaction, otherwise repeat
				try {
					socket.receive(recPacket);
					x = 1;
					System.out.println("Receiver identified EOT ACK of " + iterations + "\n");
				} catch (SocketTimeoutException e) {
					System.out.println("Receiver has not identified end ACK. Resending.");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void closeTransfer() {
		try {
			// loop until EOT messages are finalized
			int y = 0;
			while (y == 0) {
				// making and sending EOT packet
				seqNumber = -1;
				seqBuffer = ByteBuffer.allocate(4).putInt(seqNumber).array();
				ackBuffer = ByteBuffer.allocate(4).putInt(ackNumber).array();
				windowBuffer = ByteBuffer.allocate(4).putInt((int) WindowSize).array();
				byte[] finalPayload = new byte[0];
				byte[] finalSendPacket = constructPacket(seqBuffer, ackBuffer, windowBuffer, finalPayload);
				socket.send(new DatagramPacket(finalSendPacket, finalSendPacket.length, InetAddress.getByName(SERVER),
						RECPORT));
				System.out.println("Sending EOT message");

				// getting the EOT ACK
				DatagramPacket recPacket = new DatagramPacket(recBuffer, recBuffer.length);
				try {
					socket.receive(recPacket);
					System.out.println("EOT message acknowledged\n");
					y = 1;
				} catch (SocketTimeoutException e) {
					System.out.println("EOT message not acknowledged. Resending\n");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static byte[][] formatFile(byte[] buffer) {
		// getting file info
		int totalLength = buffer.length;
		int iterations = (int) Math.ceil((double) totalLength / (double) PAYLEN);

		// making a new buffer where the file data is formatted for sending
		byte[][] choppedFile = new byte[iterations][1];

		// putting formatted data into new buffer
		int y = 0;
		for (int x = 0; x < iterations; x++) {
			seqBuffer = ByteBuffer.allocate(4).putInt(x + 1).array();
			ackBuffer = ByteBuffer.allocate(4).putInt(x).array();
			windowBuffer = ByteBuffer.allocate(4).putInt((int) WindowSize).array();
			byte[] payload;

			if ((buffer.length - y) < PAYLEN) {
				int spacesToRemove = PAYLEN - (buffer.length - y);
				ackBuffer = ByteBuffer.allocate(4).putInt(spacesToRemove).array();
				payload = Arrays.copyOfRange(buffer, y, y + (PAYLEN - spacesToRemove));
				choppedFile[x] = constructTrimmedPacket(seqBuffer, ackBuffer, windowBuffer, payload, spacesToRemove);
			} else {
				payload = Arrays.copyOfRange(buffer, y, y + PAYLEN);
				choppedFile[x] = constructPacket(seqBuffer, ackBuffer, windowBuffer, payload);
			}

			y = y + PAYLEN;
		}
		return choppedFile;
	}

	private static void sendPacket(byte[][] choppedFile, int i) {
		// selecting appropriate packet to send
		byte[] packetToSend = choppedFile[i];

		// printing message info
		printInfo(packetToSend);
		// printPacket(packetToSend);

		// sending packet
		try {
			socket.send(new DatagramPacket(packetToSend, packetToSend.length, InetAddress.getByName(SERVER), RECPORT));
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static int receiveAck() throws IOException {
		DatagramPacket recPacket = new DatagramPacket(recBuffer, recBuffer.length);
		try {
			socket.receive(recPacket);
			recBuffer = recPacket.getData();
			int ackRec = ByteBuffer.wrap(recBuffer).getInt();
			System.out.println("Message " + ackRec + " acknowledged with ACK " + ackRec);
			return ackRec;
		}
		// if no ACK is received, timeout will cause packet to be resent
		catch (SocketTimeoutException e) {
			System.out.println("Message " + seqNumber + " timeout or got incorrect ACK\n");
			return -1;
		}
	}

	private static void stopAndWait() {
		try {
			// getting file info
			byte[] buffer = Files.readAllBytes(Paths.get(FileName));
			int totalLength = buffer.length;
			int iterations = (int) Math.ceil((double) totalLength / (double) PAYLEN);

			// formatting the file buffer into packets
			byte[][] choppedFile = formatFile(buffer);

			// sending initial EOT message
			initializeTransfer(iterations);

			int i = 0;
			// loop until file info is sent
			while (ackNumber != -1) {

				// sending the packet
				sendPacket(choppedFile, i);

				// receiving the ack
				int ackReceived = receiveAck();

				// updating seq and ack #
				if (ackReceived != -1) { // if ack was received
					i++; // go to next packet to send
					seqNumber++;
					ackNumber++;
					// end of transmission, goto closeTransfer
					if (ackReceived == iterations) {
						ackNumber = -1;
					}
					System.out.println();
				}
			}
			// ending transmission
			closeTransfer();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void slidingWindows() {

		try {
			// getting file info
			byte[] buffer = Files.readAllBytes(Paths.get(FileName));
			int totalLength = buffer.length;
			int iterations = (int) Math.ceil((double) totalLength / (double) PAYLEN);

			// formatting the file buffer into packets
			byte[][] choppedFile = formatFile(buffer);

			// sending initial EOT message
			initializeTransfer(iterations);

			// My sender Sliding Windows Algorithm
			// 1.) sender will send each of the outstanding packets that fit in the window
			// 2.) for each packet sent, if ACK isnt received by timout, retransmit
			// 3.) once sender it receives an ACK, it will check its ACK #
			// 4.) if the ACK # corresponds to a packet # behind the window, do nothing
			// 5.) else if the ACK # corresponds to the last ACK needed (== iterations) goto
			// closeTransmission
			// 6.) Otherwise, sender will move the start of window to the packet after the
			// ack # received
			// 7.) if start of window seq # + (windowSize-1) > iterations, shrink window
			// size by (seq # + (windowSize-1)- iterations)
			// 8.) go to step 1

			int windowStart = 0;
			int windowEnd = (int) WindowSize;
			
			//not checking for dropped ack to retransmit that specific packet
			
			while (ackNumber != -1) {

				// 1.) sender will send each of the outstanding packets that fit in the window
				for (int x = windowStart; x < windowEnd; x++) {
					sendPacket(choppedFile, x);
				}

				// 3.) once sender it receives an ACK, it will check its ACK #
				int ackReceived = receiveAck();
				System.out.println();
				

				// 4.) if the ACK # corresponds to a packet # behind the window, do nothing
				if (ackReceived < windowStart + 1) {

				}
				// 5.) else if the ACK # corresponds to the last ACK needed (== iterations) goto
				// closeTransmission
				else if (ackReceived == iterations) {
					break;
				}
				if (ackReceived == -1) {
					break;
				}
				// 6.) Otherwise, sender will move the start of window to the packet after the
				// ack # received
				else {
					windowStart = ackReceived;
					windowEnd = (int) (windowStart + WindowSize);
				}
				// 7.) if start of window seq # + (windowSize-1) > iterations, shrink window
				// size by (seq # + (windowSize-1)- iterations)
				if (windowStart + (WindowSize - 1) > iterations) {
					WindowSize = WindowSize - (windowStart + (WindowSize - 1) - iterations);
				}
				// 8.) go to step 1

			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		// ending transmission
		closeTransfer();

	}

	// returns the local host address
	private static InetAddress getLocalHost() {
		InetAddress lh = null;
		try {
			lh = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return lh;
	}

	// Sets mode (0 for stop and wait, 1 for sliding windows)
	public boolean setMode(int mode) {
		try {
			Mode = mode;
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	// gets mode (0 for stop and wait, 1 for sliding windows)
	public int getMode() {
		return Mode;
	}

	// gets mode in string format
	public String getModeString() {
		if (Mode == 0) {
			return "STOP_AND_WAIT";
		} else {
			return "SLIDING_WINDOWS";
		}
	}

	@Override
	// sets size of window in bytes for sliding windows
	// calling this in stop and wait has no effect
	public boolean setModeParameter(long ModeParameter) {
		try {
			if (Mode == 1) {
				WindowSize = ModeParameter;
				return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	// gets size of window in bytes for sliding windows
	public long getModeParameter() {
		return WindowSize;
	}

	@Override
	// sets the name of the file to be sent
	public void setFilename(String fn) {
		FileName = fn;
	}

	@Override
	// gets the name of the file to be sent
	public String getFilename() {
		return FileName;
	}

	@Override
	// sets timeout in milliseconds
	// default value is 1 second
	public boolean setTimeout(long t) {
		try {
			TimeOut = t;
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	// returns timeout in milliseconds
	public long getTimeout() {
		return TimeOut;
	}

	@Override
	// Sets the local port for the sender socket
	public boolean setLocalPort(int lp) {
		SENDPORT = lp;
		try {
			socket = new UDPSocket(SENDPORT);
			return true;
		} catch (SocketException e) {
			e.printStackTrace();
			return false;
		}
	}

	@Override
	// gets the local port for the sender socket
	public int getLocalPort() {
		return SENDPORT;
	}

	@Override
	// sets the ip address and port number of the receiver
	public boolean setReceiver(InetSocketAddress ra) {
		try {
			ReceiverAddress = ra;
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	// gets the ip address and port number of the receiver
	public InetSocketAddress getReceiver() {
		return ReceiverAddress;
	}

	// gets the ip address and port number of the sender
	public InetSocketAddress getSender() {
		return SenderAddress;
	}

	@Override
	// Sends file data from sender socket to receiver socket
	// if mode is 0, stop and wait is used
	// if mode is 1, sliding windows is used
	public boolean sendFile() {
		double startTime = System.currentTimeMillis();
		// printing file transfer info to console
		System.out.println("\nSending \"" + getFilename() + "\" from [" + getSender() + "] to [" + getReceiver()
				+ "] using " + getModeString() + "\n");

		// loading properties from properties file
		loadProps();

		// if mode == 1 mode 1 method
		if (Mode == 1) {
			slidingWindows();
		} else {
			stopAndWait();
		}

		// printing session summary
		double endTime = System.currentTimeMillis();
		double duration = (endTime - startTime) / (double) 100;
		System.out.println("Successfully transferred " + FileName + " (" + FileName.length() + " bytes) in " + duration
				+ " seconds");

		return true;
	}

	public static void main(String[] args) {
		RSendUDP sender = new RSendUDP();
		sender.setMode(1);
		sender.setModeParameter(2);
		sender.setTimeout(1000);
		sender.setFilename("important.txt");
		sender.setLocalPort(23456);
		sender.setReceiver(new InetSocketAddress("10.20.28.155", 32456));

		sender.sendFile();
	}

}
