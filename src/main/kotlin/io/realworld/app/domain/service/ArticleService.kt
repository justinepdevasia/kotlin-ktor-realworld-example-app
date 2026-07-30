package io.realworld.app.domain.service

import io.realworld.app.domain.ArticlesDTO
import io.realworld.app.domain.repository.ArticleRepository

class ArticleService(private val articleRepository: ArticleRepository) {
    fun findPopular(limit: Int, offset: Int) =
        ArticlesDTO(articleRepository.findPopular(limit, offset), articleRepository.count())
}
