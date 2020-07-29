/**
 * This class handles requests relating to extensions.
 */
package au.com.darkside.XServer.Xext;

import java.io.IOException;

import au.com.darkside.XServer.Client;
import au.com.darkside.XServer.ErrorCode;
import au.com.darkside.XServer.InputOutput;
import au.com.darkside.XServer.Util;
import au.com.darkside.XServer.XServer;


/**
 * @author mkwan
 *
 * This class handles requests relating to extensions.
 */
public class Extensions {
	public static final byte	XGE = -128;
	public static final byte	XTEST = -127;
	public static final byte	BigRequests = -126;
	public static final byte	Shape = -125;

	/**
	 * Process a request relating to an X extension.
	 *
	 * @param xServer	The X server.
	 * @param client	The remote client.
	 * @param opcode	The request's opcode.
	 * @param arg	Optional first argument.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @throws IOException
	 */
	public static void
	processRequest (
		XServer 	xServer,
		Client		client,
		byte		opcode,
		byte		arg,
		int			bytesRemaining
	) throws IOException {
		InputOutput		io = client.getInputOutput ();

		switch (opcode) {
			case XGE:
				if (bytesRemaining != 4) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (client, ErrorCode.Length, opcode, 0);
				} else {	// Assume arg == 0 (GEQueryVersion).
					short	xgeMajor = (short) io.readShort ();
					short	xgeMinor = (short) io.readShort ();

					synchronized (io) {
						Util.writeReplyHeader (client, arg);
						io.writeInt (0);	// Reply length.
						io.writeShort (xgeMajor);
						io.writeShort (xgeMinor);
						io.writePadBytes (20);
					}
					io.flush ();
				}
				break;
			case XTEST:
				XTest.processRequest (xServer, client, opcode, arg,
															bytesRemaining);
				break;
			case BigRequests:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (client, ErrorCode.Length, opcode, 0);
				} else {	// Assume arg == 0 (BigReqEnable).
					synchronized (io) {
						Util.writeReplyHeader (client, arg);
						io.writeInt (0);
						io.writeInt (Integer.MAX_VALUE);
						io.writePadBytes (20);
					}
					io.flush ();
				}
				break;
			case Shape:
				XShape.processRequest (xServer, client, opcode, arg,
															bytesRemaining);
				break;
			default:
				io.readSkip (bytesRemaining);	// Not implemented.
				ErrorCode.write (client, ErrorCode.Implementation, opcode, 0);
				break;
		}
	}
}