package org.aiknow.server.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.aiknow.server.common.entity.BaseEntity;

@Getter
@Entity
@Table(name = "profile_img")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileImg extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20, nullable = false)
    private String name;

    @Column(length = 2048, nullable = false)
    private String imgUrl;

    private ProfileImg(String name, String imgUrl) {
        this.name = name;
        this.imgUrl = imgUrl;
    }

    public static ProfileImg create(String name, String imgUrl) {
        return new ProfileImg(name, imgUrl);
    }

    public void update(String name, String imgUrl) {
        this.name = name;
        this.imgUrl = imgUrl;
    }
}
