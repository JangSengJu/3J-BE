package com.jjj.jjj.domain.auth.entity;

import com.jjj.jjj.domain.auth.enums.Role;
import com.jjj.jjj.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "members")
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private String password;

    private Member(String username, String password, Role role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public static Member create(String username, String password, Role role) {
        return new Member(username, password, role);
    }
}
