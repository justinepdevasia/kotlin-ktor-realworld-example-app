package io.realworld.app.domain.repository

import io.realworld.app.domain.Article
import io.realworld.app.domain.User
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.wrapAsExpression
import org.joda.time.DateTime

internal object Articles : LongIdTable() {
    val slug: Column<String> = varchar("slug", 255).uniqueIndex()
    val title: Column<String> = varchar("title", 255)
    val description: Column<String?> = varchar("description", 500).nullable()
    val body: Column<String> = text("body")
    val createdAt: Column<DateTime> = datetime("created_at")
    val updatedAt: Column<DateTime> = datetime("updated_at")
    val author: Column<Long> = long("author")
}

internal object Favorites : Table() {
    val article: Column<Long> = long("article").primaryKey()
    val user: Column<Long> = long("user").primaryKey()
}

class ArticleRepository {
    init {
        transaction {
            SchemaUtils.create(Articles)
            SchemaUtils.create(Favorites)
        }
    }

    // Correlated subquery instead of GROUP BY so articles with zero favorites still come back.
    private val favoritesCount = wrapAsExpression<Int>(
        Favorites.slice(Favorites.user.count())
            .select { Favorites.article eq Articles.id }
    )

    fun findPopular(limit: Int, offset: Int): List<Article> = transaction {
        Articles.join(Users, JoinType.INNER, additionalConstraint = { Articles.author eq Users.id })
            .slice(Articles.columns + Users.columns + favoritesCount)
            .selectAll()
            .orderBy(favoritesCount, SortOrder.DESC)
            .limit(limit, offset)
            .map { toDomain(it) }
    }

    fun count(): Int = transaction { Articles.selectAll().count() }

    fun create(article: Article, authorId: Long): String = transaction {
        val slug = article.title!!.toSlug()
        Articles.insertAndGetId { row ->
            row[Articles.slug] = slug
            row[title] = article.title
            row[description] = article.description
            row[body] = article.body
            row[createdAt] = DateTime.now()
            row[updatedAt] = DateTime.now()
            row[author] = authorId
        }
        slug
    }

    fun favorite(slug: String, userId: Long) = transaction {
        val articleId = Articles.select { Articles.slug eq slug }
            .map { it[Articles.id].value }
            .first()
        Favorites.insert { row ->
            row[article] = articleId
            row[user] = userId
        }
        Unit
    }

    private fun toDomain(row: ResultRow) = Article(
        slug = row[Articles.slug],
        title = row[Articles.title],
        description = row[Articles.description],
        body = row[Articles.body],
        createdAt = row[Articles.createdAt].toDate(),
        updatedAt = row[Articles.updatedAt].toDate(),
        // ponytail: no tagList join and favorited is always false — the popular feed is
        // unauthenticated, so there is no "current user" to resolve favorited against.
        favoritesCount = row[favoritesCount].toLong(),
        author = User(
            email = row[Users.email],
            username = row[Users.username],
            bio = row[Users.bio],
            image = row[Users.image]
        )
    )
}

private fun String.toSlug() = toLowerCase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
