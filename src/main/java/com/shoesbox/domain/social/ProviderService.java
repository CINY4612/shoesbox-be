package com.shoesbox.domain.social;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.shoesbox.domain.auth.CustomUserDetails;
import com.shoesbox.domain.auth.RedisService;
import com.shoesbox.domain.auth.dto.TokenResponseDto;
import com.shoesbox.domain.member.Member;
import com.shoesbox.domain.member.MemberRepository;
import com.shoesbox.domain.social.dto.GoogleProfile;
import com.shoesbox.domain.social.dto.KakaoProfile;
import com.shoesbox.domain.social.dto.NaverProfile;
import com.shoesbox.domain.social.dto.ProfileDto;
import com.shoesbox.global.config.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class ProviderService {
    private static final String BASE_PROFILE_IMAGE_URL = "https://i.ibb.co/N27FwdP/image.png";
    private final MemberRepository memberRepository;
    private final OAuthRequestFactory oAuthRequestFactory;
    private final JwtProvider jwtProvider;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final Gson gson;
    private final RedisService redisService;

    @Transactional
    public TokenResponseDto SocialLogin(String code, String provider) throws JsonProcessingException {
        String accessToken = getAccessToken(code, provider);

        ProfileDto profileDto = getProfile(accessToken, provider);
        log.info(">>>>>>>>> user email : " + profileDto.getEmail());
        log.info(">>>>>>>>> user photo : " + profileDto.getProfileImage());

        Member member = memberRepository.findByEmail(profileDto.getEmail()).orElse(null);
        if (member == null) {
            // db에 없을 경우 등록 후 토큰 생성
            String password = UUID.randomUUID().toString(); // 랜덤 password 생성
            member = Member.builder()
                    .email(profileDto.getEmail())
                    .password(bCryptPasswordEncoder.encode(password))
                    .nickname(profileDto.getEmail().split("@")[0])
                    .profileImageUrl(profileDto.getProfileImage())
                    .build();
            memberRepository.save(member);
        }

        // db에 있을 시 그냥 토큰 생성
        return getTokenInfo(member);
    }

    @Transactional
    public TokenResponseDto getTokenInfo(Member member) {
        // 강제 로그인 처리
        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(member.getAuthority().split(","))
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

        CustomUserDetails principal = CustomUserDetails.builder()
                .email(member.getEmail())
                .memberId(member.getId())
                .authorities(authorities)
                .nickname(member.getNickname())
                .build();

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                authorities));

        // 토큰 생성
        TokenResponseDto tokenResponseDto = jwtProvider.createTokenDto(principal);

        // Reids에 Refresh Token 저장
        String refreshToken = tokenResponseDto.getRefreshToken();
        redisService.setDataWithExpiration("RT:" + member.getEmail(), refreshToken,
                tokenResponseDto.getRefreshTokenLifetimeInMs());

        // 토큰 발급
        return tokenResponseDto;
    }

    private String getAccessToken(String code, String provider) throws JsonProcessingException {
        // HTTP Header 생성
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        // provider로 선택한 소셜 정보를 불러와 oAuthRequest에 저장
        OAuthRequest oAuthRequest = oAuthRequestFactory.getRequest(code, provider);
        HttpEntity<LinkedMultiValueMap<String, String>> request = new HttpEntity<>(oAuthRequest.getMap(), headers);

        RestTemplate rt = new RestTemplate();
        ResponseEntity<String> response = rt.postForEntity(oAuthRequest.getTokenUrl(), request, String.class);

        // HTTP 응답 (JSON) -> 액세스 토큰 파싱
        String responseBody = response.getBody();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(responseBody);

        return jsonNode.get("access_token").asText();
    }

    public ProfileDto getProfile(String accessToken, String provider) {
        // HTTP Header 생성
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        String profileUrl = oAuthRequestFactory.getProfileUrl(provider);
        HttpEntity<LinkedMultiValueMap<String, String>> request = new HttpEntity<>(null, headers);
        RestTemplate rt = new RestTemplate();
        ResponseEntity<String> response = rt.postForEntity(profileUrl, request, String.class);

        return extractProfile(response, provider);
    }

    private ProfileDto extractProfile(ResponseEntity<String> response, String provider) {
        if (provider.equals("kakao")) {
            KakaoProfile kakaoProfile = gson.fromJson(response.getBody(), KakaoProfile.class);
            return ProfileDto.builder()
                    .email(kakaoProfile.getKakao_account().getEmail())
                    .profileImage(kakaoProfile.getKakao_account().getProfile().getProfile_image_url())
                    .build();
        } else if (provider.equals("naver")) {
            NaverProfile naverProfile = gson.fromJson(response.getBody(), NaverProfile.class);
            return ProfileDto.builder()
                    .email(naverProfile.getResponse().getEmail())
                    .profileImage(naverProfile.getResponse().getProfile_image())
                    .build();
        } else {
            GoogleProfile googleProfile = gson.fromJson(response.getBody(), GoogleProfile.class);
            String checkedPicture = googleProfile.getPicture();
            if (!checkedPicture.contains(".bmp") || !checkedPicture.contains(".jpg") || !checkedPicture.contains(".jpeg") || !checkedPicture.contains(".png")) {
                log.info(">>>>>>>>> 구글 프로필 이미지 파일 형식이 잘못되어 기본 프로필사진으로 전환");
                checkedPicture = BASE_PROFILE_IMAGE_URL;
            }

            return ProfileDto.builder()
                    .email(googleProfile.getEmail())
                    .profileImage(checkedPicture)
                    .build();
        }
    }
}
