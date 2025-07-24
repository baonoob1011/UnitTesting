package swp.project.adn_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import swp.project.adn_backend.entity.Blog;
import swp.project.adn_backend.entity.CivilService;
import swp.project.adn_backend.entity.ServiceTest;

import java.util.Optional;


@RepositoryRestResource(path = "civil-service")
public interface CivilServiceRepository extends JpaRepository<CivilService,Long> {
    Optional<CivilService> findByService(ServiceTest service);

}
