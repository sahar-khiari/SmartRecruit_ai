package com.smartrecruit.repository;

import com.smartrecruit.entity.RoleName;
import com.smartrecruit.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByRole_Name(RoleName roleName);

    Page<User> findAllByRole_Name(RoleName roleName, Pageable pageable);
}
