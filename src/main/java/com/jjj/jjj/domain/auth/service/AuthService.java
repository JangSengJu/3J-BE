package com.jjj.jjj.domain.auth.service;

import com.jjj.jjj.domain.auth.dto.request.SignInRequest;
import com.jjj.jjj.domain.auth.dto.request.SignUpRequest;
import com.jjj.jjj.domain.auth.dto.response.MeResponse;
import com.jjj.jjj.domain.auth.dto.response.SignInResponse;
import com.jjj.jjj.domain.auth.dto.response.SignUpResponse;
import com.jjj.jjj.domain.auth.dto.response.UserResponse;
import com.jjj.jjj.domain.auth.entity.Member;
import com.jjj.jjj.domain.auth.enums.Role;
import com.jjj.jjj.domain.auth.repository.MemberRepository;
import com.jjj.jjj.domain.auth.repository.RefreshTokenRepository;
import com.jjj.jjj.global.exception.BusinessException;
import com.jjj.jjj.global.exception.ErrorCode;
import com.jjj.jjj.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {
        String username = normalizeId(request.id());

        if (memberRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.DUPLICATED_USERNAME);
        }

        Member member = Member.create(username, passwordEncoder.encode(request.password()), Role.USER);
        memberRepository.save(member);

        return new SignUpResponse(UserResponse.from(member));
    }

    @Transactional(readOnly = true)
    public SignInResult signIn(SignInRequest request) {
        String username = normalizeId(request.id());

        Member member = memberRepository.findByUsername(username)
                .filter(found -> passwordEncoder.matches(request.password(), found.getPassword()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        String accessToken = jwtProvider.createAccessToken(member.getId(), member.getRole().getKey());
        String refreshToken = issueRefreshToken(member.getId());

        return new SignInResult(new SignInResponse(accessToken, UserResponse.from(member)), refreshToken);
    }

    @Transactional(readOnly = true)
    public RefreshResult refresh(String refreshToken) {
        if (refreshToken == null || !jwtProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        String jti = jwtProvider.getJti(refreshToken);
        Long memberId = refreshTokenRepository.consumeByJti(jti)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

        String newAccessToken = jwtProvider.createAccessToken(member.getId(), member.getRole().getKey());
        String newRefreshToken = issueRefreshToken(member.getId());

        return new RefreshResult(newAccessToken, newRefreshToken);
    }

    public void signOut(String refreshToken) {
        if (refreshToken == null || !jwtProvider.validateToken(refreshToken)) {
            return;
        }
        refreshTokenRepository.deleteByJti(jwtProvider.getJti(refreshToken));
    }

    @Transactional(readOnly = true)
    public MeResponse getMe(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        return new MeResponse(UserResponse.from(member));
    }

    private String issueRefreshToken(Long memberId) {
        String refreshToken = jwtProvider.createRefreshToken(memberId);
        refreshTokenRepository.save(
                jwtProvider.getJti(refreshToken),
                memberId,
                jwtProvider.getRefreshTokenExpiration()
        );
        return refreshToken;
    }

    private String normalizeId(String id) {
        return id == null ? null : id.trim().toLowerCase();
    }

    public record SignInResult(SignInResponse response, String refreshToken) {
    }

    public record RefreshResult(String accessToken, String refreshToken) {
    }
}
