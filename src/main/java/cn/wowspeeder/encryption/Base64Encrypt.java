package cn.wowspeeder.encryption;

import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Base64.Decoder;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import java.security.Key;
import java.security.SecureRandom;

public class Base64Encrypt {
	private Key key;

	private static Base64Encrypt base64Encrypt = new Base64Encrypt();

	private Base64Encrypt(){}

	public static Base64Encrypt getInstance() {
		return base64Encrypt;
	}

	public void init(String password) throws Exception {
		setKey(password);
	}

	/**
	 * 根据参数生成KEY
	 *
	 * @param strKey
	 */
	private void setKey(String strKey) throws Exception {
		try {
			SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
			secureRandom.setSeed(strKey.getBytes());
			KeyGenerator generator = KeyGenerator.getInstance("DES");
			generator.init(secureRandom);
			this.key = generator.generateKey();
			generator = null;
		} catch (Exception e) {
			throw e;
		}
	}

	/**
	 * 加密String明文输入,String密文输出
	 *
	 * @param strMing
	 * @return
	 */
	public String getEncString(String strMing) throws Exception {
		byte[] byteMi = null;
		byte[] byteMing = null;
		String strMi = "";
		Encoder base64en = Base64.getEncoder();
		try {
			byteMing = strMing.getBytes("UTF-8");
			byteMi = this.getEncCode(byteMing);
			strMi = new String(base64en.encode(byteMi), "UTF-8");
		} catch (Exception e) {
			throw e;
		} finally {
			base64en = null;
			byteMing = null;
			byteMi = null;
		}
		return strMi;
	}

	/**
	 * 解密 以String密文输入,String明文输出
	 *
	 * @param strMi
	 * @return
	 */
	public String getDesString(String strMi) throws Exception {
		Decoder base64De = Base64.getDecoder();
		byte[] byteMing = null;
		byte[] byteMi = null;
		String strMing = "";
		try {
			byteMi = base64De.decode(strMi);
			byteMing = this.getDesCode(byteMi);
			strMing = new String(byteMing, "UTF-8");
		} catch (Exception e) {
			throw e;
		} finally {
			base64De = null;
			byteMing = null;
			byteMi = null;
		}
		return strMing;
	}

	/**
	 * 加密以byte[]明文输入,byte[]密文输出
	 *
	 * @param byteS
	 * @return
	 * @throws Exception
	 */
	private byte[] getEncCode(byte[] byteS) throws Exception {
		byte[] byteFina = null;
		Cipher cipher;
		try {
			cipher = Cipher.getInstance("DES");
			cipher.init(Cipher.ENCRYPT_MODE, key);
			byteFina = cipher.doFinal(byteS);
		} catch (Exception e) {
			throw e;
		} finally {
			cipher = null;
		}
		return byteFina;
	}

	/**
	 * 解密以byte[]密文输入,以byte[]明文输出
	 *
	 * @param byteD
	 * @return
	 * @throws Exception
	 */
	private byte[] getDesCode(byte[] byteD) throws Exception {
		Cipher cipher;
		byte[] byteFina = null;
		try {
			cipher = Cipher.getInstance("DES");
			cipher.init(Cipher.DECRYPT_MODE, key);
			byteFina = cipher.doFinal(byteD);
		} catch (Exception e) {
			throw e;
		} finally {
			cipher = null;
		}
		return byteFina;
	}

	public static byte[] hex2byte(String strhex) {
		System.out.println("strhex:"+strhex);
		if (strhex == null) {
			return null;
		}
		int l = strhex.length();
		byte[] b = new byte[l / 2];
		for (int i = 0; i != l / 2; i++) {
			b[i] = (byte) Integer.parseInt(strhex.substring(i * 2, i * 2 + 2), 16);
		}

		return b;
	}
}