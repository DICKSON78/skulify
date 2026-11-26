package org.example.school_wallet.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByPhone(String phone);

    boolean existsByRolesContaining(Role role);

    long countByEnabledTrue();

    @org.springframework.data.jpa.repository.Query("""
            select u from AppUser u
            where (:q is null
                   or lower(u.username) like lower(concat('%', :q, '%'))
                   or lower(u.fullName) like lower(concat('%', :q, '%')))
              and (:role is null or :role member of u.roles)
            order by u.fullName
            """)
    Page<AppUser> search(
            @org.springframework.data.repository.query.Param("q") String q,
            @org.springframework.data.repository.query.Param("role") Role role,
            Pageable pageable);
}
