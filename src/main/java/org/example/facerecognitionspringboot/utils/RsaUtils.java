package org.example.facerecognitionspringboot.utils;

import javax.crypto.Cipher;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * <h2>RSA 加解密工具类</h2>
 * <p>用于生成 RSA 密钥对，并实现前端公钥加密、后端私钥解密的密码安全传输。</p>
 *
 * <p><b>安全机制：</b></p>
 * <ul>
 *   <li>前端登录时，用公钥加密密码（即使网络被监听，攻击者也无法解密）</li>
 *   <li>后端收到密文后，用私钥解密得到明文密码</li>
 *   <li>私钥永远保存在服务端，不对外暴露</li>
 * </ul>
 *
 * <p><b>密钥长度：</b> 2048 位，在安全性与性能之间取得平衡。</p>
 */
public class RsaUtils {

    /**
     * 生成一对全新的 RSA 公钥和私钥。
     *
     * <p><b>生成流程：</b></p>
     * <ol>
     *   <li>创建 KeyPairGenerator 实例，指定算法为 RSA</li>
     *   <li>初始化密钥长度为 2048 位</li>
     *   <li>生成密钥对，分别取出公钥和私钥</li>
     *   <li>封装到 Map 中返回，便于后续使用</li>
     * </ol>
     *
     * @return 包含 "PublicKey" 和 "PrivateKey" 两个键的 Map
     * @throws Exception 生成失败时抛出（通常因 JDK 缺少加密提供者）
     */
    public static Map<String, Object> genKeyPair() throws Exception {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048);  // 密钥长度 2048 位
        KeyPair keyPair = keyPairGen.generateKeyPair();
        Map<String, Object> keyMap = new HashMap<>(2);
        keyMap.put("PublicKey", keyPair.getPublic());
        keyMap.put("PrivateKey", keyPair.getPrivate());
        return keyMap;
    }

    /**
     * 使用私钥解密 Base64 编码的密文。
     *
     * <p><b>解密流程：</b></p>
     * <ol>
     *   <li>将 Base64 字符串解码为字节数组</li>
     *   <li>初始化 RSA 解密 Cipher，设置模式为 DECRYPT_MODE，传入私钥</li>
     *   <li>执行解密，得到明文字节数组</li>
     *   <li>将明文字节数组转为 UTF-8 字符串返回</li>
     * </ol>
     *
     * @param encryptedBase64 前端传来的 Base64 编码密文
     * @param privateKey      服务端持有的 RSA 私钥
     * @return 解密后的密码明文
     * @throws Exception 解密失败时抛出（如密钥不匹配或数据损坏）
     */
    public static String decrypt(String encryptedBase64, PrivateKey privateKey) throws Exception {
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedBase64);
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes, "UTF-8");
    }
}