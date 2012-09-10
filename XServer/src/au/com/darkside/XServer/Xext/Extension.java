package au.com.darkside.XServer.Xext;

/**
 * This class holds details of an extension.
 *
 * @author Matthew Kwan
 */
public class Extension {
	public final byte		majorOpcode;
	public final byte		firstEvent;
	public final byte		firstError;

	/**
	 * Constructor.
	 *
	 * @param pmajorOpcode	Major opcode of the extension, or zero.
	 * @param pfirstEvent	Base event type code, or zero.
	 * @param pfirstError	Base error code, or zero.
	 */
	public Extension (
		byte	pmajorOpcode,
		byte	pfirstEvent,
		byte	pfirstError
	) {
		majorOpcode = pmajorOpcode;
		firstEvent = pfirstEvent;
		firstError = pfirstError;
	}
}