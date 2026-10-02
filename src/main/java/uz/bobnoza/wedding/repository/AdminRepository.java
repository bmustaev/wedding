package uz.bobnoza.wedding.repository;

import uz.bobnoza.wedding.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdminRepository extends JpaRepository<Admin, UUID> {

    Optional<Admin> findByUsername(String username);

    boolean existsByUsername(String username);

    /** Whose bank PIN this is (BankCodes#pinDigest) — unique, see uk_admins_bank_pin. */
    Optional<Admin> findByBankPin(String bankPin);
}
