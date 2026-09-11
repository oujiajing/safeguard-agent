package com.safeguard.agent.ingestion.dao.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IngestionPipelineNodeMapperTest {

    @Test
    void physicalDeleteByPipelineIdUsesHardDeleteSql() throws NoSuchMethodException {
        Method method = IngestionPipelineNodeMapper.class.getMethod("physicalDeleteByPipelineId", String.class);

        Delete delete = method.getAnnotation(Delete.class);
        assertNotNull(delete);
        assertEquals("DELETE FROM t_ingestion_pipeline_node WHERE pipeline_id = #{pipelineId}", delete.value()[0]);
        assertEquals(int.class, method.getReturnType());

        Param param = method.getParameters()[0].getAnnotation(Param.class);
        assertNotNull(param);
        assertEquals("pipelineId", param.value());
    }
}
