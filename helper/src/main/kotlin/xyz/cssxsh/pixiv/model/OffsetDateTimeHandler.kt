package xyz.cssxsh.pixiv.model

import org.apache.ibatis.type.JdbcType
import org.apache.ibatis.type.TypeHandler
import java.sql.CallableStatement
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.OffsetDateTime
import xyz.cssxsh.pixiv.data.JapanDateTimeSerializer

class OffsetDateTimeHandler: TypeHandler<OffsetDateTime> {

    override fun setParameter(ps: PreparedStatement?, i: Int, parameter: OffsetDateTime?, jdbcType: JdbcType?) {
        ps?.setString(i, parameter?.format(JapanDateTimeSerializer.dateFormat))
    }

    override fun getResult(rs: ResultSet?, columnName: String?): OffsetDateTime? = rs?.getString(columnName)?.let {
        OffsetDateTime.parse(it, JapanDateTimeSerializer.dateFormat)
    }

    override fun getResult(rs: ResultSet?, columnIndex: Int): OffsetDateTime? = rs?.getString(columnIndex)?.let {
        OffsetDateTime.parse(it, JapanDateTimeSerializer.dateFormat)
    }

    override fun getResult(cs: CallableStatement?, columnIndex: Int): OffsetDateTime? = cs?.getString(columnIndex)?.let {
        OffsetDateTime.parse(it, JapanDateTimeSerializer.dateFormat)
    }
}