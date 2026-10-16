package com.example.invoice.repository;

import com.example.invoice.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
	@org.springframework.data.jpa.repository.Query("select count(distinct u.id) from User u join u.userRoles a where u.status = com.example.invoice.entity.UserStatus.ACTIVE and a.role.code = 'ADMIN' and a.role.active = true")
	long countActiveAdmins();
	@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
	@org.springframework.data.jpa.repository.Query("select u from User u where u.username = :username")
	@org.springframework.data.jpa.repository.QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
	Optional<User> findByUsernameForAuthentication(@org.springframework.data.repository.query.Param("username") String username);
	Optional<User> findByUsername(String username);

	boolean existsByUsername(String username);

	boolean existsByEmail(String email);
}
