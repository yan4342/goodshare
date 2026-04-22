package yan.goodshare.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${app.jwtSecret}")
    private String jwtSecret;

    @Value("${app.jwtExpirationInMs}")
    private int jwtExpirationInMs;
    
    @Value("${app.jwtRefreshExpirationInMs:604800000}") // Default 7 days
    private int jwtRefreshExpirationInMs;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String REFRESH_TOKEN_PREFIX = "jwt:refresh:";

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String generateToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return generateToken(userDetails.getUsername());
    }

    public String generateToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }
    
    public String generateRefreshToken(String username) {
        String refreshToken = UUID.randomUUID().toString();
        // Save refresh token to Redis whitelist
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + username,
                refreshToken,
                jwtRefreshExpirationInMs,
                TimeUnit.MILLISECONDS
        );
        return refreshToken;
    }

    public String getUsernameFromJWT(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }

    public boolean validateToken(String authToken) {
        // Check if token is in blacklist
        if (Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + authToken))) {
            logger.warn("Token is blacklisted");
            return false;
        }

        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(authToken);
            return true;
        } catch (Exception ex) {
            logger.error("Token validation failed: " + ex.getMessage());
        }
        return false;
    }
    
    public void invalidateToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            
            Date expiration = claims.getExpiration();
            long ttl = expiration.getTime() - System.currentTimeMillis();
            
            if (ttl > 0) {
                // Add to blacklist until it expires
                redisTemplate.opsForValue().set(
                        BLACKLIST_PREFIX + token,
                        "revoked",
                        ttl,
                        TimeUnit.MILLISECONDS
                );
            }
            
            // Remove refresh token
            String username = claims.getSubject();
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + username);
        } catch (Exception ex) {
            logger.error("Failed to invalidate token: " + ex.getMessage());
        }
    }
    
    public boolean validateRefreshToken(String username, String refreshToken) {
        Object storedToken = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + username);
        return storedToken != null && storedToken.equals(refreshToken);
    }
}
