package com.jjj.jjj.domain.auth.repository;

import com.jjj.jjj.domain.auth.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
