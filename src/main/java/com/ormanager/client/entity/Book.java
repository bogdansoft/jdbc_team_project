package com.ormanager.client.entity;

import com.ormanager.orm.annotation.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "books")
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
public class Book {
    @Id
    private Long id;

    @Column
    @NonNull
    private String title;

    @Column(name = "published_at")
    @NonNull
    private LocalDate publishedAt;

    @ManyToOne(columnName = "publisher_id")
    private Publisher publisher;

    public Book(Long id, @NonNull String title, @NonNull LocalDate publishedAt) {
        this.id = id;
        this.title = title;
        this.publishedAt = publishedAt;
    }

    public Book(@NonNull String title, @NonNull LocalDate publishedAt, Publisher publisher) {
        this.title = title;
        this.publishedAt = publishedAt;
        this.publisher = publisher;
    }
}
