package ssh.model.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HMACUtil {
    public static byte[] hmacSha256(byte[] key, byte[] data, int offset, int len) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(data, offset, len);
            return mac.doFinal();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
} 