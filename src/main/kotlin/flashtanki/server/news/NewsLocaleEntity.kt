package flashtanki.server.news;


import jakarta.persistence.*

@Entity
@Table(name = "news_locale")
data class NewsLocaleEntity(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Int = 0,

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "news_id", referencedColumnName = "id")
        val news: NewsEntity,

        @Column(nullable = false)
        val locale: String = "", // RU, EN, PT

        @Column(nullable = false)
        val header: String = "",

        @Column(nullable = false, columnDefinition = "TEXT")
        val text: String = ""
)


