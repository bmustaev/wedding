package uz.bobnoza.wedding.repository;

import uz.bobnoza.wedding.entity.GuestMedia;
import uz.bobnoza.wedding.entity.MediaType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuestMediaRepository extends JpaRepository<GuestMedia, UUID> {

    List<GuestMedia> findAllByGuestIdOrderByMediaTypeAscUploadedAtAsc(UUID guestId);

    long countByGuestIdAndMediaType(UUID guestId, MediaType mediaType);

    Optional<GuestMedia> findByIdAndGuestId(UUID id, UUID guestId);
}
