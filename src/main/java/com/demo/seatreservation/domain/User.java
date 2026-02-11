package com.demo.seatreservation.domain;

import com.demo.seatreservation.domain.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 사용자(회원) Entity
 *
 * DB의 users 테이블과 매핑됨
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
public class User {

    /* PK (자동 증가) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
    *  로그인 이메일
    *  UNIQUE 제약 -> 중복 가입 방지
    */
    @Column(nullable = false, length = 100)
    private String email;

    /* 비밀번호 (BCrypt 해시 값 저장 예정) */
    @Column(nullable = false)
    private String password;

    /* 사용자 권한 (USER / ADMIN) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /* 생성 시간 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * DB에 insert 되기 직전 자동 실행
     * createdAt, role 기본값 세팅
     */
    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (role == null) role = Role.USER;
    }
}
