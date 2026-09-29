package org.aiknow.server.cardNews.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "category")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "category",cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CardNewsCategory> cardNewsCategory=new ArrayList<>();

    @Column(length = 20, nullable = false)
    private String name;
}
