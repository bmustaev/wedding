package uz.bobnoza.wedding.repository;

import uz.bobnoza.wedding.entity.GuestMedia;
import uz.bobnoza.wedding.entity.Hall;
import uz.bobnoza.wedding.entity.MediaType;
import uz.bobnoza.wedding.entity.MediaVisibility;
import uz.bobnoza.wedding.entity.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuestMediaRepository extends JpaRepository<GuestMedia, UUID> {

    List<GuestMedia> findAllByGuestIdOrderByMediaTypeAscUploadedAtAsc(UUID guestId);

    long countByGuestIdAndMediaType(UUID guestId, MediaType mediaType);

    Optional<GuestMedia> findByIdAndGuestId(UUID id, UUID guestId);

    List<GuestMedia> findAllByProcessingStatus(ProcessingStatus processingStatus);

    /** A guest's quest uploads (photos and videos), quest fetched. */
    @Query("select m from GuestMedia m join fetch m.quest where m.guest.id = :guestId")
    List<GuestMedia> findAllQuestUploadsOfGuest(@Param("guestId") UUID guestId);

    boolean existsByGuestIdAndQuestId(UUID guestId, UUID questId);

    /** How many guests have completed each of a hall's quests, as [questId, count] (failed conversions don't count). */
    @Query("select m.quest.id, count(m) from GuestMedia m where m.quest.hall = :hall and m.guest.deleted = false " +
           "and m.processingStatus <> :failed group by m.quest.id")
    List<Object[]> countQuestCompletions(@Param("hall") Hall hall, @Param("failed") ProcessingStatus failed);

    /** One hall's media of one type, with guest + table fetched for grouping by table. */
    @Query("select m from GuestMedia m join fetch m.guest g join fetch g.admin left join fetch g.table left join fetch m.quest " +
           "where g.deleted = false and g.hall = :hall and m.mediaType = :mediaType")
    List<GuestMedia> findAllInHall(@Param("hall") Hall hall, @Param("mediaType") MediaType mediaType);

    /** One hall's finished photos and videos with the given visibility — the guests' shared feed. */
    @Query("select m from GuestMedia m join fetch m.guest g left join fetch g.table left join fetch m.quest " +
           "where g.deleted = false and g.hall = :hall and m.visibility = :visibility and m.processingStatus = :status")
    List<GuestMedia> findAllInHallByVisibility(@Param("hall") Hall hall, @Param("visibility") MediaVisibility visibility,
                                               @Param("status") ProcessingStatus status);

    @Transactional
    @Modifying
    @Query("update GuestMedia m set m.displayKey = :displayKey, m.thumbKey = :thumbKey, " +
           "m.durationSeconds = :durationSeconds, m.widthPx = :widthPx, m.heightPx = :heightPx, " +
           "m.processingStatus = :status, m.processingError = null where m.id = :id")
    int markReady(@Param("id") UUID id, @Param("displayKey") String displayKey, @Param("thumbKey") String thumbKey,
                  @Param("durationSeconds") Integer durationSeconds, @Param("widthPx") Integer widthPx,
                  @Param("heightPx") Integer heightPx, @Param("status") ProcessingStatus status);

    @Transactional
    @Modifying
    @Query("update GuestMedia m set m.processingStatus = :status, m.processingError = :error where m.id = :id")
    int markFailed(@Param("id") UUID id, @Param("error") String error, @Param("status") ProcessingStatus status);
}
