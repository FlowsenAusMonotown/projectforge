package org.projectforge.rest

import org.projectforge.framework.persistence.api.BaseDao
import org.projectforge.framework.persistence.api.BaseSearchFilter
import org.projectforge.framework.persistence.api.ExtendedBaseDO
import javax.ws.rs.core.Response

class RestHelper {
    companion object {
        fun <O : ExtendedBaseDO<Int>> getList(baseDao: BaseDao<O>?, filter: BaseSearchFilter): List<O> {
            val list = baseDao!!.getList(filter)
            return list
        }

        fun buildResponse(obj: Any): Response {
            val json = JsonUtils.toJson(obj)
            return Response.ok(json).build()
        }

        fun buildResponse(obj: ExtendedBaseDO<Int>): Response {
            val json = JsonUtils.toJson(obj)
            return Response.ok(json).build()
        }

        fun <O : ExtendedBaseDO<Int>> saveOrUpdate(baseDao: BaseDao<O>?, obj: O): Response {
            var id = baseDao!!.saveOrUpdate(obj)
            val json = JsonUtils.toJson(id)
            return Response.ok(json).build()
        }

        fun <O : ExtendedBaseDO<Int>> undelete(baseDao: BaseDao<O>?, obj: O): Response {
            var id = baseDao!!.undelete(obj)
            val json = JsonUtils.toJson(id)
            return Response.ok(json).build()
        }

        fun <O : ExtendedBaseDO<Int>> markAsDeleted(baseDao: BaseDao<O>?, obj: O): Response {
            baseDao!!.markAsDeleted(obj)
            val json = JsonUtils.toJson(obj)
            return Response.ok().build()
        }
    }
}