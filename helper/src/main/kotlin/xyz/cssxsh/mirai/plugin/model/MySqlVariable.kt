package xyz.cssxsh.mirai.plugin.model

import javax.persistence.*

/**
 * @see variables
 */
@Entity
data class MySqlVariable(
    @Id
    @Column(name = "Variable_name", nullable = false)
    val name: String,
    @Column(name = "Value", nullable = false)
    val value: String
)