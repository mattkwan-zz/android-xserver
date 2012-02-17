/**
 * This class implements a selection.
 */
package au.com.darkside.XServer;

import java.io.IOException;


/**
 * @author Matthew KWan
 * 
 * This class implements a selection.
 */
public class Selection {
	private final int		_id;
	private ClientComms		_owner = null;
	private Window			_ownerWindow = null;
	private int				_lastChangeTime = 0;

	/**
	 * Constructor.
	 *
	 * @param id		The selection's ID.
	 */
	public Selection (
		int			id
	) {
		_id = id;
	}

	/**
	 * Return the selection's atom ID.
	 *
	 * @return	The selection's atom ID.
	 */
	public int
	getId () {
		return _id;
	}

	/**
	 * If the selection is owned by the client, clear it.
	 * This occurs when a client disconnects.
	 *
	 * @param client	The disconnecting client.
	 */
	public void
	clearClient (
		ClientComms		client
	) {
		if (_owner == client) {
			_owner = null;
			_ownerWindow = null;
		}
	}

	/**
	 * Process an X request relating to selections.
	 *
	 * @param xServer	The X server.
	 * @param client	The client issuing the request.
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param opcode	The request's opcode.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @throws IOException
	 */
	public static void
	processRequest (
		XServer			xServer,
		ClientComms		client,
		InputOutput		io,
		int				sequenceNumber,
		byte			opcode,
		int				bytesRemaining
	) throws IOException {
		switch (opcode) {
			case RequestCode.SetSelectionOwner:
				processSetSelectionOwnerRequest (xServer, client, io,
											sequenceNumber, bytesRemaining);
				break;
			case RequestCode.GetSelectionOwner:
				if (bytesRemaining != 4) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
																opcode, 0);
				} else {
					int			aid = io.readInt ();	// Selection atom.

					if (!xServer.atomExists (aid)) {
						ErrorCode.write (io, ErrorCode.Atom, sequenceNumber,
										RequestCode.SetSelectionOwner, aid);
					} else {
						int			wid = 0;
						Selection	sel = xServer.getSelection (aid);

						if (sel != null && sel._ownerWindow != null)
							wid = sel._ownerWindow.getId ();

						synchronized (io) {
							Util.writeReplyHeader (io, 0, sequenceNumber);
							io.writeInt (0);	// Reply length.
							io.writeInt (wid);	// Owner.
							io.writePadBytes (20);	// Unused.
						}
						io.flush ();
					}
				}
				break;
			case RequestCode.ConvertSelection:
				processConvertSelectionRequest (xServer, client, io,
											sequenceNumber, bytesRemaining);
				break;
			default:
				io.readSkip (bytesRemaining);
				ErrorCode.write (io, ErrorCode.Implementation,
												sequenceNumber, opcode, 0);
				break;
		}
	}

	/**
	 * Process a SetSelectionOwner request.
	 * Change the owner of the specified selection.
	 *
	 * @param xServer	The X server.
	 * @param client	The client issuing the request.
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @throws IOException
	 */
	public static void
	processSetSelectionOwnerRequest (
		XServer			xServer,
		ClientComms		client,
		InputOutput		io,
		int				sequenceNumber,
		int				bytesRemaining
	) throws IOException {
		if (bytesRemaining != 12) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
										RequestCode.SetSelectionOwner, 0);
			return;
		}

		int			wid = io.readInt ();	// Owner window.
		int			aid = io.readInt ();	// Selection atom.
		int			time = io.readInt ();	// Timestamp.
		Window		w = null;

		if (wid != 0) {
			Resource	r = xServer.getResource (wid);

			if (r == null || r.getType () != Resource.WINDOW) {
				ErrorCode.write (io, ErrorCode.Window, sequenceNumber,
									RequestCode.SetSelectionOwner, wid);
				return;
			}

			w = (Window) r;
		}

		Atom		a = xServer.getAtom (aid);

		if (a == null) {
			ErrorCode.write (io, ErrorCode.Atom, sequenceNumber,
									RequestCode.SetSelectionOwner, aid);
			return;
		}

		Selection	sel = xServer.getSelection (aid);

		if (sel == null) {
			sel = new Selection (aid);
			xServer.addSelection (sel);
		}

		int			now = xServer.getTimestamp ();

		if (time != 0) {
			if (time < sel._lastChangeTime || time >= now)
				return;
		} else {
			time = now;
		}

		sel._lastChangeTime = time;
		sel._ownerWindow = w;

		if (sel._owner != null && sel._owner != client)
			EventCode.sendSelectionClear (sel._owner, time, w, a);

		sel._owner = (w != null) ? client : null;
	}

	/**
	 * Process a ConvertSelection request.
	 *
	 * @param xServer	The X server.
	 * @param client	The client issuing the request.
	 * @param io	The input/output stream.
	 * @param sequenceNumber	The request sequence number.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @throws IOException
	 */
	public static void
	processConvertSelectionRequest (
		XServer			xServer,
		ClientComms		client,
		InputOutput		io,
		int				sequenceNumber,
		int				bytesRemaining
	) throws IOException {
		if (bytesRemaining != 20) {
			io.readSkip (bytesRemaining);
			ErrorCode.write (io, ErrorCode.Length, sequenceNumber,
											RequestCode.ConvertSelection, 0);
			return;
		}

		int			wid = io.readInt ();	// Requestor.
		int			sid = io.readInt ();	// Selection.
		int			tid = io.readInt ();	// Target.
		int			pid = io.readInt ();	// Property.
		int			time = io.readInt ();	// Time.
		Resource	r = xServer.getResource (wid);
		Window		w;
		Atom		selectionAtom, targetAtom, propertyAtom;

		if (r == null || r.getType () != Resource.WINDOW) {
			ErrorCode.write (io, ErrorCode.Window, sequenceNumber,
										RequestCode.ConvertSelection, wid);
			return;
		} else {
			w = (Window) r;
		}

		selectionAtom = xServer.getAtom (sid);
		if (selectionAtom == null) {
			ErrorCode.write (io, ErrorCode.Atom, sequenceNumber,
										RequestCode.ConvertSelection, sid);
			return;
		}

		targetAtom = xServer.getAtom (tid);
		if (targetAtom == null) {
			ErrorCode.write (io, ErrorCode.Atom, sequenceNumber,
										RequestCode.ConvertSelection, tid);
			return;
		}

		propertyAtom = null;
		if (pid != 0 && (propertyAtom = xServer.getAtom (pid)) == null) {
			ErrorCode.write (io, ErrorCode.Atom, sequenceNumber,
										RequestCode.ConvertSelection, pid);
			return;
		}

		ClientComms	owner = null;
		Selection	sel = xServer.getSelection (sid);

		if (sel != null)
			owner = sel._owner;

		if (owner != null) {
			try {
				EventCode.sendSelectionRequest (owner, time, sel._ownerWindow,
							w, selectionAtom, targetAtom, propertyAtom);
			} catch (IOException e) {
			}
		} else {
			EventCode.sendSelectionNotify (client, time, w, selectionAtom,
													targetAtom, propertyAtom);
		}
	}
}
