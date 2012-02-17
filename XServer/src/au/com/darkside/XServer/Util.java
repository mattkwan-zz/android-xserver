/**
 * Utility functions.
 */
package au.com.darkside.XServer;

import java.io.IOException;

/**
 * @author Matthew Kwan
 *
 * Utility functions.
 */
public class Util {
	/**
	 * Count the number of bits in an integer.
	 *
	 * @param n	The integer containing the bits.
	 * @return	The number of bits in the integer.
	 */
	public static int
	bitcount (
		int		n
	) {
		int		c = 0;

		while (n != 0) {
			c += n & 1;
			n >>= 1;
		}

		return c;
	}

	/**
	 * Write the header of a reply.
	 *
	 * @param io	The input/output stream.
	 * @param arg	Optional argument.
	 * @param sequenceNumber	The request sequence number.
	 * @throws IOException
	 */
	public static void
	writeReplyHeader (
		InputOutput		io,
		int				arg,
		int				sequenceNumber
	) throws IOException {
		io.writeByte ((byte) 1);	// Reply.
		io.writeByte ((byte) arg);
		io.writeShort ((short) (sequenceNumber & 0xffff));
	}

	/**
	 * Sample code for setting a view from a non-GUI thread.
	 *
	 * @param visibility	The view's visibility.
	 *
	public void
	handlerSetVisibility (
		final int	visibility
	) {
		final View	view = this;
		Handler		handler = getHandler ();

		if (handler == null) {
			setVisibility (visibility);
		} else {
			getHandler().post (new Runnable () {
				public void run () {
					view.setVisibility (visibility);
				}
			});
		}
	} */

}
