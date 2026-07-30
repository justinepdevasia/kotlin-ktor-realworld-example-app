package io.realworld.app.web.controllers

import io.realworld.app.domain.Article
import io.realworld.app.domain.ArticlesDTO
import io.realworld.app.domain.User
import io.realworld.app.domain.repository.ArticleRepository
import io.realworld.app.domain.repository.UserRepository
import io.realworld.app.web.rules.AppRule
import org.apache.http.HttpStatus
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

/**
 * Seeds through the repositories: article create/favorite are still stubbed at the HTTP layer.
 */
class ArticlePopularFeedTest {

    companion object {
        @ClassRule
        @JvmField
        val appRule = AppRule()

        private fun article(title: String) = Article(
            title = title,
            description = "desc of $title",
            body = "body of $title"
        )

        @BeforeClass
        @JvmStatic
        fun seed() {
            val users = UserRepository()
            val articles = ArticleRepository()

            val author = users.create(User(email = "author@valid.com", username = "author", password = "x"))!!
            val reader1 = users.create(User(email = "r1@valid.com", username = "reader_one", password = "x"))!!
            val reader2 = users.create(User(email = "r2@valid.com", username = "reader_two", password = "x"))!!

            val two = articles.create(article("two favorites"), author)
            val one = articles.create(article("one favorite"), author)
            articles.create(article("no favorites"), author)

            articles.favorite(two, reader1)
            articles.favorite(two, reader2)
            articles.favorite(one, reader1)
        }
    }

    @Test
    fun `popular feed is sorted by favorites count descending`() {
        val response = appRule.http.get<ArticlesDTO>("/api/articles/feed/popular")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(listOf(2L, 1L, 0L), response.body.articles.map { it.favoritesCount })
        assertEquals(3, response.body.articlesCount)
        assertEquals("author", response.body.articles.first().author?.username)
    }

    @Test
    fun `popular feed honours limit and offset`() {
        val firstPage = appRule.http.get<ArticlesDTO>("/api/articles/feed/popular", mapOf("limit" to 1))
        assertEquals(1, firstPage.body.articles.size)
        assertEquals(2L, firstPage.body.articles.first().favoritesCount)

        val secondPage = appRule.http.get<ArticlesDTO>(
            "/api/articles/feed/popular", mapOf("limit" to 1, "offset" to 1)
        )
        assertEquals(1, secondPage.body.articles.size)
        assertEquals(1L, secondPage.body.articles.first().favoritesCount)
    }
}
