package io.realworld.app.web.controllers

import io.realworld.app.domain.Article
import io.realworld.app.domain.ArticlesDTO
import io.realworld.app.domain.User
import io.realworld.app.domain.repository.ArticleRepository
import io.realworld.app.domain.repository.UserRepository
import io.realworld.app.web.rules.AppRule
import org.apache.http.HttpStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

/**
 * Integration tests for `GET /api/articles/feed/popular`.
 *
 * Articles and favorites are seeded through the repositories because article creation and
 * favoriting are still stubbed at the HTTP layer in this codebase; the user is registered over
 * HTTP so the tests exercise the real JWT the endpoint requires.
 */
class ArticlePopularFeedTest {

    companion object {
        private const val PATH = "/api/articles/feed/popular"
        private const val EMAIL = "popular_feed@valid_email.com"
        private const val PASSWORD = "password"

        @ClassRule
        @JvmField
        val appRule = AppRule()

        @BeforeClass
        @JvmStatic
        fun seed() {
            val users = UserRepository()
            val articles = ArticleRepository()

            appRule.http.registerUser(EMAIL, PASSWORD, "popular_author")
            val authorId = users.findByEmail(EMAIL)!!.id!!
            val reader1 = users.create(User(email = "reader1@valid.com", username = "reader_one", password = "x"))!!
            val reader2 = users.create(User(email = "reader2@valid.com", username = "reader_two", password = "x"))!!

            val twoFavorites = articles.create(article("two favorites"), authorId)
            val oneFavorite = articles.create(article("one favorite"), authorId)
            articles.create(article("no favorites"), authorId)

            articles.favorite(twoFavorites, reader1)
            articles.favorite(twoFavorites, reader2)
            articles.favorite(oneFavorite, reader1)
        }

        private fun article(title: String) = Article(
            title = title,
            description = "desc of $title",
            body = "body of $title"
        )
    }

    @Before
    fun authenticate() {
        appRule.http.loginAndSetTokenHeader(EMAIL, PASSWORD)
    }

    @Test
    fun `return articles sorted by favorites count descending`() {
        val response = appRule.http.get<ArticlesDTO>(PATH)

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(listOf(2L, 1L, 0L), response.body.articles.map { it.favoritesCount })
        assertEquals("two favorites", response.body.articles.first().title)
        assertEquals(3, response.body.articlesCount)
    }

    @Test
    fun `return the article author without leaking credentials`() {
        val author = appRule.http.get<ArticlesDTO>(PATH).body.articles.first().author

        assertEquals("popular_author", author?.username)
        assertEquals(null, author?.password)
        assertEquals(null, author?.token)
    }

    @Test
    fun `paginate with limit and offset`() {
        val firstPage = appRule.http.get<ArticlesDTO>(PATH, mapOf("limit" to 1))
        val secondPage = appRule.http.get<ArticlesDTO>(PATH, mapOf("limit" to 1, "offset" to 1))

        assertEquals(HttpStatus.SC_OK, firstPage.status)
        assertEquals(1, firstPage.body.articles.size)
        assertEquals(2L, firstPage.body.articles.first().favoritesCount)
        assertEquals(1, secondPage.body.articles.size)
        assertEquals(1L, secondPage.body.articles.first().favoritesCount)
        // articlesCount is the total available, not the size of the page.
        assertEquals(3, firstPage.body.articlesCount)
    }

    @Test
    fun `return an empty page when the offset is past the end`() {
        val response = appRule.http.get<ArticlesDTO>(PATH, mapOf("offset" to 99))

        assertEquals(HttpStatus.SC_OK, response.status)
        assertTrue(response.body.articles.isEmpty())
    }

    @Test
    fun `return unauthorized without a token`() {
        appRule.http.headers.remove("Authorization")

        val response = appRule.http.getRaw(PATH)

        assertEquals(HttpStatus.SC_UNAUTHORIZED, response.status)
    }

    @Test
    fun `return unprocessable entity for a non numeric limit`() {
        val response = appRule.http.getRaw(PATH, mapOf("limit" to "abc"))

        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, response.status)
        assertTrue(response.body.contains("limit must be a positive integer"))
    }

    @Test
    fun `return unprocessable entity for a negative offset`() {
        val response = appRule.http.getRaw(PATH, mapOf("offset" to -1))

        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, response.status)
        assertTrue(response.body.contains("offset must be a positive integer"))
    }

    @Test
    fun `return unprocessable entity for a limit above the maximum`() {
        val response = appRule.http.getRaw(PATH, mapOf("limit" to 101))

        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, response.status)
        assertTrue(response.body.contains("limit must not be greater than 100"))
    }
}
