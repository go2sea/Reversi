


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class BytesIntTransfer {
	public static byte[] Int2Bytes(int integer) {
		ByteArrayOutputStream boutput = new ByteArrayOutputStream();
		DataOutputStream doutput = new DataOutputStream(boutput);
		try {
			doutput.writeInt(integer);
		} catch (IOException e) {
			e.printStackTrace();
		}
		byte[] buf = boutput.toByteArray();
		return buf;
	}

	public static int Bytes2Int(byte[] buf) {
		ByteArrayInputStream bintput = new ByteArrayInputStream(buf);
		DataInputStream dintput = new DataInputStream(bintput);
		int result = 0;
		try {
			result = dintput.readInt();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result;
	}
	
}
