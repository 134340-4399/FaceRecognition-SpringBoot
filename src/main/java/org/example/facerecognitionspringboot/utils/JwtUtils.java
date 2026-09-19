package org.example.facerecognitionspringboot.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Date;

@Component
public class JwtUtils {

    // 密钥从配置 jwt.secret 注入（本地值在 application-secret.properties，不入库）
    private static String SECRET_KEY;

    @Value("${jwt.secret:}")
    public void setSecretKey(String secret) {
        SECRET_KEY = secret;
    }

    private static final long EXPIRE_TIME = 7L * 24 * 60 * 60 * 1000; // 7 天

    public static String generateToken(Integer empId, String role) {
        Date expireDate = new Date(System.currentTimeMillis() + EXPIRE_TIME);
        return JWT.create()
                .withClaim("empId", empId)
                .withClaim("role", role)
                .withExpiresAt(expireDate)
                .sign(Algorithm.HMAC256(SECRET_KEY));
    }

    public static boolean verifyToken(String token) {
        try {
            JWT.require(Algorithm.HMAC256(SECRET_KEY)).build().verify(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 Token 中提取角色（用于拦截器做权限判断）
     */
    public static String getRoleFromToken(String token) {
        try {
            DecodedJWT jwt = JWT.require(Algorithm.HMAC256(SECRET_KEY)).build().verify(token);
            return jwt.getClaim("role").asString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 Token 中提取员工 ID
     */
    public static Integer getEmpIdFromToken(String token) {
        try {
            DecodedJWT jwt = JWT.require(Algorithm.HMAC256(SECRET_KEY)).build().verify(token);
            return jwt.getClaim("empId").asInt();
        } catch (Exception e) {
            return null;
        }
    }
}
