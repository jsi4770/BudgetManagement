package com.back.was.domain.auth;

import com.back.was.domain.auth.dto.AuthResponse;
import com.back.was.domain.auth.dto.AuthResult;
import com.back.was.domain.auth.dto.LoginRequest;
import com.back.was.domain.auth.dto.SignupRequest;
import com.back.was.domain.auth.dto.TokenRefreshResponse;
import com.back.was.domain.auth.dto.UserResponse;
import com.back.was.domain.budget.BudgetRepository;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import com.back.was.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** AUTH-01 회원가입, AUTH-02 로그인, AUTH-03 재발급, AUTH-04 로그아웃. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BudgetRepository budgetRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final TokenHasher tokenHasher;

    /**
     * AUTH-01. 가입과 동시에 토큰을 발급해 자동 로그인시킨다.
     *
     * <p>이메일 UNIQUE 제약과 사전 검사를 모두 둔다. 사전 검사는 명세의
     * {@code EMAIL_DUPLICATED} 를 주기 위한 것이고, 제약은 동시 요청 두 개가 검사를
     * 함께 통과하는 경우를 막는다. 그 경합에서 지는 쪽은 제약 위반으로 500이 되는데,
     * 가입 재시도로 해결되는 희귀 케이스라 여기서 따로 다루지 않는다.
     */
    @Transactional
    public AuthResult<AuthResponse> signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }

        User user = userRepository.save(User.signUp(
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password())));

        String refreshToken = issueRefreshToken(user);
        AuthResponse response = AuthResponse.forSignup(
                UserResponse.from(user),
                jwtProvider.createAccessToken(user.getId(), user.getRole().name()),
                jwtProvider.getAccessTokenValiditySeconds());

        return new AuthResult<>(response, refreshToken);
    }

    /**
     * AUTH-02.
     *
     * <p>이메일이 없을 때와 비밀번호가 틀렸을 때 모두 같은
     * {@code INVALID_CREDENTIALS} 를 준다 — 구분하면 어떤 이메일이 가입돼 있는지
     * 알려주게 된다. 명세 AUTH-02 의 오류도 이 하나뿐이다.
     */
    @Transactional
    public AuthResult<AuthResponse> login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        String refreshToken = issueRefreshToken(user);
        AuthResponse response = AuthResponse.forLogin(
                UserResponse.from(user),
                jwtProvider.createAccessToken(user.getId(), user.getRole().name()),
                jwtProvider.getAccessTokenValiditySeconds(),
                budgetRepository.existsByUserId(user.getId()));

        return new AuthResult<>(response, refreshToken);
    }

    /**
     * AUTH-03 토큰 재발급 (rotation).
     *
     * <p><b>Refresh Token 의 JWT 서명을 검증하지 않는다.</b> 유효성의 근거는 DB의
     * REFRESH_TOKEN 행이다 (ERD 2장) — 그래야 로그아웃·회전으로 폐기한 토큰이 즉시
     * 무효가 된다. 서명만 보면 이미 삭제된 행의 토큰도 통과한다. 사용자 식별도
     * {@code sub} 클레임이 아니라 행의 FK 에서 가져온다.
     *
     * <p>ERD 2장의 회전 규칙: 보낸 토큰의 행을 <b>삭제</b>하고 새 토큰으로 <b>새 행</b>을
     * 만든다. 만료일도 새로 30일이 되므로 30일간 미사용이면 재로그인이 필요하다.
     *
     * <p>회전이라 같은 토큰으로 두 번 재발급할 수 없다. 프론트가 페이지 진입마다
     * 호출하므로 탭 두 개가 동시에 호출하면 뒤늦은 쪽은 행을 못 찾아 401 →
     * 재로그인이 된다. 명세가 회전을 요구하므로 그대로 두되, 문제가 되면 짧은
     * 유예 기간(old token grace)을 도입해야 한다.
     */
    @Transactional
    public AuthResult<TokenRefreshResponse> refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHasher.hash(refreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            // 만료된 행은 스케줄러를 기다리지 않고 바로 치운다.
            refreshTokenRepository.delete(stored);
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = stored.getUser();
        refreshTokenRepository.delete(stored);

        String rotated = issueRefreshToken(user);
        TokenRefreshResponse response = new TokenRefreshResponse(
                jwtProvider.createAccessToken(user.getId(), user.getRole().name()),
                jwtProvider.getAccessTokenValiditySeconds());

        return new AuthResult<>(response, rotated);
    }

    /**
     * AUTH-04 로그아웃. 해당 쿠키의 행만 지우므로 다른 기기 로그인은 유지된다.
     *
     * <p><b>멱등하다.</b> 쿠키가 없거나 이미 폐기된 토큰이어도 조용히 성공한다 —
     * 명세 AUTH-04 에 오류 코드가 정의돼 있지 않고, 실패를 알려주면 "그 토큰은
     * 유효했다"는 정보를 주게 되며, 프론트는 어차피 메모리의 Access Token 을 버리면
     * 끝이기 때문이다. 쿠키 삭제는 호출자가 항상 수행한다.
     */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(tokenHasher.hash(refreshToken))
                .ifPresent(refreshTokenRepository::delete);
    }

    /**
     * ERD 2장: 로그인한 기기마다 REFRESH_TOKEN 행 하나. 원문은 반환해 쿠키로만
     * 내보내고 DB에는 해시만 남긴다.
     */
    private String issueRefreshToken(User user) {
        String refreshToken = jwtProvider.createRefreshToken(user.getId());
        refreshTokenRepository.save(RefreshToken.issue(
                user,
                tokenHasher.hash(refreshToken),
                LocalDateTime.now().plusSeconds(jwtProvider.getRefreshTokenValiditySeconds())));
        return refreshToken;
    }
}
