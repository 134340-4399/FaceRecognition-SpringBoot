    package org.example.facerecognitionspringboot.controller;

    import org.example.facerecognitionspringboot.dao.EmployeeRepository;
    import org.example.facerecognitionspringboot.entity.Employee;
    import org.example.facerecognitionspringboot.utils.RsaUtils;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
    import org.springframework.web.bind.annotation.*;
    import jakarta.annotation.PostConstruct;

    import java.io.*;
    import java.security.KeyFactory;
    import java.security.PrivateKey;
    import java.security.PublicKey;
    import java.security.spec.PKCS8EncodedKeySpec;
    import java.security.spec.X509EncodedKeySpec;
    import java.util.Base64;
    import java.util.HashMap;
    import java.util.Map;

    @RestController
    @RequestMapping("/api/auth")
    public class AuthController {

        @Autowired
        private EmployeeRepository employeeRepository;

        private static String publicKeyBase64;
        private static PrivateKey privateKey;
        private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        // ===== RSA 密钥持久化路径 =====
        private static final String KEY_DIR =
                System.getProperty("user.home") + File.separator + ".face_attendance";
        private static final String KEY_FILE = KEY_DIR + File.separator + "rsa_keys.ser";

        static {
            loadOrGenerateKeys();
        }

        @PostConstruct
        public void initKeys() {
            if (publicKeyBase64 == null || publicKeyBase64.isEmpty()) {
                loadOrGenerateKeys();
            }
        }

        /**
         * 加载已有密钥文件，不存在或损坏则重新生成
         */
        private static synchronized void loadOrGenerateKeys() {
            if (publicKeyBase64 != null && !publicKeyBase64.isEmpty()) return;

            File keyFile = new File(KEY_FILE);
            if (keyFile.exists()) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(keyFile))) {
                    byte[] privateBytes = (byte[]) ois.readObject();
                    byte[] publicBytes = (byte[]) ois.readObject();
                    KeyFactory kf = KeyFactory.getInstance("RSA");
                    privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
                    PublicKey pubKey = kf.generatePublic(new X509EncodedKeySpec(publicBytes));
                    publicKeyBase64 = Base64.getEncoder().encodeToString(pubKey.getEncoded());
                    System.out.println("🔐 RSA 密钥对已从文件加载");
                    return;
                } catch (Exception e) {
                    System.err.println("⚠️ 密钥文件损坏，重新生成...");
                }
            }
            generateAndSaveKeys();
        }

        /**
         * 生成新密钥对并持久化到文件（重启不丢失）
         */
        private static void generateAndSaveKeys() {
            try {
                Map<String, Object> keyMap = RsaUtils.genKeyPair();
                privateKey = (PrivateKey) keyMap.get("PrivateKey");
                PublicKey pubKey = (PublicKey) keyMap.get("PublicKey");
                publicKeyBase64 = Base64.getEncoder().encodeToString(pubKey.getEncoded());

                File dir = new File(KEY_DIR);
                if (!dir.exists()) dir.mkdirs();
                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(KEY_FILE))) {
                    oos.writeObject(privateKey.getEncoded());   // PKCS#8 编码
                    oos.writeObject(pubKey.getEncoded());        // X.509 编码
                }
                System.out.println("🔐 RSA 密钥对已生成并持久化到: " + KEY_FILE);
            } catch (Exception e) {
                System.err.println("❌ RSA 密钥生成失败！");
                e.printStackTrace();
            }
        }

        @GetMapping("/public-key")
        public Map<String, Object> getPublicKey() {
            Map<String, Object> result = new HashMap<>();
            if (publicKeyBase64 != null && !publicKeyBase64.isEmpty()) {
                result.put("success", true);
                result.put("publicKey", publicKeyBase64);
            } else {
                result.put("success", false);
                result.put("message", "安全模块启动中，请稍后刷新重试");
            }
            return result;
        }

        @PostMapping("/login")
        public Map<String, Object> login(@RequestParam("username") String username,
                                         @RequestParam("password") String encryptedPassword) {
            Map<String, Object> result = new HashMap<>();

            Employee emp = employeeRepository.findByUsername(username);
            if (emp == null) {
                result.put("success", false);
                result.put("message", "账号不存在！");
                return result;
            }

            try {
                String rawPassword = RsaUtils.decrypt(encryptedPassword, privateKey);
                if (emp.getPassword() == null || !passwordEncoder.matches(rawPassword, emp.getPassword())) {
                    result.put("success", false);
                    result.put("message", "密码错误！");
                    return result;
                }
            } catch (Exception e) {
                result.put("success", false);
                result.put("message", "安全校验解析失败！");
                return result;
            }

            result.put("success", true);
            result.put("message", "登录成功");
            result.put("id", emp.getId());
            result.put("username", emp.getName());
            result.put("role", emp.getRole());
            result.put("token", org.example.facerecognitionspringboot.utils.JwtUtils
                    .generateToken(emp.getId(), emp.getRole()));
            return result;
        }
    }
