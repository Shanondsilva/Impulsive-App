package com.impulsive.app.backend.domain.engine.adaptive

import java.security.SecureRandom

interface RandomisationSource {
    fun nextDouble(): Double

    fun nextInt(bound: Int): Int
}

class SecureRandomisationSource(
    private val secureRandom: SecureRandom = SecureRandom(),
) : RandomisationSource {
    override fun nextDouble(): Double = secureRandom.nextDouble()

    override fun nextInt(bound: Int): Int = secureRandom.nextInt(bound)
}
