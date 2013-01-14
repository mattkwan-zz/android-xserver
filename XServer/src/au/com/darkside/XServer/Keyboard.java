/**
 * This class handles an X keyboard.
 */
package au.com.darkside.XServer;

import java.io.IOException;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

/**
 * @author Matthew Kwan
 *
 * This class handles an X keyboard.
 */
public class Keyboard {
	private int				_minimumKeycode;
	private int				_numKeycodes;
	private byte			_keysymsPerKeycode = 3;
	private int[]			_keyboardMapping = null;
	private byte			_keycodesPerModifier = 2;
	private byte[]			_keymap = new byte[32];
	private byte[]			_modifierMapping = new byte[] {
		KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
		0, 0, 0, 0,
		KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
		0, 0, 0, 0, 0, 0, 0, 0
	};

	private static final int	DefaultBellPercent = 50;
	private int					_bellPercent = DefaultBellPercent;
	private static final int	DefaultBellPitch = 400;
	private int					_bellPitch = DefaultBellPitch;
	private static final int	DefaultBellDuration = 100;
	private int					_bellDuration = DefaultBellDuration;
	private short[]				_bellBuffer = null;
	private boolean				_bellBufferFilled = false;
	private AudioTrack			_audioTrack = null;

	private static final int	SAMPLE_RATE = 11025;
	private static final int	AttrKeyClickPercent = 0;
	private static final int	AttrBellPercent = 1;
	private static final int	AttrBellPitch = 2;
	private static final int	AttrBellDuration = 3;
	private static final int	AttrLed = 4;
	private static final int	AttrLedMode = 5;
	private static final int	AttrKey = 6;
	private static final int	AttrAutoRepeatMode = 7;

	/**
	 * Constructor.
	 */
	Keyboard () {
		final byte			kpk = _keysymsPerKeycode;
		int					min = 255;
		int					max = 0;
		int					idx = 0;
		int[]				map = new int[256 * kpk];
		KeyCharacterMap		kcm = KeyCharacterMap.load (
										KeyCharacterMap.BUILT_IN_KEYBOARD);

		for (int i = 0; i < 256; i++) {
			int			c1 = kcm.get (i, 0);
			int			c2 = kcm.get (i, KeyEvent.META_SHIFT_ON);
			int			c3 = kcm.get (i, KeyEvent.META_ALT_ON);

			map[idx++] = c1;
			map[idx++] = c2;
			map[idx++] = c3;

			if (c1 != 0 || c2 != 0 || c3 != 0) {
				if (i < min)
					min = i;
				if (i > max)
					max = i;
			}
 		}

		if (max == 0)
			min = 0;

		if (max < KeyEvent.KEYCODE_DEL)
			max = KeyEvent.KEYCODE_DEL;

		_minimumKeycode = min;
		_numKeycodes = max - min + 1;
		if (_numKeycodes > 248)
			_numKeycodes = 248;

		_keyboardMapping = new int[kpk * _numKeycodes];
		System.arraycopy (map, min * kpk, _keyboardMapping, 0,
													_keyboardMapping.length);

		_keyboardMapping[(KeyEvent.KEYCODE_DEL - min) * kpk] = 127;
		_keyboardMapping[(KeyEvent.KEYCODE_ALT_LEFT - min) * kpk] = 0xff7e;
		_keyboardMapping[(KeyEvent.KEYCODE_ALT_RIGHT - min) * kpk] = 0xff7e;
	}

	/**
	 * Translate an Android keycode to an X keycode.
	 *
	 * @param keycode	The Android keycode.
	 * @return	The corresponding X keycode.
	 */
	public int
	translateToXKeycode (
		int			keycode
	) {
		if (_minimumKeycode < 8)
			return keycode + 8 - _minimumKeycode;
		else
			return keycode;
	}

	/**
	 * Return the minimum keycode.
	 *
	 * @return	The minimum keycode.
	 */
	public int
	getMinimumKeycode () {
		if (_minimumKeycode < 8)
			return 8;
		else
			return _minimumKeycode;
	}

	/**
	 * Return the minimum keycode diff.
	 *
	 * @return	The minimum keycode.
	 */
	private int
	getMinimumKeycodeDiff () {
		if (_minimumKeycode < 8)
			return 8 - _minimumKeycode;
		else
			return 0;
	}

	/**
	 * Return the maximum keycode.
	 *
	 * @return	The maximum keycode.
	 */
	public int
	getMaximumKeycode () {
		return getMinimumKeycode () + _numKeycodes - 1;
	}

	/**
	 * Return the keymap for keycodes 8-255.
	 *
	 * @return	The keymap for keycodes 8-255.
	 */
	public byte[]
	getKeymap () {
		byte[]		keymap = new byte[31];

		System.arraycopy (_keymap, 1, keymap, 0, 31);

		return keymap;
	}

	/**
	 * Update the keymap when a key is pressed or released.
	 *
	 * @param keycode	The keycode of the key.
	 * @param pressed	True if pressed, false if released.
	 */
	public void
	updateKeymap (
		int			keycode,
		boolean		pressed
	) {
		if (keycode < 0 || keycode > 255)
			return;

		int			offset = keycode / 8;
		byte		mask = (byte) (1 << (keycode & 7));

		if (pressed)
			_keymap[offset] |= mask;
		else
			_keymap[offset] &= ~mask;
	}

	/**
	 * Process an X request relating to this keyboard.
	 *
	 * @param xServer	The X server.
	 * @param client	The remote client.
	 * @param opcode	The request's opcode.
	 * @param arg		Optional first argument.
	 * @param bytesRemaining	Bytes yet to be read in the request.
	 * @throws IOException
	 */
	public void
	processRequest (
		XServer		xServer,
		Client		client,
		byte		opcode,
		byte		arg,
		int			bytesRemaining
	) throws IOException {
		InputOutput		io = client.getInputOutput ();

		switch (opcode) {
			case RequestCode.QueryKeymap:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (client, ErrorCode.Length, opcode, 0);
				} else {
					synchronized (io) {
						Util.writeReplyHeader (client, (byte) 0);
						io.writeInt (2);	// Reply length.
						io.writeBytes (_keymap, 0, 32);	// Keys.
					}
					io.flush ();
				}
				break;
			case RequestCode.ChangeKeyboardMapping:
				if (bytesRemaining < 4) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (client, ErrorCode.Length, opcode, 0);
				} else {
					byte	keycodeCount = arg;
					byte	keycode = (byte) io.readByte ();	// First code.
					byte	kspkc = (byte) io.readByte ();	// Keysyms per code.

					io.readSkip (2);	// Unused.
					bytesRemaining -= 4;

					if (bytesRemaining != keycodeCount * kspkc * 4) {
						io.readSkip (bytesRemaining);
						ErrorCode.write (client, ErrorCode.Length, opcode, 0);
					} else {
						_minimumKeycode = keycode;
						_numKeycodes = keycodeCount;
						_keysymsPerKeycode = kspkc;
						_keyboardMapping = new int[keycodeCount * kspkc];
						for (int i = 0; i < _keyboardMapping.length; i++)
							_keyboardMapping[i] = io.readInt ();	// Keysyms.

						xServer.sendMappingNotify (1, keycode, keycodeCount);
					}
				}
				break;
			case RequestCode.GetKeyboardMapping:
				if (bytesRemaining != 4) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (client, ErrorCode.Length, opcode, 0);
				} else {
					int		keycode = io.readByte ();	// First code.
					int		count = io.readByte ();	// Count.
					int		length = count * _keysymsPerKeycode;
					int		offset = (keycode - getMinimumKeycode ())
														* _keysymsPerKeycode;

					io.readSkip (2);	// Unused.

					synchronized (io) {
						Util.writeReplyHeader (client , _keysymsPerKeycode);
						io.writeInt (length);	// Reply length.
						io.writePadBytes (24);	// Unused.

						for (int i = 0; i < length; i++) {
							int		n = i + offset;

							if (n < 0 || n >= _keyboardMapping.length)
								io.writeInt (0);	// No symbol.
							else
								io.writeInt (_keyboardMapping[n]);
						}
					}
					io.flush ();
				}
				break;
			case RequestCode.ChangeKeyboardControl:
				if (bytesRemaining < 4) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (client, ErrorCode.Length, opcode, 0);
				} else {
					int			valueMask = io.readInt ();	// Value mask.
					int			nbits = Util.bitcount (valueMask);

					bytesRemaining -= 4;
					if (bytesRemaining != nbits * 4) {
						io.readSkip (bytesRemaining);
						ErrorCode.write (client, ErrorCode.Length, opcode, 0);
					} else {
						for (int i = 0; i < 23; i++)
							if ((valueMask & (1 << i)) != 0)
								processValue (io, i);
					}
				}
				break;
			case RequestCode.GetKeyboardControl:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (client, ErrorCode.Length, opcode, 0);
				} else {
					synchronized (io) {
						Util.writeReplyHeader (client, _keysymsPerKeycode);
						io.writeInt (5);	// Reply length.
						io.writeInt (0);	// LED mask.
						io.writeByte ((byte) 0);	// Key click percent.
						io.writeByte ((byte) _bellPercent);	// Bell volume.
						io.writeShort ((short) _bellPitch);	// Bell pitch Hz.
						io.writeShort ((short) _bellDuration);
						io.writePadBytes (2);	// Unused.
						io.writePadBytes (32);	// Auto repeats. Ignored.
					}
					io.flush ();
				}
				break;
			case RequestCode.SetModifierMapping:
				if (bytesRemaining != 8 * arg) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (client, ErrorCode.Length, opcode, 0);
				} else {	// Not supported. Always fails.
					io.readSkip (bytesRemaining);
					synchronized (io) {
						Util.writeReplyHeader (client, (byte) 2);
						io.writeInt (0);	// Reply length.
						io.writePadBytes (24);	// Unused.
					}
					io.flush ();
				}
				break;
			case RequestCode.GetModifierMapping:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (client, ErrorCode.Length, opcode, 0);
				} else {
					final byte	kpm = _keycodesPerModifier;
					byte[]		map = null;

					if (kpm > 0) {
						int		 diff = getMinimumKeycodeDiff ();

						map = new byte[kpm * 8];
						for (int i = 0; i < map.length; i++)
							if (_modifierMapping[i] == 0)
								map[i] = 0;
							else
								map[i] = (byte) (_modifierMapping[i] + diff);
					}

					synchronized (io) {
						Util.writeReplyHeader (client, kpm);
						io.writeInt (kpm * 2);	// Reply length.
						io.writePadBytes (24);	// Unused.

						if (map != null)
							io.writeBytes (map, 0, map.length);
					}
					io.flush ();
				}
				break;
			case RequestCode.Bell:
				if (bytesRemaining != 0) {
					io.readSkip (bytesRemaining);
					ErrorCode.write (client, ErrorCode.Length, opcode, 0);
				} else {
					playBell ((byte) arg);
				}
				break;				
			default:
				io.readSkip (bytesRemaining);
				ErrorCode.write (client, ErrorCode.Implementation, opcode, 0);
				break;
		}
	}

	/**
	 * Play a beep.
	 *
	 * @param percent	Volume relative to base volume, [-100, 100]
	 */
	private void
	playBell (
		int			percent
	) {
		int			volume;

		if (percent < 0) {
			volume = _bellPercent + _bellPercent * percent / 100;
			_bellBufferFilled = false;
		} else if (percent > 0){
			volume = _bellPercent - _bellPercent * percent / 100 + percent;
			_bellBufferFilled = false;
		} else {
			volume = _bellPercent;
		}

		if (_bellBuffer == null) {
			_bellBuffer = new short[SAMPLE_RATE * _bellDuration / 1000];
			_bellBufferFilled = false;

		}

		if (!_bellBufferFilled) {
			double		vol = 32767.0 * (double) volume / 100.0;
			double		dt = _bellPitch * 2.0 * Math.PI / SAMPLE_RATE;

			for (int i = 0; i < _bellBuffer.length; i++)
				_bellBuffer[i] = (short) (vol * Math.sin ((double) i * dt));

			_bellBufferFilled = true;
		}

		if (_audioTrack != null) {
			_audioTrack.stop ();
			_audioTrack.release ();
		}

		_audioTrack = new AudioTrack (AudioManager.STREAM_SYSTEM,
					SAMPLE_RATE, AudioFormat.CHANNEL_CONFIGURATION_MONO,
	    			AudioFormat.ENCODING_PCM_16BIT, 2 * _bellBuffer.length,
	    			AudioTrack.MODE_STATIC);

		_audioTrack.write (_bellBuffer, 0, _bellBuffer.length);
		_audioTrack.play ();
	}

	/**
	 * Process a single keyboard attribute value.
	 *
	 * @param io	The input/output stream.
	 * @param maskBit	The mask bit of the attribute.
	 * @throws IOException
	 */
	private void
	processValue (
		InputOutput		io,
		int				maskBit
	) throws IOException {
		switch (maskBit) {
			case AttrKeyClickPercent:
				io.readByte ();	// Not implemented.
				io.readSkip (3);
				break;
			case AttrBellPercent:
				_bellPercent = (byte) io.readByte ();
				if (_bellPercent < 0)
					_bellPercent = DefaultBellPercent;
				io.readSkip (3);
				_bellBufferFilled = false;
				break;
			case AttrBellPitch:
				_bellPitch = (short) io.readShort ();
				if (_bellPitch < 0)
					_bellPitch = DefaultBellPitch;
				io.readSkip (2);
				_bellBufferFilled = false;
				break;
			case AttrBellDuration:
				_bellDuration = (short) io.readShort ();
				if (_bellDuration < 0)
					_bellDuration = DefaultBellDuration;
				io.readSkip (2);
				_bellBuffer = null;
				break;
			case AttrLed:
				io.readByte ();	// Not implemented.
				io.readSkip (3);
				break;
			case AttrLedMode:
				io.readByte ();	// Not implemented.
				io.readSkip (3);
				break;
			case AttrKey:
				io.readByte ();	// Not implemented.
				io.readSkip (3);
				break;
			case AttrAutoRepeatMode:
				io.readByte ();	// Not implemented.
				io.readSkip (3);
				break;
		}
	}
}