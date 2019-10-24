import java.net.DatagramPacket;
import edu.utulsa.unet.*;
import com.braju.format.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class RReceiveUDP implements RReceiveUDPI {

	private static String PROP = "unet.properties";
	private static final String SERVER = "10.20.28.155";
	private static int RECPORT = 32456;
	private static int SENDPORT = 23456;
	private static int MTU;
	private static int HL;
	private static int PAYLEN;

	private static File file;
	private static int Mode = 0; // mode
	private static int WindowSize = 256; // modeParameter
	private static String FileName; // filename
	static UDPSocket socket;

	private static byte[] finalArray = new byte[0];

	private static void sendToFinalArray(byte[] data) {
		byte[] test = new byte[finalArray.length + data.length];
		System.arraycopy(finalArray, 0, test, 0, finalArray.length);
		System.arraycopy(data, 0, test, finalArray.length, data.length);
		finalArray = test;
	}

	private static void writeFinalFile() throws IOException {
		FileOutputStream fos = new FileOutputStream(FileName);
		fos.write(finalArray);
		fos.close();
	}

	private static void printPacket(byte[] packet) {
		System.out.println("<----");
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
		if (seq == -1) {
			System.out.println("EOT message received\nEnding Transmission\n");
		} else {
			System.out.println("Message " + seq + " received with " + payload + " actual bytes of data");
		}
	}

	// method to check if packet is an END TRANSMISSION packet (ack = -1)
	private static boolean checkEnd(byte[] packet) {
		byte[] testAck = Arrays.copyOfRange(packet, 4, 8);
		int ack = ByteBuffer.wrap(testAck).getInt();
		if (ack == -1) {
			return true;
		} else {
			return false;
		}
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

		System.out.println();
		System.out.println("~~~~~PROPERTY INFO~~~~~~");
		System.out.println("MTU: " + MTU);
		System.out.println("HL: " + HL);
		System.out.println("PAYLEN: " + PAYLEN);
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

	private static void sendAck(int ackNumber, int seqNumber, UDPSocket socket)
			throws UnknownHostException, IOException {

		byte[] ack = new byte[4];
		ack = ByteBuffer.allocate(4).putInt(ackNumber).array();
		System.out.println("Message " + seqNumber + " is being sent an ACK of " + ackNumber);
		socket.send(new DatagramPacket(ack, ack.length, InetAddress.getByName(SERVER), SENDPORT));
		System.out.println();

	}

	private static void sendInitialAck(UDPSocket socket) throws UnknownHostException, IOException {
		byte[] ack = new byte[4];
		ack = ByteBuffer.allocate(4).putInt(0).array();
		System.out.println("Sending ACK for EOT message\n");
		socket.send(new DatagramPacket(ack, ack.length, InetAddress.getByName(SERVER), SENDPORT));
	}

	private static int initializeTransfer(byte[] buffer, DatagramPacket packet) {

		int endNumber = 0;
		try {
			socket.setSoTimeout(10000);
			int x = 0;
			// continuously receive packets until last EOT packet is received
			while (x != 1) {
				try {
					socket.receive(packet);
					byte[] endNumberBuffer = Arrays.copyOfRange(buffer, 0, 4);
					endNumber = ByteBuffer.wrap(endNumberBuffer).getInt();
					x = 1;
					System.out.println("Received EOT ACK of " + endNumber);
				} catch (SocketTimeoutException e) {
					System.out.println("First setup messge not received");
				}
				sendInitialAck(socket);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return endNumber;
	}

	private static void closeTransfer() {
		// sending 10 extra EOT ACKS in case the last ACK is lost
		try {
			int y = 0;
			while (y < 10) {
				byte[] ack = new byte[4];
				ack = ByteBuffer.allocate(4).putInt(-1).array();
				socket.send(new DatagramPacket(ack, ack.length, InetAddress.getByName(SERVER), SENDPORT));
				y++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void stopAndWait() {
		// initializing variables
		int ackNumber = 1;
		try {
			// setting up packet to be received
			byte[] buffer = new byte[MTU];
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

			// sending inital EOT info
			int endNumber = initializeTransfer(buffer, packet);

			// once initial EOT info is stored, move onto receiving file data
			socket.setSoTimeout(1000);
			int mostRecentWrittenSeq = -5;
			int lastPacket = 0;
			while (ackNumber != (endNumber + 2)) {
				try {
					// receiving the packet
					socket.receive(packet);

					// printing messsage info
					printInfo(buffer);
					// printPacket(buffer);

					// sequence number of the packet received
					byte[] testSeq = Arrays.copyOfRange(buffer, 0, 4);
					int seqNumber = ByteBuffer.wrap(testSeq).getInt();

					// ACK number of the packet received
					byte[] testACK = Arrays.copyOfRange(buffer, 4, 8);
					int testAck = ByteBuffer.wrap(testACK).getInt();

					// last packet is received
					if (seqNumber == endNumber) {
						lastPacket = 1;
					}

					// if we receive a message with seq of -1, that means file transfer complete
					ackNumber = seqNumber;
					// break to EOT finalization
					if (seqNumber == -1) {
						break;
					}

					// saving data into a final array of all data collected
					byte[] seqA = Arrays.copyOfRange(buffer, 0, 4);
					int seq = ByteBuffer.wrap(seqA).getInt();
					// checking to make sure data hasnt been written yet in case of dropped ACK
					if (mostRecentWrittenSeq != seq) {
						// write the trimmed last packet to final array
						if (lastPacket == 1) {
							byte[] headacheBuffer = Arrays.copyOfRange(buffer, 12, (MTU - testAck));
							sendToFinalArray(headacheBuffer);
						}
						// write the packet to the final array
						else {
							byte[] dataToSave = Arrays.copyOfRange(buffer, 12, buffer.length);
							sendToFinalArray(dataToSave);
						}
					}
					mostRecentWrittenSeq = seq;

					// sending the ACK
					sendAck(ackNumber, seqNumber, socket);

					// ACK successfully sent, so increment ACK for next packet
					ackNumber++;

				} catch (SocketTimeoutException e) {
				}

			}
			// ending transmission
			closeTransfer();

			// writing data received to file
			writeFinalFile();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void slidingWindows() {
		// initializing variables
		int ackNumber = 1;
		try {
			// setting up packet to be received
			byte[] buffer = new byte[MTU];
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

			// sending inital EOT info
			int endNumber = initializeTransfer(buffer, packet);

			// once initial EOT info is stored, move onto receiving file data
			socket.setSoTimeout(1000);

			// My receiver Sliding Windows Algorithm
			// 1.) Receives a packet
			// 2.) if the sequence # is one already stored in finalFile, do nothing
			// 3.) if the sequence # corresponds to the first packet in the window,
			// a.) store packet in finalFile corresponding to its seq #
			// b.) if sequence # received = last expected sequence #,
			// send ACK for last packet
			// break out of loop
			// c.) slide window over by 1 + # of consecutive packets after this packet in
			// finalFile
			// d.) if start of window seq # + (windowSize-1) > endNumber, shrink window
			// size by (seq # + (windowSize-1)- iterations)
			// send ACK for packet one space before the window
			// 4.) else, store packet in finalFile corresponding to its seq #
			// 5.) goto step 1
			// 6.) closeTransmission

			int[] writtenData = new int[endNumber];
			for (int x = 0; x < writtenData.length; x++) {
				writtenData[x] = 0;
			}
			byte[][] preFinalFile = new byte[endNumber][1];
			int windowStart = 1;
			int windowEnd = windowStart + WindowSize;
			System.out.println("window start: " + windowStart);
			System.out.println("window end: " + windowEnd);
			System.out.println();

			while (true) {

				try {
					socket.setSoTimeout(1000);
					// 1.) Receives a packet
					socket.receive(packet);

					// printing messsage info
					printInfo(buffer);
					// printPacket(buffer);

					// sequence number of the packet received
					byte[] testSeq = Arrays.copyOfRange(buffer, 0, 4);
					int seqNumber = ByteBuffer.wrap(testSeq).getInt();

					// ACK number of the packet received
					byte[] testACK = Arrays.copyOfRange(buffer, 4, 8);
					int testAck = ByteBuffer.wrap(testACK).getInt();

					// Data of the packet received
					byte[] testData = Arrays.copyOfRange(buffer, 12, MTU);

					// 2.) if the sequence # is one already stored in finalFile, do nothing
					if (seqNumber < 0) {
						break;
					}
					if (writtenData[seqNumber - 1] == 1) {

					} else {

						// storing data received
						System.out.println("Storing message " + seqNumber + " in slot " + seqNumber);
						writtenData[seqNumber - 1] = 1;
						if (seqNumber == endNumber) {
							byte[] headacheBuffer = Arrays.copyOfRange(buffer, 12, (MTU - testAck));
							preFinalFile[seqNumber - 1] = headacheBuffer;

						} else {
							preFinalFile[seqNumber - 1] = testData;
						}

						for (int x = 0; x < writtenData.length; x++) {
							System.out.print(x + 1 + ":" + writtenData[x] + " ");
						}
						System.out.println();

						String payload = new String(preFinalFile[seqNumber - 1]);
					}

					// 3.) if the sequence # corresponds to the first packet in the window,
					if (seqNumber == windowStart) {
						System.out.println(
								"message " + seqNumber + " corresponds to first packet in window, gonna slide");

						// a.) store packet in finalFile corresponding to its seq #
						// preFinalFile[seqNumber-1] = buffer;
						// b.) if sequence # received = last expected sequence #,
						if (seqNumber == endNumber) {
							// send ACK for last packet
							System.out.println("received all messages, ending transmission");
							// break out of loop
							break;
						}
						// c.) slide window over by 1 + # of consecutive packets after this packet in
						// finalFile
						int slide = 1;
						for (int x = seqNumber; x < writtenData.length; x++) {
							if (writtenData[x] == 1) {
								System.out.println("message " + x + " accounted for, increasing slide by 1");
								slide++;
							} else {
								break;
							}
						}

						System.out.println("sliding window over by " + slide);

						windowStart = windowStart + slide;
						windowEnd = windowEnd + slide;

						// d.) if start of window seq # + (windowSize-1) > endNumber, shrink window
						// size by (seq # + (windowSize-1)- iterations)
						// better yet, make the windowEnd the endNumber
						if (windowStart + (WindowSize - 1) > endNumber) {
							windowEnd = endNumber;
						}

						System.out.println("window start: " + windowStart);
						System.out.println("window end: " + windowEnd);

						// send ACK for packet one space before the window
						sendAck(windowStart - 1, windowStart - 1, socket);

						// 4.) else, store packet in finalFile corresponding to its seq #
					} else {
						// preFinalFile[seqNumber-1] = buffer;

					}
				} catch (SocketTimeoutException e) {
					sendAck(windowStart - 1, windowStart - 1, socket);
					if (windowStart -1 >= endNumber) {
						break;
					}
				}
			}

			System.out.println();

			// write final file
			for (int x = 0; x < preFinalFile.length; x++) {
				sendToFinalArray(preFinalFile[x]);
			}
			writeFinalFile();

			// ending transmission
			closeTransfer();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
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

	@Override
	// sets size of window in bytes for sliding windows
	// calling this in stop and wait has no effect
	public boolean setModeParameter(long ModeParameter) {
		try {
			if (Mode == 1) {
				WindowSize = (int) ModeParameter;
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
	// indicates name to give to a received file
	// creates new file with that name so received data can be written to that file
	public void setFilename(String fn) {
		FileName = fn;
		file = new File(fn);
		try {
			file.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	// returns the name of the file to be created
	public String getFilename() {
		return FileName;
	}

	@Override
	// sets the local port for the receiver socket
	public boolean setLocalPort(int lp) {
		RECPORT = lp;
		try {
			socket = new UDPSocket(RECPORT);
			return true;
		} catch (SocketException e) {
			e.printStackTrace();
			return false;
		}
	}

	@Override
	// gets the local port for the receiver socket
	public int getLocalPort() {
		return socket.getLocalPort();
	}

	@Override
	// writes the data received from the sender to the newly created file of the
	// same name
	public boolean receiveFile() {
		double startTime = System.currentTimeMillis();
		// loading properties
		loadProps();

		// if mode == 1 mode 1 method
		try {
			if (Mode == 1) {
				slidingWindows();
			} else {
				stopAndWait();
			}

			// calulating time file transfer took
			double endTime = System.currentTimeMillis();
			double duration = (endTime - startTime) / (double) 1000;

			// telling user end result
			System.out.println("Successfully received " + FileName + " (" + FileName.length() + " bytes) in " + duration
					+ " seconds");

			return true;
		} catch (Exception e) {
			System.out.println("There was an error in receiving the file. Please try again.");
			return false;
		}

	}

	public static void main(String[] args) {
		RReceiveUDP receiver = new RReceiveUDP();
		receiver.setMode(1);
		receiver.setModeParameter(4);
		receiver.setFilename("less_important.txt");
		receiver.setLocalPort(32456); // sets receiver port

		receiver.receiveFile();

	}
}
