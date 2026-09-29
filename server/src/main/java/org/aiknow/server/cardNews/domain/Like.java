package org.aiknow.server.cardNews.domain;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "like")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Like {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cardNewsId", nullable = false)
    private CardNews cardNews;

//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(referencedColumnName = "users_id", nullable = false)
//    private Users users;

}
