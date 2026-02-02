package flashtanki.server.news

import jakarta.persistence.*

@Entity
@Table(name = "news")
data class NewsEntity(
    @Id
    @Column(length = 64)
    val id: String = "",

    @Column(nullable = false)
    val image: String = "",

    @Column(nullable = false)
    val date: String = "",

    @OneToMany(
        mappedBy = "news",
        fetch = FetchType.LAZY,
        cascade = [CascadeType.ALL]
    )
    val locales: List<NewsLocaleEntity> = emptyList()
)

