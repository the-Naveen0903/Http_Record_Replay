import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository

// To inherit CRUD methods for Post entity
public interface PostRepository extends JpaRepository<Post, Long> 
{

}


