package com.jjj.jjj.domain.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redisTemplate;

    public void save(String jti, Long memberId, Duration ttl) {
        redisTemplate.opsForValue().set(key(jti), String.valueOf(memberId), ttl);
    }

    public Optional<Long> findMemberIdByJti(String jti) {
        String memberId = redisTemplate.opsForValue().get(key(jti));
        if (memberId == null) {
            return Optional.empty();
        }
        return Optional.of(Long.valueOf(memberId));
    }

    public boolean existsByJti(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(jti)));
    }

    public void deleteByJti(String jti) {
        redisTemplate.delete(key(jti));
    }

    private String key(String jti) {
        return KEY_PREFIX + jti;
    }
}
