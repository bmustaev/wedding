package uz.bobnoza.wedding.repository;

import uz.bobnoza.wedding.entity.GalleryImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface GalleryImageRepository extends JpaRepository<GalleryImage, UUID> {

    List<GalleryImage> findAllByOrderByDisplayOrderAsc();

    @Query("SELECT COALESCE(MAX(g.displayOrder), -1) FROM GalleryImage g")
    int findMaxDisplayOrder();
}
