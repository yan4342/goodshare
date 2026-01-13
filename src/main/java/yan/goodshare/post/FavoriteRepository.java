package yan.goodshare.post;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    Optional<Favorite> findByUserIdAndPostId(Long userId, Long postId);
    List<Favorite> findByUserId(Long userId);
}
