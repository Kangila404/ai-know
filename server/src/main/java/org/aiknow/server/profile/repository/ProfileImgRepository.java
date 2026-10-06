package org.aiknow.server.profile.repository;


import org.aiknow.server.profile.domain.ProfileImg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfileImgRepository extends JpaRepository<ProfileImg,Long> {

}
